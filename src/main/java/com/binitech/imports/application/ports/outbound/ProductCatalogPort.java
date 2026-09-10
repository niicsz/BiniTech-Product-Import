package com.binitech.imports.application.ports.outbound;

import com.binitech.imports.domain.ImportConfiguration;
import com.binitech.imports.domain.ImportRow;
import com.binitech.imports.domain.SessionIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProductCatalogPort {
  void assertAuthorized(SessionIdentity identity);

  Map<String, ExistingProduct> findExisting(Set<String> barcodes, SessionIdentity identity);

  List<ApplyResult> apply(
      String jobId,
      List<ImportRow> rows,
      ImportConfiguration configuration,
      SessionIdentity identity);

  record ExistingProduct(
      String id,
      String barcode,
      String name,
      BigDecimal price,
      BigDecimal cost,
      int stockQuantity,
      String category,
      boolean active,
      String ownerId) {}

  record ApplyResult(int lineNumber, ResultAction action, String productId, String error) {}

  enum ResultAction {
    CREATED,
    UPDATED,
    IGNORED,
    ERROR
  }
}
