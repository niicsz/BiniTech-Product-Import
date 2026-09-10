package com.binitech.imports.application.ports.outbound;

public interface ImportQueuePort {
  void enqueue(String jobId);
}
