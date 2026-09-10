package com.binitech.imports.adapters.outbound.persistence.document;

import com.binitech.imports.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "product_import_rows")
@CompoundIndexes({
  @CompoundIndex(name = "uk_row_job_line", def = "{'jobId':1,'lineNumber':1}", unique = true),
  @CompoundIndex(
      name = "idx_row_tenant_job_processed",
      def = "{'tenantId':1,'jobId':1,'processed':1,'lineNumber':1}")
})
public record ImportRowDocument(
    @Id String id,
    String jobId,
    String tenantId,
    int lineNumber,
    String name,
    String barcode,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal price,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal cost,
    Integer stockQuantity,
    String category,
    Boolean active,
    RowAction action,
    String existingProductId,
    @Field(targetType = FieldType.DECIMAL128) BigDecimal currentPrice,
    Integer currentStockQuantity,
    List<ImportIssue> issues,
    boolean processed) {
  public static ImportRowDocument fromDomain(ImportRow source) {
    return new ImportRowDocument(
        source.getId(),
        source.getJobId(),
        source.getTenantId(),
        source.getLineNumber(),
        source.getName(),
        source.getBarcode(),
        source.getPrice(),
        source.getCost(),
        source.getStockQuantity(),
        source.getCategory(),
        source.getActive(),
        source.getAction(),
        source.getExistingProductId(),
        source.getCurrentPrice(),
        source.getCurrentStockQuantity(),
        source.getIssues(),
        source.isProcessed());
  }

  public ImportRow toDomain() {
    ImportRow target = new ImportRow();
    target.setId(id);
    target.setJobId(jobId);
    target.setTenantId(tenantId);
    target.setLineNumber(lineNumber);
    target.setName(name);
    target.setBarcode(barcode);
    target.setPrice(price);
    target.setCost(cost);
    target.setStockQuantity(stockQuantity);
    target.setCategory(category);
    target.setActive(active);
    target.setAction(action);
    target.setExistingProductId(existingProductId);
    target.setCurrentPrice(currentPrice);
    target.setCurrentStockQuantity(currentStockQuantity);
    target.setIssues(issues == null ? List.of() : issues);
    target.setProcessed(processed);
    return target;
  }
}
