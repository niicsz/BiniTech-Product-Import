package com.binitech.imports.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class ImportJob {
  private String id;
  private String tenantId;
  private String createdBy;
  private String createdByRole;
  private String idempotencyKey;
  private String fileId;
  private String fileName;
  private String contentType;
  private long fileSize;
  private ImportStatus status = ImportStatus.PENDING;
  private List<String> headers = List.of();
  private List<ColumnMapping> suggestedMappings = List.of();
  private List<Map<String, String>> sampleRows = List.of();
  private List<ColumnMapping> mappings = List.of();
  private DuplicateStrategy duplicateStrategy = DuplicateStrategy.ERROR;
  private ImportSummary summary = ImportSummary.empty();
  private boolean validated;
  private ImportConfiguration configuration;
  private OffsetDateTime createdAt;
  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;
  private String failureMessage;

  public int progressPercentage() {
    return summary.totalRecords() == 0
        ? 0
        : Math.min(100, summary.processedRecords() * 100 / summary.totalRecords());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getCreatedByRole() {
    return createdByRole;
  }

  public void setCreatedByRole(String createdByRole) {
    this.createdByRole = createdByRole;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public ImportStatus getStatus() {
    return status;
  }

  public void setStatus(ImportStatus status) {
    this.status = status;
  }

  public List<String> getHeaders() {
    return headers;
  }

  public void setHeaders(List<String> headers) {
    this.headers = headers;
  }

  public List<ColumnMapping> getSuggestedMappings() {
    return suggestedMappings;
  }

  public void setSuggestedMappings(List<ColumnMapping> suggestedMappings) {
    this.suggestedMappings = suggestedMappings;
  }

  public List<Map<String, String>> getSampleRows() {
    return sampleRows;
  }

  public void setSampleRows(List<Map<String, String>> sampleRows) {
    this.sampleRows = sampleRows;
  }

  public List<ColumnMapping> getMappings() {
    return mappings;
  }

  public void setMappings(List<ColumnMapping> mappings) {
    this.mappings = mappings;
  }

  public DuplicateStrategy getDuplicateStrategy() {
    return duplicateStrategy;
  }

  public void setDuplicateStrategy(DuplicateStrategy duplicateStrategy) {
    this.duplicateStrategy = duplicateStrategy;
  }

  public ImportSummary getSummary() {
    return summary;
  }

  public void setSummary(ImportSummary summary) {
    this.summary = summary;
  }

  public boolean isValidated() {
    return validated;
  }

  public void setValidated(boolean validated) {
    this.validated = validated;
  }

  public ImportConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(ImportConfiguration configuration) {
    this.configuration = configuration;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(OffsetDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
  }
}
