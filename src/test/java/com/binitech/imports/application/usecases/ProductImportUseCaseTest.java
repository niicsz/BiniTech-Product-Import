package com.binitech.imports.application.usecases;

import static org.junit.jupiter.api.Assertions.*;

import com.binitech.imports.adapters.outbound.file.TabularFileParserAdapter;
import com.binitech.imports.application.ports.outbound.*;
import com.binitech.imports.domain.*;
import com.binitech.imports.domain.exception.BusinessException;
import com.binitech.imports.domain.exception.ResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ProductImportUseCaseTest {
  private static final SessionIdentity TENANT_A =
      new SessionIdentity("user-a", "ana", "TENANT_ADMIN", "tenant-a");
  private final MemoryJobs jobs = new MemoryJobs();
  private final MemoryRows rows = new MemoryRows();
  private final MemoryFiles files = new MemoryFiles();
  private final FakeCatalog catalog = new FakeCatalog();
  private final FakeQueue queue = new FakeQueue();
  private ProductImportUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new ProductImportUseCase(
            jobs, rows, files, new TabularFileParserAdapter(), catalog, queue, 1_000_000, 50_000);
  }

  @Test
  void uploadsDetectsMappingsAndIsIdempotentWithinTenant() {
    byte[] content = validCsv().getBytes(StandardCharsets.UTF_8);

    ImportJob first = upload(content, "same-key");
    ImportJob repeated = upload(content, "same-key");

    assertEquals(first.getId(), repeated.getId());
    assertEquals(1, files.storeCalls);
    assertEquals(ImportField.NAME, first.getSuggestedMappings().getFirst().targetField());
    assertEquals("tenant-a", first.getTenantId());
    assertEquals("user-a", first.getCreatedBy());
  }

  @Test
  void validatesAllRowsDuplicatesAndExistingProductBeforeImportingAnything() {
    String csv =
        "Descrição do Produto;EAN;Valor Venda;Preço de Custo;Qtd. Estoque;Grupo;Status\n"
            + "Café;7891234567895;R$ 10,50;5,00;10;Mercearia;sim\n"
            + ";111;9,00;2,00;3;Mercearia;sim\n"
            + "Arroz;222;abc;2,00;-1;Mercearia;sim\n"
            + "Café duplicado;7891234567895;11,00;5,00;2;Mercearia;sim\n";
    ImportJob job = upload(csv.getBytes(StandardCharsets.UTF_8), null);
    catalog.existing.put(
        "7891234567895",
        new ProductCatalogPort.ExistingProduct(
            "product-1",
            "7891234567895",
            "Café atual",
            new BigDecimal("9.99"),
            new BigDecimal("4.00"),
            7,
            "Mercearia",
            true,
            "user-a"));

    ImportJob validated =
        useCase.validate(job.getId(), mappings(), DuplicateStrategy.FIRST, TENANT_A);

    assertTrue(validated.isValidated());
    assertEquals(4, validated.getSummary().totalRecords());
    assertEquals(2, validated.getSummary().errorRecords());
    assertEquals(2, validated.getSummary().warningRecords());
    ImportRow existing =
        rows.data.stream().filter(r -> r.getLineNumber() == 2).findFirst().orElseThrow();
    assertEquals(RowAction.UPDATE, existing.getAction());
    assertEquals(new BigDecimal("10.50"), existing.getPrice());
    assertEquals(new BigDecimal("9.99"), existing.getCurrentPrice());
    assertEquals(
        RowAction.IGNORE,
        useCase.overrideRowAction(job.getId(), 2, RowAction.IGNORE, TENANT_A).getAction());
    assertEquals(
        RowAction.UPDATE,
        useCase.overrideRowAction(job.getId(), 2, RowAction.UPDATE, TENANT_A).getAction());
    ImportRow duplicate =
        rows.data.stream().filter(r -> r.getLineNumber() == 5).findFirst().orElseThrow();
    assertEquals(RowAction.IGNORE, duplicate.getAction());
    assertTrue(
        rows.data.stream()
            .flatMap(r -> r.getIssues().stream())
            .anyMatch(issue -> issue.code().equals("REQUIRED_NAME")));
    assertTrue(
        rows.data.stream()
            .flatMap(r -> r.getIssues().stream())
            .anyMatch(issue -> issue.code().equals("INVALID_PRICE")));
    assertTrue(
        rows.data.stream()
            .flatMap(r -> r.getIssues().stream())
            .anyMatch(issue -> issue.code().equals("INVALID_STOCK")));
  }

  @Test
  void requiresExplicitStockConfirmationAndQueuesOnlyAfterValidation() {
    ImportJob job = upload(validCsv().getBytes(StandardCharsets.UTF_8), null);
    useCase.validate(job.getId(), mappings(), DuplicateStrategy.ERROR, TENANT_A);

    assertThrows(
        BusinessException.class,
        () ->
            useCase.start(
                job.getId(),
                ImportMode.CREATE_AND_UPDATE,
                StockMode.REPLACE,
                EnumSet.of(ImportField.NAME, ImportField.STOCK_QUANTITY),
                false,
                TENANT_A));

    ImportJob started =
        useCase.start(
            job.getId(),
            ImportMode.CREATE_AND_UPDATE,
            StockMode.REPLACE,
            EnumSet.of(ImportField.NAME, ImportField.STOCK_QUANTITY),
            true,
            TENANT_A);
    assertEquals(List.of(job.getId()), queue.jobIds);
    assertEquals(StockMode.REPLACE, started.getConfiguration().stockMode());
    assertFalse(started.getConfiguration().updateFields().contains(ImportField.STOCK_QUANTITY));
  }

  @Test
  void requiresEverySourceColumnToBeMappedOrExplicitlyIgnored() {
    ImportJob job = upload(validCsv().getBytes(StandardCharsets.UTF_8), null);
    List<ColumnMapping> incomplete =
        mappings().stream().filter(mapping -> !mapping.sourceColumn().equals("Status")).toList();

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> useCase.validate(job.getId(), incomplete, DuplicateStrategy.ERROR, TENANT_A));

    assertTrue(exception.getMessage().contains("Status"));
  }

  @Test
  void isolatesEveryJobByAuthenticatedTenant() {
    ImportJob job = upload(validCsv().getBytes(StandardCharsets.UTF_8), null);
    SessionIdentity tenantB = new SessionIdentity("user-b", "bia", "TENANT_ADMIN", "tenant-b");

    assertThrows(ResourceNotFoundException.class, () -> useCase.get(job.getId(), tenantB));
  }

  @Test
  void delegatesPermissionChecksToThePdv() {
    catalog.denied = true;
    byte[] content = validCsv().getBytes(StandardCharsets.UTF_8);
    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.upload(
                new ByteArrayInputStream(content),
                "produtos.csv",
                "text/csv",
                content.length,
                null,
                TENANT_A));
    assertTrue(jobs.data.isEmpty());
  }

  @Test
  void keepsFailedAuditWhenFileIsEmptyOrTooLarge() {
    byte[] empty = "nome;codigo_barras;preco;estoque\n".getBytes(StandardCharsets.UTF_8);
    assertThrows(BusinessException.class, () -> upload(empty, "empty"));
    assertEquals(ImportStatus.FAILED, jobs.data.values().iterator().next().getStatus());

    ProductImportUseCase limited =
        new ProductImportUseCase(
            new MemoryJobs(),
            new MemoryRows(),
            new MemoryFiles(),
            new TabularFileParserAdapter(),
            catalog,
            queue,
            4,
            50_000);
    assertThrows(
        BusinessException.class,
        () ->
            limited.upload(
                new ByteArrayInputStream(new byte[5]),
                "produtos.csv",
                "text/csv",
                5,
                null,
                TENANT_A));
  }

  private ImportJob upload(byte[] content, String key) {
    return useCase.upload(
        new ByteArrayInputStream(content),
        "produtos.csv",
        "text/csv",
        content.length,
        key,
        TENANT_A);
  }

  private static String validCsv() {
    return "Descrição do Produto;EAN;Valor Venda;Preço de Custo;Qtd. Estoque;Grupo;Status\n"
        + "Café;7891234567895;R$ 10,50;5,00;10;Mercearia;sim\n";
  }

  private static List<ColumnMapping> mappings() {
    return List.of(
        new ColumnMapping("Descrição do Produto", ImportField.NAME, 1),
        new ColumnMapping("EAN", ImportField.BARCODE, 1),
        new ColumnMapping("Valor Venda", ImportField.PRICE, 1),
        new ColumnMapping("Preço de Custo", ImportField.COST, 1),
        new ColumnMapping("Qtd. Estoque", ImportField.STOCK_QUANTITY, 1),
        new ColumnMapping("Grupo", ImportField.CATEGORY, 1),
        new ColumnMapping("Status", ImportField.ACTIVE, 1));
  }

  static final class MemoryJobs implements ImportJobRepositoryPort {
    final Map<String, ImportJob> data = new LinkedHashMap<>();
    int sequence;

    public ImportJob save(ImportJob job) {
      if (job.getId() == null) job.setId("job-" + ++sequence);
      data.put(job.getId(), job);
      return job;
    }

    public Optional<ImportJob> findByIdAndTenantId(String id, String tenantId) {
      return Optional.ofNullable(data.get(id)).filter(job -> tenantId.equals(job.getTenantId()));
    }

    public Optional<ImportJob> findById(String id) {
      return Optional.ofNullable(data.get(id));
    }

    public Optional<ImportJob> findByTenantIdAndIdempotencyKey(String tenantId, String key) {
      return data.values().stream()
          .filter(job -> tenantId.equals(job.getTenantId()) && key.equals(job.getIdempotencyKey()))
          .findFirst();
    }

    public synchronized boolean tryStart(String id) {
      ImportJob job = data.get(id);
      if (job == null || job.getStatus() != ImportStatus.PENDING || job.getConfiguration() == null)
        return false;
      job.setStatus(ImportStatus.PROCESSING);
      return true;
    }
  }

  static final class MemoryRows implements ImportRowRepositoryPort {
    final List<ImportRow> data = new ArrayList<>();

    public void deleteByJobIdAndTenantId(String jobId, String tenantId) {
      data.removeIf(r -> jobId.equals(r.getJobId()) && tenantId.equals(r.getTenantId()));
    }

    public void saveAll(List<ImportRow> source) {
      for (ImportRow row : source) if (!data.contains(row)) data.add(row);
    }

    public List<ImportRow> findPage(
        String jobId, String tenantId, int page, int size, boolean issuesOnly) {
      return data.stream()
          .filter(r -> jobId.equals(r.getJobId()) && tenantId.equals(r.getTenantId()))
          .filter(r -> !issuesOnly || !r.getIssues().isEmpty())
          .skip((long) page * size)
          .limit(size)
          .toList();
    }

    public List<ImportRow> findPendingPage(String jobId, String tenantId, int size) {
      return data.stream()
          .filter(r -> jobId.equals(r.getJobId()) && tenantId.equals(r.getTenantId()))
          .filter(r -> !r.isProcessed())
          .limit(size)
          .toList();
    }

    public List<ImportRow> findIssues(String jobId, String tenantId) {
      return findPage(jobId, tenantId, 0, Integer.MAX_VALUE, true);
    }

    public Optional<ImportRow> findByJobIdAndTenantIdAndLineNumber(
        String jobId, String tenantId, int lineNumber) {
      return data.stream()
          .filter(
              row ->
                  jobId.equals(row.getJobId())
                      && tenantId.equals(row.getTenantId())
                      && lineNumber == row.getLineNumber())
          .findFirst();
    }
  }

  static final class MemoryFiles implements FileStoragePort {
    final Map<String, byte[]> data = new HashMap<>();
    int storeCalls;

    public String store(InputStream content, String fileName, String contentType, String tenantId) {
      try {
        storeCalls++;
        String id = tenantId + "-file-" + storeCalls;
        data.put(id, content.readAllBytes());
        return id;
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    }

    public InputStream open(String fileId, String tenantId) {
      if (!fileId.startsWith(tenantId + "-")) throw new AccessDeniedException("tenant");
      return new ByteArrayInputStream(data.get(fileId));
    }

    public void delete(String fileId, String tenantId) {
      data.remove(fileId);
    }
  }

  static final class FakeCatalog implements ProductCatalogPort {
    final Map<String, ExistingProduct> existing = new HashMap<>();
    boolean denied;

    public void assertAuthorized(SessionIdentity identity) {
      if (denied) throw new AccessDeniedException("sem permissão");
    }

    public Map<String, ExistingProduct> findExisting(
        Set<String> barcodes, SessionIdentity identity) {
      Map<String, ExistingProduct> found = new HashMap<>();
      barcodes.forEach(
          barcode ->
              Optional.ofNullable(existing.get(barcode)).ifPresent(p -> found.put(barcode, p)));
      return found;
    }

    public List<ApplyResult> apply(
        String jobId,
        List<ImportRow> source,
        ImportConfiguration configuration,
        SessionIdentity identity) {
      return List.of();
    }
  }

  static final class FakeQueue implements ImportQueuePort {
    final List<String> jobIds = new ArrayList<>();

    public void enqueue(String jobId) {
      jobIds.add(jobId);
    }
  }
}
