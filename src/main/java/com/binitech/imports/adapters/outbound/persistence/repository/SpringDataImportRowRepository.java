package com.binitech.imports.adapters.outbound.persistence.repository;

import com.binitech.imports.adapters.outbound.persistence.document.ImportRowDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SpringDataImportRowRepository extends MongoRepository<ImportRowDocument, String> {
  void deleteByJobIdAndTenantId(String jobId, String tenantId);

  List<ImportRowDocument> findByJobIdAndTenantId(String jobId, String tenantId, Pageable pageable);

  List<ImportRowDocument> findByJobIdAndTenantIdAndProcessedFalse(
      String jobId, String tenantId, Pageable pageable);

  Optional<ImportRowDocument> findByJobIdAndTenantIdAndLineNumber(
      String jobId, String tenantId, int lineNumber);

  @Query("{'jobId':?0,'tenantId':?1,'issues.0':{$exists:true}}")
  List<ImportRowDocument> findIssues(String jobId, String tenantId, Pageable pageable);
}
