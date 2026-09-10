package com.binitech.imports.application.service;

import com.binitech.imports.domain.ColumnMapping;
import com.binitech.imports.domain.ImportField;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ColumnMappingDetector {
  private static final Map<ImportField, Set<String>> ALIASES = aliases();

  public List<ColumnMapping> detect(List<String> headers) {
    return headers.stream().map(this::detect).toList();
  }

  private ColumnMapping detect(String header) {
    String normalized = normalize(header);
    for (var entry : ALIASES.entrySet()) {
      if (entry.getValue().contains(normalized)) {
        return new ColumnMapping(header, entry.getKey(), 1.0);
      }
    }
    for (var entry : ALIASES.entrySet()) {
      if (entry.getValue().stream()
          .anyMatch(a -> normalized.contains(a) || a.contains(normalized))) {
        return new ColumnMapping(header, entry.getKey(), 0.75);
      }
    }
    return new ColumnMapping(header, ImportField.IGNORE, 0.0);
  }

  public static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private static Map<ImportField, Set<String>> aliases() {
    Map<ImportField, Set<String>> result = new LinkedHashMap<>();
    result.put(
        ImportField.NAME,
        Set.of("nome", "produto", "descricao", "descricao do produto", "nome do produto"));
    result.put(
        ImportField.BARCODE,
        Set.of("codigo de barras", "codigo barras", "barcode", "ean", "gtin", "codigo"));
    result.put(
        ImportField.PRICE,
        Set.of("preco", "preco venda", "valor venda", "valor de venda", "venda"));
    result.put(
        ImportField.COST,
        Set.of("custo", "preco custo", "preco de custo", "valor custo", "custo unitario"));
    result.put(
        ImportField.STOCK_QUANTITY,
        Set.of("estoque", "quantidade", "qtd estoque", "quantidade estoque", "saldo estoque"));
    result.put(ImportField.CATEGORY, Set.of("categoria", "grupo", "departamento", "secao"));
    result.put(ImportField.ACTIVE, Set.of("ativo", "status", "situacao", "inativo"));
    return result;
  }
}
