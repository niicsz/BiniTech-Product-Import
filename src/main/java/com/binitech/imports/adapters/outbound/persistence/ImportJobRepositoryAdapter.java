package com.binitech.imports.adapters.outbound.persistence;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.binitech.imports.adapters.outbound.persistence.document.ImportJobDocument;
import com.binitech.imports.adapters.outbound.persistence.repository.SpringDataImportJobRepository;
import com.binitech.imports.application.ports.outbound.ImportJobRepositoryPort;
import com.binitech.imports.domain.ImportJob;
import com.binitech.imports.domain.ImportStatus;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class ImportJobRepositoryAdapter implements ImportJobRepositoryPort {
  private final SpringDataImportJobRepository repository;
  private final MongoTemplate mongo;

  public ImportJobRepositoryAdapter(SpringDataImportJobRepository repository, MongoTemplate mongo) {
    this.repository = repository;
    this.mongo = mongo;
  }

  @Override
  public ImportJob save(ImportJob job) {
    return repository.save(ImportJobDocument.fromDomain(job)).toDomain();
  }

  @Override
  public Optional<ImportJob> findByIdAndTenantId(String id, String tenantId) {
    return repository.findByIdAndTenantId(id, tenantId).map(ImportJobDocument::toDomain);
  }

  @Override
  public Optional<ImportJob> findById(String id) {
    return repository.findById(id).map(ImportJobDocument::toDomain);
  }

  @Override
  public Optional<ImportJob> findByTenantIdAndIdempotencyKey(String tenantId, String key) {
    return repository
        .findByTenantIdAndIdempotencyKey(tenantId, key)
        .map(ImportJobDocument::toDomain);
  }

  @Override
  public boolean tryStart(String id) {
    Query query =
        Query.query(
            where("_id")
                .is(id)
                .and("status")
                .is(ImportStatus.PENDING)
                .and("configuration")
                .ne(null));
    Update update = new Update().set("status", ImportStatus.PROCESSING);
    return mongo.findAndModify(
            query, update, FindAndModifyOptions.options().returnNew(true), ImportJobDocument.class)
        != null;
  }
}
