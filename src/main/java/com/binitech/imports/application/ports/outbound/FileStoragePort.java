package com.binitech.imports.application.ports.outbound;

import java.io.InputStream;

public interface FileStoragePort {
  String store(InputStream content, String fileName, String contentType, String tenantId);

  InputStream open(String fileId, String tenantId);

  void delete(String fileId, String tenantId);
}
