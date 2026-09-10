package com.binitech.imports.adapters.outbound.persistence.repository;

import com.binitech.imports.adapters.outbound.persistence.document.ImportJobDocument;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataImportJobRepository extends MongoRepository<ImportJobDocument, String> {
  Optional<ImportJobDocument> findByIdAndTenantId(String id, String tenantId);

  Optional<ImportJobDocument> findByTenantIdAndIdempotencyKey(
      String tenantId, String idempotencyKey);
}
