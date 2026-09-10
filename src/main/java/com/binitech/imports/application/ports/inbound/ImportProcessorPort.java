package com.binitech.imports.application.ports.inbound;

public interface ImportProcessorPort {
  void process(String jobId);
}
