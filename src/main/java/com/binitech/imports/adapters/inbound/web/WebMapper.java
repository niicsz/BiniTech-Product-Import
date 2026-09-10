package com.binitech.imports.adapters.inbound.web;

import com.binitech.imports.adapters.inbound.web.generated.model.*;
import com.binitech.imports.domain.ColumnMapping;
import com.binitech.imports.domain.ImportIssue;
import com.binitech.imports.domain.ImportJob;
import com.binitech.imports.domain.ImportRow;

public final class WebMapper {
  private WebMapper() {}

  public static ImportJobDTO job(ImportJob source) {
    var summary = source.getSummary();
    return new ImportJobDTO()
        .id(source.getId())
        .status(ImportStatus.fromValue(source.getStatus().name()))
        .fileName(source.getFileName())
        .fileSize(source.getFileSize())
        .headers(source.getHeaders())
        .suggestedMappings(source.getSuggestedMappings().stream().map(WebMapper::mapping).toList())
        .sampleRows(source.getSampleRows())
        .summary(
            new ImportSummaryDTO()
                .totalRecords(summary.totalRecords())
                .validRecords(summary.validRecords())
                .warningRecords(summary.warningRecords())
                .errorRecords(summary.errorRecords())
                .processedRecords(summary.processedRecords())
                .createdRecords(summary.createdRecords())
                .updatedRecords(summary.updatedRecords())
                .ignoredRecords(summary.ignoredRecords()))
        .validated(source.isValidated())
        .progressPercentage(source.progressPercentage())
        .createdAt(source.getCreatedAt())
        .startedAt(source.getStartedAt())
        .finishedAt(source.getFinishedAt())
        .failureMessage(source.getFailureMessage());
  }

  public static ImportRowDTO row(ImportRow source) {
    return new ImportRowDTO()
        .lineNumber(source.getLineNumber())
        .name(source.getName())
        .barcode(source.getBarcode())
        .price(source.getPrice())
        .cost(source.getCost())
        .stockQuantity(source.getStockQuantity())
        .category(source.getCategory())
        .active(source.getActive())
        .action(RowAction.fromValue(source.getAction().name()))
        .existingProductId(source.getExistingProductId())
        .currentPrice(source.getCurrentPrice())
        .currentStockQuantity(source.getCurrentStockQuantity())
        .issues(source.getIssues().stream().map(WebMapper::issue).toList());
  }

  public static ColumnMapping mapping(ColumnMappingDTO source) {
    return new ColumnMapping(
        source.getSourceColumn(),
        com.binitech.imports.domain.ImportField.valueOf(source.getTargetField().name()),
        source.getConfidence() == null ? 1.0 : source.getConfidence());
  }

  public static ColumnMappingDTO mapping(ColumnMapping source) {
    return new ColumnMappingDTO(
            source.sourceColumn(), ImportField.fromValue(source.targetField().name()))
        .confidence(source.confidence());
  }

  private static ImportIssueDTO issue(ImportIssue source) {
    return new ImportIssueDTO()
        .severity(ImportIssueDTO.SeverityEnum.fromValue(source.severity().name()))
        .field(source.field())
        .code(source.code())
        .message(source.message())
        .relatedLines(source.relatedLines());
  }
}
