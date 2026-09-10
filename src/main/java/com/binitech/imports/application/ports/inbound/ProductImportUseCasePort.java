package com.binitech.imports.application.ports.inbound;

import com.binitech.imports.domain.*;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public interface ProductImportUseCasePort {
  ImportJob upload(
      InputStream content,
      String fileName,
      String contentType,
      long fileSize,
      String idempotencyKey,
      SessionIdentity identity);

  ImportJob validate(
      String id,
      List<ColumnMapping> mappings,
      DuplicateStrategy duplicateStrategy,
      SessionIdentity identity);

  ImportJob start(
      String id,
      ImportMode mode,
      StockMode stockMode,
      Set<ImportField> updateFields,
      boolean confirmStock,
      SessionIdentity identity);

  ImportJob get(String id, SessionIdentity identity);

  List<ImportRow> rows(String id, int page, int size, boolean issuesOnly, SessionIdentity identity);

  ImportRow overrideRowAction(
      String id, int lineNumber, RowAction action, SessionIdentity identity);

  byte[] errorReport(String id, SessionIdentity identity);

  void cancel(String id, SessionIdentity identity);

  byte[] template();
}
