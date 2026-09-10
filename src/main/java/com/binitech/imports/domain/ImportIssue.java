package com.binitech.imports.domain;

import java.util.List;

public record ImportIssue(
    IssueSeverity severity, String field, String code, String message, List<Integer> relatedLines) {
  public ImportIssue(IssueSeverity severity, String field, String code, String message) {
    this(severity, field, code, message, List.of());
  }
}
