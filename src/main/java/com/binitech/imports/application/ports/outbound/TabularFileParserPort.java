package com.binitech.imports.application.ports.outbound;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface TabularFileParserPort {
  ParseResult parse(InputStream content, String fileName, int maxRecords, RowHandler handler);

  record ParseResult(
      List<String> headers, List<Map<String, String>> sampleRows, int totalRecords) {}

  @FunctionalInterface
  interface RowHandler {
    void accept(int lineNumber, Map<String, String> values);
  }
}
