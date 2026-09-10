package com.binitech.imports.adapters.outbound.file;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.binitech.imports.application.ports.outbound.FileStoragePort;
import com.binitech.imports.domain.exception.ResourceNotFoundException;
import java.io.InputStream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

@Component
public class GridFsFileStorageAdapter implements FileStoragePort {
  private final GridFsTemplate gridFs;

  public GridFsFileStorageAdapter(GridFsTemplate gridFs) {
    this.gridFs = gridFs;
  }

  @Override
  public String store(InputStream content, String fileName, String contentType, String tenantId) {
    return gridFs
        .store(content, fileName, contentType, new Document("tenantId", tenantId))
        .toHexString();
  }

  @Override
  public InputStream open(String fileId, String tenantId) {
    try {
      var file =
          gridFs.findOne(
              Query.query(
                  where("_id").is(new ObjectId(fileId)).and("metadata.tenantId").is(tenantId)));
      if (file == null) throw new ResourceNotFoundException("Arquivo", fileId);
      return gridFs.getResource(file).getInputStream();
    } catch (ResourceNotFoundException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResourceNotFoundException("Arquivo", fileId);
    }
  }

  @Override
  public void delete(String fileId, String tenantId) {
    if (fileId == null) return;
    gridFs.delete(
        Query.query(where("_id").is(new ObjectId(fileId)).and("metadata.tenantId").is(tenantId)));
  }
}
