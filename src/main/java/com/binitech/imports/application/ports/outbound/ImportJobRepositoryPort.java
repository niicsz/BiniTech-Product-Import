package com.binitech.imports.application.ports.outbound;

import com.binitech.imports.domain.ImportJob;
import java.util.Optional;

public interface ImportJobRepositoryPort {
  ImportJob save(ImportJob job);

  Optional<ImportJob> findByIdAndTenantId(String id, String tenantId);

  Optional<ImportJob> findById(String id);

  Optional<ImportJob> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

  boolean tryStart(String id);
}
