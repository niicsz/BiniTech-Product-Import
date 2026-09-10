package com.binitech.imports.application.usecases;

import com.binitech.imports.application.ports.inbound.ImportProcessorPort;
import com.binitech.imports.application.ports.outbound.ImportJobRepositoryPort;
import com.binitech.imports.application.ports.outbound.ImportRowRepositoryPort;
import com.binitech.imports.application.ports.outbound.ProductCatalogPort;
import com.binitech.imports.domain.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessProductImportUseCase implements ImportProcessorPort {
  private final ImportJobRepositoryPort jobs;
  private final ImportRowRepositoryPort rows;
  private final ProductCatalogPort catalog;
  private final int batchSize;

  public ProcessProductImportUseCase(
      ImportJobRepositoryPort jobs,
      ImportRowRepositoryPort rows,
      ProductCatalogPort catalog,
      int batchSize) {
    this.jobs = jobs;
    this.rows = rows;
    this.catalog = catalog;
    this.batchSize = batchSize;
  }

  @Override
  public void process(String jobId) {
    ImportJob initial = jobs.findById(jobId).orElse(null);
    if (initial == null
        || initial.getConfiguration() == null
        || initial.getStatus() == ImportStatus.CANCELLED
        || initial.getStatus() == ImportStatus.COMPLETED
        || initial.getStatus() == ImportStatus.COMPLETED_WITH_ERRORS) return;
    if (!jobs.tryStart(jobId)) return;

    ImportJob job = jobs.findById(jobId).orElseThrow();
    job.setStartedAt(
        job.getStartedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : job.getStartedAt());
    jobs.save(job);
    SessionIdentity identity =
        new SessionIdentity(
            job.getCreatedBy(), "import-worker", job.getCreatedByRole(), job.getTenantId());
    try {
      while (true) {
        job = jobs.findById(jobId).orElseThrow();
        if (job.getStatus() == ImportStatus.CANCELLED) return;
        List<ImportRow> batch = rows.findPendingPage(jobId, job.getTenantId(), batchSize);
        if (batch.isEmpty()) break;
        processBatch(job, batch, identity);
      }
      job = jobs.findById(jobId).orElseThrow();
      job.setStatus(
          job.getSummary().errorRecords() > 0
              ? ImportStatus.COMPLETED_WITH_ERRORS
              : ImportStatus.COMPLETED);
      job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
      jobs.save(job);
    } catch (Exception exception) {
      job = jobs.findById(jobId).orElse(job);
      job.setStatus(ImportStatus.FAILED);
      job.setFailureMessage(safeMessage(exception));
      job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
      jobs.save(job);
    }
  }

  private void processBatch(ImportJob job, List<ImportRow> batch, SessionIdentity identity) {
    List<ImportRow> actionable =
        batch.stream()
            .filter(r -> r.getAction() == RowAction.CREATE || r.getAction() == RowAction.UPDATE)
            .toList();
    Map<Integer, ProductCatalogPort.ApplyResult> results = new HashMap<>();
    if (!actionable.isEmpty()) {
      for (var result : catalog.apply(job.getId(), actionable, job.getConfiguration(), identity)) {
        results.put(result.lineNumber(), result);
      }
    }

    int created = 0;
    int updated = 0;
    int ignored = 0;
    int newErrors = 0;
    List<ImportRow> changed = new ArrayList<>();
    for (ImportRow row : batch) {
      if (row.getAction() == RowAction.ERROR) {
        row.setProcessed(true);
      } else if (row.getAction() == RowAction.IGNORE) {
        row.setProcessed(true);
        ignored++;
      } else {
        var result = results.get(row.getLineNumber());
        if (result == null) {
          row.addIssue(
              new ImportIssue(
                  IssueSeverity.ERROR,
                  null,
                  "NO_RESULT",
                  "O PDV não retornou resultado para a linha."));
          newErrors++;
        } else {
          switch (result.action()) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case IGNORED -> ignored++;
            case ERROR -> {
              row.addIssue(
                  new ImportIssue(IssueSeverity.ERROR, null, "IMPORT_ERROR", result.error()));
              newErrors++;
            }
          }
        }
        row.setProcessed(true);
      }
      changed.add(row);
    }
    rows.saveAll(changed);
    ImportJob latest = jobs.findById(job.getId()).orElse(job);
    ImportSummary old = latest.getSummary();
    latest.setSummary(
        new ImportSummary(
            old.totalRecords(),
            old.validRecords(),
            old.warningRecords(),
            old.errorRecords() + newErrors,
            old.processedRecords() + batch.size(),
            old.createdRecords() + created,
            old.updatedRecords() + updated,
            old.ignoredRecords() + ignored));
    jobs.save(latest);
  }

  private static String safeMessage(Exception exception) {
    String value = exception.getMessage();
    if (value == null || value.isBlank()) return "Falha inesperada no processamento.";
    return value.length() > 300 ? value.substring(0, 300) : value;
  }
}
