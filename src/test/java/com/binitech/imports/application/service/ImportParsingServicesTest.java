package com.binitech.imports.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.binitech.imports.domain.ImportField;
import com.binitech.imports.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportParsingServicesTest {
  private final BrazilianNumberParser numbers = new BrazilianNumberParser();

  @Test
  void parsesBrazilianAndInternationalMoneyWithoutDouble() {
    assertEquals(new BigDecimal("10.50"), numbers.money("10,50"));
    assertEquals(new BigDecimal("10.50"), numbers.money("10.50"));
    assertEquals(new BigDecimal("1234.56"), numbers.money("R$ 1.234,56"));
    assertEquals(new BigDecimal("1234.56"), numbers.money("1,234.56"));
    assertNull(numbers.money(" "));
  }

  @Test
  void rejectsInvalidMoneyAndFractionalStock() {
    assertThrows(BusinessException.class, () -> numbers.money("dez reais"));
    assertThrows(BusinessException.class, () -> numbers.integer("1,5"));
  }

  @Test
  void detectsKnownColumnsAndLeavesUnknownOnIgnore() {
    var mappings =
        new ColumnMappingDetector()
            .detect(
                List.of(
                    "Descrição do Produto",
                    "EAN",
                    "Valor Venda",
                    "Preço de Custo",
                    "Qtd. Estoque",
                    "Grupo",
                    "Observação livre"));

    assertEquals(ImportField.NAME, mappings.get(0).targetField());
    assertEquals(ImportField.BARCODE, mappings.get(1).targetField());
    assertEquals(ImportField.PRICE, mappings.get(2).targetField());
    assertEquals(ImportField.COST, mappings.get(3).targetField());
    assertEquals(ImportField.STOCK_QUANTITY, mappings.get(4).targetField());
    assertEquals(ImportField.CATEGORY, mappings.get(5).targetField());
    assertEquals(ImportField.IGNORE, mappings.get(6).targetField());
  }
}
