package com.binitech.imports.adapters.outbound.file;

import com.binitech.imports.application.ports.outbound.TabularFileParserPort;
import com.binitech.imports.domain.exception.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class TabularFileParserAdapter implements TabularFileParserPort {
  private static final int MAX_COLUMNS = 200;

  static {
    ZipSecureFile.setMinInflateRatio(0.01);
    ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
  }

  @Override
  public ParseResult parse(
      InputStream content, String fileName, int maxRecords, RowHandler handler) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".csv")) return parseCsv(content, maxRecords, handler);
    if (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
      return parseWorkbook(content, maxRecords, handler);
    throw new BusinessException("Formato de arquivo não suportado.");
  }

  private ParseResult parseCsv(InputStream content, int maxRecords, RowHandler handler) {
    try {
      BufferedInputStream input = new BufferedInputStream(content);
      input.mark(16_384);
      byte[] preview = input.readNBytes(16_384);
      input.reset();
      if (containsNull(preview)) throw new BusinessException("O conteúdo não é um CSV válido.");
      char delimiter = detectDelimiter(new String(preview, StandardCharsets.UTF_8));
      Reader reader = new InputStreamReader(skipBom(input), StandardCharsets.UTF_8);
      CSVFormat format =
          CSVFormat.DEFAULT
              .builder()
              .setDelimiter(delimiter)
              .setHeader()
              .setSkipHeaderRecord(true)
              .setIgnoreEmptyLines(true)
              .setTrim(true)
              .get();
      try (CSVParser csv = format.parse(reader)) {
        List<String> headers = sanitizeHeaders(csv.getHeaderNames());
        List<Map<String, String>> samples = new ArrayList<>();
        int count = 0;
        for (var record : csv) {
          if (record.stream().allMatch(String::isBlank)) continue;
          if (++count > maxRecords)
            throw new BusinessException(
                "O arquivo excede o limite de " + maxRecords + " produtos.");
          Map<String, String> row = new LinkedHashMap<>();
          for (int index = 0; index < headers.size(); index++) {
            row.put(headers.get(index), index < record.size() ? record.get(index) : "");
          }
          if (samples.size() < 5) samples.add(Map.copyOf(row));
          handler.accept((int) record.getRecordNumber() + 1, row);
        }
        return new ParseResult(headers, List.copyOf(samples), count);
      }
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException("CSV inválido ou codificação diferente de UTF-8.", exception);
    }
  }

  private ParseResult parseWorkbook(InputStream content, int maxRecords, RowHandler handler) {
    try (Workbook workbook = WorkbookFactory.create(new BufferedInputStream(content))) {
      if (workbook.getNumberOfSheets() == 0)
        throw new BusinessException("A planilha não possui abas.");
      Sheet sheet = workbook.getSheetAt(0);
      Iterator<Row> iterator = sheet.rowIterator();
      if (!iterator.hasNext()) throw new BusinessException("A planilha está vazia.");
      DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
      Row headerRow = iterator.next();
      int columns = Math.min(MAX_COLUMNS, Math.max(0, headerRow.getLastCellNum()));
      List<String> rawHeaders = new ArrayList<>();
      for (int column = 0; column < columns; column++)
        rawHeaders.add(formatter.formatCellValue(headerRow.getCell(column)));
      List<String> headers = sanitizeHeaders(rawHeaders);
      List<Map<String, String>> samples = new ArrayList<>();
      int count = 0;
      while (iterator.hasNext()) {
        Row source = iterator.next();
        Map<String, String> row = new LinkedHashMap<>();
        boolean blank = true;
        for (int column = 0; column < headers.size(); column++) {
          String value =
              formatter.formatCellValue(
                  source.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
          if (!value.isBlank()) blank = false;
          row.put(headers.get(column), value.trim());
        }
        if (blank) continue;
        if (++count > maxRecords)
          throw new BusinessException("O arquivo excede o limite de " + maxRecords + " produtos.");
        if (samples.size() < 5) samples.add(Map.copyOf(row));
        handler.accept(source.getRowNum() + 1, row);
      }
      return new ParseResult(headers, List.copyOf(samples), count);
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(
          "Planilha Excel inválida, corrompida ou protegida por senha.", exception);
    }
  }

  private static List<String> sanitizeHeaders(List<String> source) {
    if (source.isEmpty() || source.size() > MAX_COLUMNS)
      throw new BusinessException("O arquivo deve ter entre 1 e 200 colunas.");
    List<String> result = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (String raw : source) {
      String header = raw == null ? "" : raw.trim();
      if (header.isBlank()) throw new BusinessException("Há uma coluna sem cabeçalho.");
      if (!unique.add(header)) throw new BusinessException("Cabeçalho duplicado: " + header);
      result.add(header);
    }
    return List.copyOf(result);
  }

  private static InputStream skipBom(BufferedInputStream input) throws IOException {
    input.mark(3);
    byte[] bom = input.readNBytes(3);
    if (bom.length != 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF)
      input.reset();
    return input;
  }

  private static char detectDelimiter(String preview) {
    String first = preview.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
    long semicolons = first.chars().filter(c -> c == ';').count();
    long commas = first.chars().filter(c -> c == ',').count();
    long tabs = first.chars().filter(c -> c == '\t').count();
    if (tabs > semicolons && tabs > commas) return '\t';
    return semicolons >= commas ? ';' : ',';
  }

  private static boolean containsNull(byte[] bytes) {
    for (byte value : bytes) if (value == 0) return true;
    return false;
  }
}
