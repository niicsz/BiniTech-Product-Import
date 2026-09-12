package com.binitech.imports.adapters.outbound.persistence.document;

import com.binitech.imports.domain.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "product_import_jobs")
@CompoundIndexes({
  @CompoundIndex(name = "idx_job_tenant", def = "{'tenantId':1,'_id':1}"),
  @CompoundIndex(
      name = "uk_job_tenant_idempotency",
      def = "{'tenantId':1,'idempotencyKey':1}",
      unique = true,
      partialFilter = "{'idempotencyKey':{$type:'string'}}")
})
public record ImportJobDocument(
    @Id String id,
    String tenantId,
    String createdBy,
    String createdByRole,
    String idempotencyKey,
    String fileId,
    String fileName,
    String contentType,
    long fileSize,
    ImportStatus status,
    List<String> headers,
    List<ColumnMapping> suggestedMappings,
    List<Map<String, String>> sampleRows,
    String sampleRowsEncoding,
    List<ColumnMapping> mappings,
    DuplicateStrategy duplicateStrategy,
    ImportSummary summary,
    boolean validated,
    ImportConfiguration configuration,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    String failureMessage) {

  private static final String INDEXED_SAMPLE_ROWS = "indexed-v1";

  public static ImportJobDocument fromDomain(ImportJob source) {
    return new ImportJobDocument(
        source.getId(),
        source.getTenantId(),
        source.getCreatedBy(),
        source.getCreatedByRole(),
        source.getIdempotencyKey(),
        source.getFileId(),
        source.getFileName(),
        source.getContentType(),
        source.getFileSize(),
        source.getStatus(),
        source.getHeaders(),
        source.getSuggestedMappings(),
        encodeSampleRows(source.getHeaders(), source.getSampleRows()),
        INDEXED_SAMPLE_ROWS,
        source.getMappings(),
        source.getDuplicateStrategy(),
        source.getSummary(),
        source.isValidated(),
        source.getConfiguration(),
        source.getCreatedAt() == null ? null : source.getCreatedAt().toInstant(),
        source.getStartedAt() == null ? null : source.getStartedAt().toInstant(),
        source.getFinishedAt() == null ? null : source.getFinishedAt().toInstant(),
        source.getFailureMessage());
  }

  public ImportJob toDomain() {
    ImportJob target = new ImportJob();
    target.setId(id);
    target.setTenantId(tenantId);
    target.setCreatedBy(createdBy);
    target.setCreatedByRole(createdByRole);
    target.setIdempotencyKey(idempotencyKey);
    target.setFileId(fileId);
    target.setFileName(fileName);
    target.setContentType(contentType);
    target.setFileSize(fileSize);
    target.setStatus(status);
    target.setHeaders(headers == null ? List.of() : headers);
    target.setSuggestedMappings(suggestedMappings == null ? List.of() : suggestedMappings);
    target.setSampleRows(decodeSampleRows(headers, sampleRows, sampleRowsEncoding));
    target.setMappings(mappings == null ? List.of() : mappings);
    target.setDuplicateStrategy(
        duplicateStrategy == null ? DuplicateStrategy.ERROR : duplicateStrategy);
    target.setSummary(summary == null ? ImportSummary.empty() : summary);
    target.setValidated(validated);
    target.setConfiguration(configuration);
    target.setCreatedAt(createdAt == null ? null : createdAt.atOffset(ZoneOffset.UTC));
    target.setStartedAt(startedAt == null ? null : startedAt.atOffset(ZoneOffset.UTC));
    target.setFinishedAt(finishedAt == null ? null : finishedAt.atOffset(ZoneOffset.UTC));
    target.setFailureMessage(failureMessage);
    return target;
  }

  private static List<Map<String, String>> encodeSampleRows(
      List<String> headers, List<Map<String, String>> sampleRows) {
    if (headers == null || sampleRows == null || sampleRows.isEmpty()) return List.of();
    List<Map<String, String>> encoded = new ArrayList<>(sampleRows.size());
    for (Map<String, String> row : sampleRows) {
      Map<String, String> values = new LinkedHashMap<>();
      for (int index = 0; index < headers.size(); index++) {
        values.put(storageKey(index), row.getOrDefault(headers.get(index), ""));
      }
      encoded.add(Map.copyOf(values));
    }
    return List.copyOf(encoded);
  }

  private static List<Map<String, String>> decodeSampleRows(
      List<String> headers, List<Map<String, String>> sampleRows, String encoding) {
    if (sampleRows == null || sampleRows.isEmpty()) return List.of();
    if (!INDEXED_SAMPLE_ROWS.equals(encoding) || headers == null) return List.copyOf(sampleRows);
    List<Map<String, String>> decoded = new ArrayList<>(sampleRows.size());
    for (Map<String, String> row : sampleRows) {
      Map<String, String> values = new LinkedHashMap<>();
      for (int index = 0; index < headers.size(); index++) {
        values.put(headers.get(index), row.getOrDefault(storageKey(index), ""));
      }
      decoded.add(Map.copyOf(values));
    }
    return List.copyOf(decoded);
  }

  private static String storageKey(int index) {
    return "column_" + index;
  }
}
