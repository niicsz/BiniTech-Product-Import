package com.binitech.imports.domain;

public record ImportSummary(
    int totalRecords,
    int validRecords,
    int warningRecords,
    int errorRecords,
    int processedRecords,
    int createdRecords,
    int updatedRecords,
    int ignoredRecords) {
  public static ImportSummary empty() {
    return new ImportSummary(0, 0, 0, 0, 0, 0, 0, 0);
  }
}
