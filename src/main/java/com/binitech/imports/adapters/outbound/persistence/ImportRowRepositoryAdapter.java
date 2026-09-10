package com.binitech.imports.adapters.outbound.persistence;

import com.binitech.imports.adapters.outbound.persistence.document.ImportRowDocument;
import com.binitech.imports.adapters.outbound.persistence.repository.SpringDataImportRowRepository;
import com.binitech.imports.application.ports.outbound.ImportRowRepositoryPort;
import com.binitech.imports.domain.ImportRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class ImportRowRepositoryAdapter implements ImportRowRepositoryPort {
  private static final Sort BY_LINE = Sort.by("lineNumber").ascending();
  private final SpringDataImportRowRepository repository;

  public ImportRowRepositoryAdapter(SpringDataImportRowRepository repository) {
    this.repository = repository;
  }

  @Override
  public void deleteByJobIdAndTenantId(String jobId, String tenantId) {
    repository.deleteByJobIdAndTenantId(jobId, tenantId);
  }

  @Override
  public void saveAll(List<ImportRow> rows) {
    repository.saveAll(rows.stream().map(ImportRowDocument::fromDomain).toList());
  }

  @Override
  public List<ImportRow> findPage(
      String jobId, String tenantId, int page, int size, boolean issuesOnly) {
    var pageable = PageRequest.of(page, size, BY_LINE);
    var found =
        issuesOnly
            ? repository.findIssues(jobId, tenantId, pageable)
            : repository.findByJobIdAndTenantId(jobId, tenantId, pageable);
    return found.stream().map(ImportRowDocument::toDomain).toList();
  }

  @Override
  public List<ImportRow> findPendingPage(String jobId, String tenantId, int size) {
    return repository
        .findByJobIdAndTenantIdAndProcessedFalse(jobId, tenantId, PageRequest.of(0, size, BY_LINE))
        .stream()
        .map(ImportRowDocument::toDomain)
        .toList();
  }

  @Override
  public List<ImportRow> findIssues(String jobId, String tenantId) {
    return repository.findIssues(jobId, tenantId, PageRequest.of(0, 50_000, BY_LINE)).stream()
        .map(ImportRowDocument::toDomain)
        .toList();
  }

  @Override
  public Optional<ImportRow> findByJobIdAndTenantIdAndLineNumber(
      String jobId, String tenantId, int lineNumber) {
    return repository
        .findByJobIdAndTenantIdAndLineNumber(jobId, tenantId, lineNumber)
        .map(ImportRowDocument::toDomain);
  }
}
