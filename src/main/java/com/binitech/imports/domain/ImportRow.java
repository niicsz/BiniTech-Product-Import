package com.binitech.imports.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ImportRow {
  private String id;
  private String jobId;
  private String tenantId;
  private int lineNumber;
  private String name;
  private String barcode;
  private BigDecimal price;
  private BigDecimal cost;
  private Integer stockQuantity;
  private String category;
  private Boolean active;
  private RowAction action = RowAction.CREATE;
  private String existingProductId;
  private BigDecimal currentPrice;
  private Integer currentStockQuantity;
  private List<ImportIssue> issues = new ArrayList<>();
  private boolean processed;

  public void addIssue(ImportIssue issue) {
    issues.add(issue);
    if (issue.severity() == IssueSeverity.ERROR) action = RowAction.ERROR;
  }

  public boolean hasErrors() {
    return issues.stream().anyMatch(i -> i.severity() == IssueSeverity.ERROR);
  }

  public boolean hasWarnings() {
    return issues.stream().anyMatch(i -> i.severity() == IssueSeverity.WARNING);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal cost) {
    this.cost = cost;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public RowAction getAction() {
    return action;
  }

  public void setAction(RowAction action) {
    this.action = action;
  }

  public String getExistingProductId() {
    return existingProductId;
  }

  public void setExistingProductId(String existingProductId) {
    this.existingProductId = existingProductId;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(BigDecimal currentPrice) {
    this.currentPrice = currentPrice;
  }

  public Integer getCurrentStockQuantity() {
    return currentStockQuantity;
  }

  public void setCurrentStockQuantity(Integer currentStockQuantity) {
    this.currentStockQuantity = currentStockQuantity;
  }

  public List<ImportIssue> getIssues() {
    return issues;
  }

  public void setIssues(List<ImportIssue> issues) {
    this.issues = new ArrayList<>(issues == null ? List.of() : issues);
  }

  public boolean isProcessed() {
    return processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }
}
