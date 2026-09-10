package com.binitech.imports.application.usecases;

import com.binitech.imports.application.ports.inbound.ProductImportUseCasePort;
import com.binitech.imports.application.ports.outbound.*;
import com.binitech.imports.application.service.BrazilianNumberParser;
import com.binitech.imports.application.service.ColumnMappingDetector;
import com.binitech.imports.domain.*;
import com.binitech.imports.domain.exception.BusinessException;
import com.binitech.imports.domain.exception.ResourceNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class ProductImportUseCase implements ProductImportUseCasePort {
  private static final Set<String> EXTENSIONS = Set.of("csv", "xlsx", "xls");
  private static final int PERSISTENCE_BATCH = 500;
  private final ImportJobRepositoryPort jobs;
  private final ImportRowRepositoryPort rows;
  private final FileStoragePort files;
  private final TabularFileParserPort parser;
  private final ProductCatalogPort catalog;
  private final ImportQueuePort queue;
  private final ColumnMappingDetector detector;
  private final BrazilianNumberParser numbers;
  private final int maxFileBytes;
  private final int maxRecords;

  public ProductImportUseCase(
      ImportJobRepositoryPort jobs,
      ImportRowRepositoryPort rows,
      FileStoragePort files,
      TabularFileParserPort parser,
      ProductCatalogPort catalog,
      ImportQueuePort queue,
      int maxFileBytes,
      int maxRecords) {
    this.jobs = jobs;
    this.rows = rows;
    this.files = files;
    this.parser = parser;
    this.catalog = catalog;
    this.queue = queue;
    this.detector = new ColumnMappingDetector();
    this.numbers = new BrazilianNumberParser();
    this.maxFileBytes = maxFileBytes;
    this.maxRecords = maxRecords;
  }

  @Override
  public ImportJob upload(
      InputStream content,
      String fileName,
      String contentType,
      long fileSize,
      String idempotencyKey,
      SessionIdentity identity) {
    catalog.assertAuthorized(identity);
    validateFile(fileName, fileSize);
    String normalizedKey = blankToNull(idempotencyKey);
    if (normalizedKey != null) {
      var existing = jobs.findByTenantIdAndIdempotencyKey(identity.tenantId(), normalizedKey);
      if (existing.isPresent()) return existing.get();
    }

    ImportJob job = new ImportJob();
    job.setTenantId(identity.tenantId());
    job.setCreatedBy(identity.userId());
    job.setCreatedByRole(identity.role());
    job.setIdempotencyKey(normalizedKey);
    job.setFileName(safeFileName(fileName));
    job.setContentType(contentType);
    job.setFileSize(fileSize);
    job.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    job = jobs.save(job);

    try {
      String fileId = files.store(content, job.getFileName(), contentType, identity.tenantId());
      job.setFileId(fileId);
      job = jobs.save(job);
      try (InputStream stored = files.open(fileId, identity.tenantId())) {
        var metadata = parser.parse(stored, job.getFileName(), maxRecords, (line, values) -> {});
        if (metadata.totalRecords() == 0)
          throw new BusinessException("O arquivo não possui produtos.");
        job.setHeaders(metadata.headers());
        job.setSampleRows(metadata.sampleRows());
        job.setSuggestedMappings(detector.detect(metadata.headers()));
      }
      return jobs.save(job);
    } catch (RuntimeException exception) {
      if (job.getFileId() != null) files.delete(job.getFileId(), identity.tenantId());
      job.setFileId(null);
      job.setStatus(ImportStatus.FAILED);
      job.setFailureMessage(safeMessage(exception));
      job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
      jobs.save(job);
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException("Não foi possível ler o arquivo enviado.", exception);
    }
  }

  @Override
  public ImportJob validate(
      String id,
      List<ColumnMapping> mappings,
      DuplicateStrategy duplicateStrategy,
      SessionIdentity identity) {
    catalog.assertAuthorized(identity);
    ImportJob job = owned(id, identity);
    ensurePending(job);
    List<ColumnMapping> safeMappings = validateMappings(job, mappings);
    DuplicateStrategy strategy =
        duplicateStrategy == null ? DuplicateStrategy.ERROR : duplicateStrategy;
    rows.deleteByJobIdAndTenantId(id, identity.tenantId());

    List<ImportRow> parsedRows = new ArrayList<>();
    Map<String, ImportField> mapping = new HashMap<>();
    safeMappings.forEach(m -> mapping.put(m.sourceColumn(), m.targetField()));
    try (InputStream content = files.open(job.getFileId(), identity.tenantId())) {
      parser.parse(
          content,
          job.getFileName(),
          maxRecords,
          (line, values) -> parsedRows.add(toRow(job, line, values, mapping)));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException("Não foi possível validar o arquivo.", exception);
    }
    if (parsedRows.isEmpty()) throw new BusinessException("O arquivo não possui produtos.");

    resolveDuplicates(parsedRows, strategy);
    Set<String> barcodes = new LinkedHashSet<>();
    parsedRows.stream()
        .filter(r -> !r.hasErrors() && r.getBarcode() != null)
        .forEach(r -> barcodes.add(r.getBarcode()));
    Map<String, ProductCatalogPort.ExistingProduct> existing =
        catalog.findExisting(barcodes, identity);
    for (ImportRow row : parsedRows) {
      var product = row.getBarcode() == null ? null : existing.get(row.getBarcode());
      if (product != null && !row.hasErrors() && row.getAction() != RowAction.IGNORE) {
        row.setAction(RowAction.UPDATE);
        row.setExistingProductId(product.id());
        row.setCurrentPrice(product.price());
        row.setCurrentStockQuantity(product.stockQuantity());
        row.addIssue(
            new ImportIssue(
                IssueSeverity.WARNING,
                "barcode",
                "EXISTING_PRODUCT",
                "Produto existente encontrado; revise os campos que poderão ser atualizados."));
      }
    }
    persistRows(parsedRows);
    job.setMappings(safeMappings);
    job.setDuplicateStrategy(strategy);
    job.setSummary(summary(parsedRows, 0, 0, 0, 0));
    job.setValidated(true);
    job.setFailureMessage(null);
    return jobs.save(job);
  }

  @Override
  public ImportJob start(
      String id,
      ImportMode mode,
      StockMode stockMode,
      Set<ImportField> updateFields,
      boolean confirmStock,
      SessionIdentity identity) {
    catalog.assertAuthorized(identity);
    ImportJob job = owned(id, identity);
    if (job.getStatus() != ImportStatus.PENDING) return job;
    if (!job.isValidated()) throw new BusinessException("Valide o arquivo antes de iniciar.");
    StockMode safeStockMode = stockMode == null ? StockMode.IGNORE : stockMode;
    if (safeStockMode != StockMode.IGNORE && !confirmStock) {
      throw new BusinessException("Confirme explicitamente a alteração de estoque.");
    }
    ImportMode safeMode = mode == null ? ImportMode.CREATE_ONLY : mode;
    Set<ImportField> safeFields =
        updateFields == null ? Set.of() : new LinkedHashSet<>(updateFields);
    safeFields.remove(ImportField.STOCK_QUANTITY);
    safeFields.remove(ImportField.IGNORE);
    job.setConfiguration(new ImportConfiguration(safeMode, safeStockMode, safeFields));
    job = jobs.save(job);
    queue.enqueue(job.getId());
    return job;
  }

  @Override
  public ImportJob get(String id, SessionIdentity identity) {
    return owned(id, identity);
  }

  @Override
  public List<ImportRow> rows(
      String id, int page, int size, boolean issuesOnly, SessionIdentity identity) {
    owned(id, identity);
    int safePage = Math.max(0, page);
    int safeSize = Math.min(200, Math.max(1, size));
    return rows.findPage(id, identity.tenantId(), safePage, safeSize, issuesOnly);
  }

  @Override
  public ImportRow overrideRowAction(
      String id, int lineNumber, RowAction action, SessionIdentity identity) {
    catalog.assertAuthorized(identity);
    ImportJob job = owned(id, identity);
    ensurePending(job);
    ImportRow row =
        rows.findByJobIdAndTenantIdAndLineNumber(id, identity.tenantId(), lineNumber)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Linha da importação", Integer.toString(lineNumber)));
    if (row.hasErrors())
      throw new BusinessException("Uma linha com erro não pode ser marcada para atualização.");
    if (row.getExistingProductId() == null)
      throw new BusinessException("A decisão individual só se aplica a produto existente.");
    if (action != RowAction.UPDATE && action != RowAction.IGNORE)
      throw new BusinessException("Escolha manter atual ou atualizar.");
    row.setAction(action);
    rows.saveAll(List.of(row));
    return row;
  }

  @Override
  public byte[] errorReport(String id, SessionIdentity identity) {
    owned(id, identity);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.writeBytes("\uFEFFlinha;produto;erro\r\n".getBytes(StandardCharsets.UTF_8));
    for (ImportRow row : rows.findIssues(id, identity.tenantId())) {
      for (ImportIssue issue : row.getIssues()) {
        if (issue.severity() == IssueSeverity.ERROR) {
          String record =
              row.getLineNumber() + ";" + csv(row.getName()) + ";" + csv(issue.message()) + "\r\n";
          output.writeBytes(record.getBytes(StandardCharsets.UTF_8));
        }
      }
    }
    return output.toByteArray();
  }

  @Override
  public void cancel(String id, SessionIdentity identity) {
    ImportJob job = owned(id, identity);
    if (job.getStatus() == ImportStatus.COMPLETED
        || job.getStatus() == ImportStatus.COMPLETED_WITH_ERRORS) {
      throw new BusinessException("Uma importação concluída não pode ser cancelada.");
    }
    job.setStatus(ImportStatus.CANCELLED);
    job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    jobs.save(job);
  }

  @Override
  public byte[] template() {
    return ("\uFEFFnome;codigo_barras;preco;custo;estoque;categoria;ativo\r\n"
            + "Café 500g;7891234567895;R$ 18,90;12,50;24;Mercearia;sim\r\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private ImportRow toRow(
      ImportJob job, int line, Map<String, String> values, Map<String, ImportField> mapping) {
    ImportRow row = new ImportRow();
    row.setJobId(job.getId());
    row.setTenantId(job.getTenantId());
    row.setLineNumber(line);
    mapping.forEach(
        (source, target) -> {
          if (target != ImportField.IGNORE) setValue(row, target, values.get(source));
        });
    validateRow(row);
    return row;
  }

  private void setValue(ImportRow row, ImportField field, String raw) {
    try {
      switch (field) {
        case NAME -> row.setName(trim(raw));
        case BARCODE -> row.setBarcode(normalizeBarcode(raw));
        case PRICE -> row.setPrice(numbers.money(raw));
        case COST -> row.setCost(numbers.money(raw));
        case STOCK_QUANTITY -> row.setStockQuantity(numbers.integer(raw));
        case CATEGORY -> row.setCategory(trim(raw));
        case ACTIVE -> row.setActive(parseActive(raw));
        case IGNORE -> {}
      }
    } catch (BusinessException exception) {
      row.addIssue(
          new ImportIssue(
              IssueSeverity.ERROR,
              field.name(),
              "INVALID_" + field.name(),
              exception.getMessage()));
    }
  }

  private void validateRow(ImportRow row) {
    if (row.getName() == null || row.getName().isBlank())
      error(row, "name", "REQUIRED_NAME", "Nome obrigatório.");
    else if (row.getName().length() > 200)
      error(row, "name", "INVALID_NAME", "Nome deve ter no máximo 200 caracteres.");
    if (row.getBarcode() == null || row.getBarcode().isBlank())
      error(row, "barcode", "REQUIRED_BARCODE", "Código de barras obrigatório.");
    else if (!validBarcode(row.getBarcode()))
      error(row, "barcode", "INVALID_BARCODE", "Código de barras inválido.");
    if (row.getPrice() == null) error(row, "price", "REQUIRED_PRICE", "Preço obrigatório.");
    else if (row.getPrice().compareTo(new BigDecimal("0.01")) < 0)
      error(row, "price", "INVALID_PRICE", "Preço deve ser maior que zero.");
    if (row.getCost() != null && row.getCost().signum() < 0)
      error(row, "cost", "INVALID_COST", "Custo não pode ser negativo.");
    if (row.getStockQuantity() == null)
      error(row, "stockQuantity", "REQUIRED_STOCK", "Estoque obrigatório.");
    else if (row.getStockQuantity() < 0)
      error(row, "stockQuantity", "INVALID_STOCK", "Estoque não pode ser negativo.");
    if (row.getCategory() != null && row.getCategory().length() > 100)
      error(row, "category", "INVALID_CATEGORY", "Categoria deve ter no máximo 100 caracteres.");
    if (row.getActive() == null) row.setActive(true);
  }

  private void resolveDuplicates(List<ImportRow> parsedRows, DuplicateStrategy strategy) {
    Map<String, List<ImportRow>> byBarcode = new LinkedHashMap<>();
    parsedRows.stream()
        .filter(r -> r.getBarcode() != null && !r.getBarcode().isBlank())
        .forEach(r -> byBarcode.computeIfAbsent(r.getBarcode(), key -> new ArrayList<>()).add(r));
    byBarcode.values().stream()
        .filter(group -> group.size() > 1)
        .forEach(
            group -> {
              List<Integer> lines = group.stream().map(ImportRow::getLineNumber).toList();
              for (int index = 0; index < group.size(); index++) {
                ImportRow row = group.get(index);
                boolean keep =
                    strategy == DuplicateStrategy.FIRST && index == 0
                        || strategy == DuplicateStrategy.LAST && index == group.size() - 1;
                if (strategy == DuplicateStrategy.ERROR) {
                  row.addIssue(
                      new ImportIssue(
                          IssueSeverity.ERROR,
                          "barcode",
                          "DUPLICATE_BARCODE",
                          "Código de barras duplicado no arquivo.",
                          lines));
                } else if (!keep) {
                  row.setAction(RowAction.IGNORE);
                  row.addIssue(
                      new ImportIssue(
                          IssueSeverity.WARNING,
                          "barcode",
                          "DUPLICATE_IGNORED",
                          "Linha duplicada ignorada pela estratégia selecionada.",
                          lines));
                }
              }
            });
  }

  private List<ColumnMapping> validateMappings(ImportJob job, List<ColumnMapping> mappings) {
    if (mappings == null || mappings.isEmpty())
      throw new BusinessException("Confirme o mapeamento das colunas.");
    Set<String> headers = new HashSet<>(job.getHeaders());
    Set<String> sources = new HashSet<>();
    Set<ImportField> targets = EnumSet.noneOf(ImportField.class);
    for (ColumnMapping mapping : mappings) {
      if (!headers.contains(mapping.sourceColumn()))
        throw new BusinessException("Coluna inexistente: " + mapping.sourceColumn());
      if (!sources.add(mapping.sourceColumn()))
        throw new BusinessException("Coluna mapeada mais de uma vez: " + mapping.sourceColumn());
      if (mapping.targetField() != ImportField.IGNORE && !targets.add(mapping.targetField())) {
        throw new BusinessException(
            "Campo BiniTech mapeado mais de uma vez: " + mapping.targetField());
      }
    }
    Set<String> unmappedHeaders = new LinkedHashSet<>(headers);
    unmappedHeaders.removeAll(sources);
    if (!unmappedHeaders.isEmpty()) {
      throw new BusinessException(
          "Confirme ou marque como ignoradas as colunas: " + unmappedHeaders);
    }
    Set<ImportField> required =
        EnumSet.of(
            ImportField.NAME, ImportField.BARCODE, ImportField.PRICE, ImportField.STOCK_QUANTITY);
    required.removeAll(targets);
    if (!required.isEmpty())
      throw new BusinessException("Campos obrigatórios sem mapeamento: " + required);
    return List.copyOf(mappings);
  }

  private ImportSummary summary(
      List<ImportRow> source, int processed, int created, int updated, int ignored) {
    int errors = (int) source.stream().filter(ImportRow::hasErrors).count();
    int warnings = (int) source.stream().filter(r -> !r.hasErrors() && r.hasWarnings()).count();
    int valid = source.size() - errors;
    return new ImportSummary(
        source.size(), valid, warnings, errors, processed, created, updated, ignored);
  }

  private void persistRows(List<ImportRow> all) {
    for (int start = 0; start < all.size(); start += PERSISTENCE_BATCH) {
      rows.saveAll(all.subList(start, Math.min(all.size(), start + PERSISTENCE_BATCH)));
    }
  }

  private ImportJob owned(String id, SessionIdentity identity) {
    return jobs.findByIdAndTenantId(id, identity.tenantId())
        .orElseThrow(() -> new ResourceNotFoundException("Importação", id));
  }

  private void ensurePending(ImportJob job) {
    if (job.getStatus() != ImportStatus.PENDING)
      throw new BusinessException("A importação já foi iniciada ou encerrada.");
  }

  private void validateFile(String fileName, long size) {
    String extension = extension(fileName);
    if (!EXTENSIONS.contains(extension))
      throw new BusinessException("Envie um arquivo CSV, XLSX ou XLS.");
    if (size <= 0) throw new BusinessException("O arquivo está vazio.");
    if (size > maxFileBytes)
      throw new BusinessException("O arquivo excede o limite de " + maxFileBytes + " bytes.");
  }

  private static String extension(String name) {
    if (name == null) return "";
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String safeFileName(String name) {
    String base = name == null ? "importacao" : name.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t]", "_");
    return base.length() > 200 ? base.substring(base.length() - 200) : base;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String normalizeBarcode(String raw) {
    String value = trim(raw);
    if (value == null) return null;
    if (value.endsWith(".0") && value.substring(0, value.length() - 2).matches("\\d+"))
      return value.substring(0, value.length() - 2);
    return value.replace(" ", "");
  }

  private static Boolean parseActive(String raw) {
    String value = ColumnMappingDetector.normalize(raw);
    if (value.isBlank()) return true;
    if (Set.of("sim", "s", "true", "1", "ativo", "active").contains(value)) return true;
    if (Set.of("nao", "n", "false", "0", "inativo", "inactive").contains(value)) return false;
    throw new BusinessException("Status inválido: " + raw);
  }

  private static boolean validBarcode(String value) {
    if (value.length() > 64 || value.chars().anyMatch(Character::isISOControl)) return false;
    if (!value.matches("\\d+")) return true;
    if (!Set.of(8, 12, 13, 14).contains(value.length())) return value.length() <= 64;
    int sum = 0;
    for (int i = value.length() - 2, weight = 3; i >= 0; i--, weight = weight == 3 ? 1 : 3)
      sum += (value.charAt(i) - '0') * weight;
    int check = (10 - sum % 10) % 10;
    return check == value.charAt(value.length() - 1) - '0';
  }

  private static void error(ImportRow row, String field, String code, String message) {
    row.addIssue(new ImportIssue(IssueSeverity.ERROR, field, code, message));
  }

  private static String csv(String value) {
    if (value == null) return "";
    return value.contains(";") || value.contains("\"") || value.contains("\n")
        ? "\"" + value.replace("\"", "\"\"") + "\""
        : value;
  }

  private static String safeMessage(Exception exception) {
    String value = exception.getMessage();
    if (value == null || value.isBlank()) return "Falha ao analisar o arquivo.";
    return value.length() > 300 ? value.substring(0, 300) : value;
  }
}
