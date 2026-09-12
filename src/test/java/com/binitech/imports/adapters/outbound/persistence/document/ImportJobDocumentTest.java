package com.binitech.imports.adapters.outbound.persistence.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.binitech.imports.domain.ImportJob;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportJobDocumentTest {

  @Test
  void persistsSampleRowsWithoutUsingExternalHeadersAsMongoKeys() {
    ImportJob job = new ImportJob();
    job.setHeaders(List.of("Descrição do Produto", "Qtd. Estoque", "$Categoria"));
    Map<String, String> sample = new LinkedHashMap<>();
    sample.put("Descrição do Produto", "Café");
    sample.put("Qtd. Estoque", "10");
    sample.put("$Categoria", "Mercearia");
    job.setSampleRows(List.of(sample));

    ImportJobDocument document = ImportJobDocument.fromDomain(job);

    assertEquals("indexed-v1", document.sampleRowsEncoding());
    assertFalse(
        document.sampleRows().getFirst().keySet().stream()
            .anyMatch(key -> key.contains(".") || key.startsWith("$")));
    assertEquals(List.of(sample), document.toDomain().getSampleRows());
  }
}
