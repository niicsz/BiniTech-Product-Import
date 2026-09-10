package com.binitech.imports.application.usecases;

import static org.junit.jupiter.api.Assertions.*;

import com.binitech.imports.application.ports.outbound.ProductCatalogPort;
import com.binitech.imports.domain.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessProductImportUseCaseTest {
  @Test
  void processesCreateUpdateIgnoreAndPartialErrorOnlyOnce() {
    var jobs = new ProductImportUseCaseTest.MemoryJobs();
    var rows = new ProductImportUseCaseTest.MemoryRows();
    ImportJob job = new ImportJob();
    job.setTenantId("tenant-a");
    job.setCreatedBy("user-a");
    job.setCreatedByRole("TENANT_ADMIN");
    job.setValidated(true);
    job.setConfiguration(
        new ImportConfiguration(
            ImportMode.CREATE_AND_UPDATE, StockMode.IGNORE, EnumSet.of(ImportField.PRICE)));
    job.setSummary(new ImportSummary(5, 4, 0, 1, 0, 0, 0, 0));
    jobs.save(job);
    rows.saveAll(
        List.of(
            row(job, 2, RowAction.CREATE),
            row(job, 3, RowAction.UPDATE),
            row(job, 4, RowAction.CREATE),
            row(job, 5, RowAction.IGNORE),
            row(job, 6, RowAction.ERROR)));
    RecordingCatalog catalog = new RecordingCatalog();
    var processor = new ProcessProductImportUseCase(jobs, rows, catalog, 100);

    processor.process(job.getId());
    processor.process(job.getId());

    assertEquals(1, catalog.calls);
    assertEquals(ImportStatus.COMPLETED_WITH_ERRORS, job.getStatus());
    assertEquals(5, job.getSummary().processedRecords());
    assertEquals(1, job.getSummary().createdRecords());
    assertEquals(1, job.getSummary().updatedRecords());
    assertEquals(1, job.getSummary().ignoredRecords());
    assertEquals(2, job.getSummary().errorRecords());
    assertTrue(rows.data.stream().allMatch(ImportRow::isProcessed));
  }

  @Test
  void concurrentConsumersClaimTheSameJobOnlyOnce() throws Exception {
    var jobs = new ProductImportUseCaseTest.MemoryJobs();
    var rows = new ProductImportUseCaseTest.MemoryRows();
    ImportJob job = new ImportJob();
    job.setTenantId("tenant-a");
    job.setCreatedBy("user-a");
    job.setCreatedByRole("TENANT_ADMIN");
    job.setValidated(true);
    job.setConfiguration(
        new ImportConfiguration(ImportMode.CREATE_ONLY, StockMode.IGNORE, Set.of()));
    job.setSummary(new ImportSummary(1, 1, 0, 0, 0, 0, 0, 0));
    jobs.save(job);
    rows.saveAll(List.of(row(job, 2, RowAction.CREATE)));
    RecordingCatalog catalog = new RecordingCatalog();
    var processor = new ProcessProductImportUseCase(jobs, rows, catalog, 100);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> runAfter(start, processor, job.getId()));
      var second = executor.submit(() -> runAfter(start, processor, job.getId()));
      start.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    }

    assertEquals(1, catalog.calls);
    assertEquals(ImportStatus.COMPLETED, job.getStatus());
  }

  private static void runAfter(
      CountDownLatch start, ProcessProductImportUseCase processor, String jobId) {
    try {
      start.await();
      processor.process(jobId);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static ImportRow row(ImportJob job, int line, RowAction action) {
    ImportRow row = new ImportRow();
    row.setJobId(job.getId());
    row.setTenantId(job.getTenantId());
    row.setLineNumber(line);
    row.setName("Produto " + line);
    row.setBarcode("code-" + line);
    row.setAction(action);
    if (action == RowAction.ERROR)
      row.addIssue(new ImportIssue(IssueSeverity.ERROR, "name", "TEST", "inválido"));
    return row;
  }

  static final class RecordingCatalog implements ProductCatalogPort {
    int calls;

    public void assertAuthorized(SessionIdentity identity) {}

    public Map<String, ExistingProduct> findExisting(
        Set<String> barcodes, SessionIdentity identity) {
      return Map.of();
    }

    public List<ApplyResult> apply(
        String jobId,
        List<ImportRow> rows,
        ImportConfiguration configuration,
        SessionIdentity identity) {
      calls++;
      return List.of(
          new ApplyResult(2, ResultAction.CREATED, "p-2", null),
          new ApplyResult(3, ResultAction.UPDATED, "p-3", null),
          new ApplyResult(4, ResultAction.ERROR, null, "produto recusado"));
    }
  }
}
