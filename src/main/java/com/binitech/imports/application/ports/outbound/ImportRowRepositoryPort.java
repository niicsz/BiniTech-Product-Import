package com.binitech.imports.application.ports.outbound;

import com.binitech.imports.domain.ImportRow;
import java.util.List;
import java.util.Optional;

public interface ImportRowRepositoryPort {
  void deleteByJobIdAndTenantId(String jobId, String tenantId);

  void saveAll(List<ImportRow> rows);

  List<ImportRow> findPage(String jobId, String tenantId, int page, int size, boolean issuesOnly);

  List<ImportRow> findPendingPage(String jobId, String tenantId, int size);

  List<ImportRow> findIssues(String jobId, String tenantId);

  Optional<ImportRow> findByJobIdAndTenantIdAndLineNumber(
      String jobId, String tenantId, int lineNumber);
}
