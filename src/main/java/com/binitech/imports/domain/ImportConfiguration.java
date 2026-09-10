package com.binitech.imports.domain;

import java.util.Set;

public record ImportConfiguration(
    ImportMode mode, StockMode stockMode, Set<ImportField> updateFields) {
  public ImportConfiguration {
    updateFields = updateFields == null ? Set.of() : Set.copyOf(updateFields);
  }
}
