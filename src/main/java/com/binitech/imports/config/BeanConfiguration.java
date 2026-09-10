package com.binitech.imports.config;

import com.binitech.imports.application.ports.inbound.*;
import com.binitech.imports.application.ports.outbound.*;
import com.binitech.imports.application.usecases.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
  @Bean
  ProductImportUseCasePort productImportUseCase(
      ImportJobRepositoryPort jobs,
      ImportRowRepositoryPort rows,
      FileStoragePort files,
      TabularFileParserPort parser,
      ProductCatalogPort catalog,
      ImportQueuePort queue,
      @Value("${imports.max-file-bytes}") int maxFileBytes,
      @Value("${imports.max-records}") int maxRecords) {
    return new ProductImportUseCase(
        jobs, rows, files, parser, catalog, queue, maxFileBytes, maxRecords);
  }

  @Bean
  ImportProcessorPort importProcessor(
      ImportJobRepositoryPort jobs,
      ImportRowRepositoryPort rows,
      ProductCatalogPort catalog,
      @Value("${imports.batch-size}") int batchSize) {
    return new ProcessProductImportUseCase(jobs, rows, catalog, batchSize);
  }
}
