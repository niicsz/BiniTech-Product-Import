package com.binitech.imports.adapters.outbound.file;

import static org.junit.jupiter.api.Assertions.*;

import com.binitech.imports.application.ports.outbound.TabularFileParserPort.ParseResult;
import com.binitech.imports.domain.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TabularFileParserAdapterTest {
  private final TabularFileParserAdapter parser = new TabularFileParserAdapter();

  @Test
  void parsesCsvWithBrazilianDelimiterAccentsAndQuotedText() {
    String csv =
        "\uFEFFDescrição do Produto;EAN;Valor Venda;Categoria\r\n"
            + "\"Café; torrado\";7891234567895;R$ 10,50;Mercearia\r\n";
    List<Map<String, String>> rows = new ArrayList<>();

    ParseResult result =
        parser.parse(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            "produtos.csv",
            100,
            (line, values) -> rows.add(values));

    assertEquals(1, result.totalRecords());
    assertEquals(
        List.of("Descrição do Produto", "EAN", "Valor Venda", "Categoria"), result.headers());
    assertEquals("Café; torrado", rows.getFirst().get("Descrição do Produto"));
    assertEquals("R$ 10,50", rows.getFirst().get("Valor Venda"));
  }

  @Test
  void rejectsMalformedBinaryCsv() {
    byte[] invalid = new byte[] {'n', 'o', 'm', 'e', ';', 0, 'x'};
    assertThrows(
        BusinessException.class,
        () -> parser.parse(new ByteArrayInputStream(invalid), "produtos.csv", 100, (l, r) -> {}));
  }

  @Test
  void rejectsDuplicateOrUnknownFileStructuresSafely() {
    String duplicateHeader = "nome;nome\nA;B\n";
    assertThrows(
        BusinessException.class,
        () ->
            parser.parse(
                new ByteArrayInputStream(duplicateHeader.getBytes(StandardCharsets.UTF_8)),
                "produtos.csv",
                100,
                (l, r) -> {}));
    assertThrows(
        BusinessException.class,
        () ->
            parser.parse(
                new ByteArrayInputStream(new byte[] {1}), "produtos.pdf", 100, (l, r) -> {}));
  }

  @Test
  void enforcesRecordLimitBeforeKeepingLargeFileInMemory() {
    String csv = "nome;codigo\nA;1\nB;2\nC;3\n";
    BusinessException error =
        assertThrows(
            BusinessException.class,
            () ->
                parser.parse(
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                    "produtos.csv",
                    2,
                    (l, r) -> {}));
    assertTrue(error.getMessage().contains("limite"));
  }

  @Test
  void parsesXlsx() throws Exception {
    assertWorkbook(new XSSFWorkbook(), "produtos.xlsx");
  }

  @Test
  void parsesLegacyXls() throws Exception {
    assertWorkbook(new HSSFWorkbook(), "produtos.xls");
  }

  private void assertWorkbook(Workbook workbook, String fileName) throws Exception {
    byte[] bytes;
    try (workbook;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Produtos");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("Nome do Produto");
      header.createCell(1).setCellValue("Preço");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("Açúcar");
      row.createCell(1).setCellValue(7.5);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    List<Map<String, String>> rows = new ArrayList<>();

    ParseResult result =
        parser.parse(
            new ByteArrayInputStream(bytes), fileName, 10, (line, values) -> rows.add(values));

    assertEquals(1, result.totalRecords());
    assertEquals("Açúcar", rows.getFirst().get("Nome do Produto"));
    assertFalse(rows.getFirst().get("Preço").isBlank());
  }
}
