package com.binitech.imports.adapters.inbound.web;

import com.binitech.imports.adapters.inbound.web.generated.api.ProductImportsApi;
import com.binitech.imports.adapters.inbound.web.generated.model.*;
import com.binitech.imports.application.ports.inbound.ProductImportUseCasePort;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImportController implements ProductImportsApi {
  private final ProductImportUseCasePort useCase;
  private final AuthenticatedUserProvider users;

  public ProductImportController(
      ProductImportUseCasePort useCase, AuthenticatedUserProvider users) {
    this.useCase = useCase;
    this.users = users;
  }

  @Override
  public ResponseEntity<ImportJobDTO> uploadProductImport(
      MultipartFile file, String idempotencyKey) {
    try {
      var job =
          useCase.upload(
              file.getInputStream(),
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              idempotencyKey,
              users.current());
      return ResponseEntity.status(HttpStatus.CREATED).body(WebMapper.job(job));
    } catch (IOException exception) {
      throw new IllegalArgumentException("Não foi possível receber o arquivo.", exception);
    }
  }

  @Override
  public ResponseEntity<ImportJobDTO> validateProductImport(
      String id, ValidateImportRequest request) {
    var mappings = request.getMappings().stream().map(WebMapper::mapping).toList();
    var strategy =
        request.getDuplicateStrategy() == null
            ? com.binitech.imports.domain.DuplicateStrategy.ERROR
            : com.binitech.imports.domain.DuplicateStrategy.valueOf(
                request.getDuplicateStrategy().name());
    return ResponseEntity.ok(
        WebMapper.job(useCase.validate(id, mappings, strategy, users.current())));
  }

  @Override
  public ResponseEntity<ImportJobDTO> startProductImport(String id, StartImportRequest request) {
    var fields =
        java.util.Optional.ofNullable(request.getUpdateFields())
            .orElseGet(java.util.Set::of)
            .stream()
            .map(f -> com.binitech.imports.domain.ImportField.valueOf(f.name()))
            .collect(java.util.stream.Collectors.toSet());
    var job =
        useCase.start(
            id,
            com.binitech.imports.domain.ImportMode.valueOf(request.getMode().name()),
            com.binitech.imports.domain.StockMode.valueOf(request.getStockMode().name()),
            fields,
            Boolean.TRUE.equals(request.getConfirmStockChange()),
            users.current());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(WebMapper.job(job));
  }

  @Override
  public ResponseEntity<ImportJobDTO> getProductImport(String id) {
    return ResponseEntity.ok(WebMapper.job(useCase.get(id, users.current())));
  }

  @Override
  public ResponseEntity<List<ImportRowDTO>> listProductImportRows(
      String id, Integer page, Integer size, Boolean issuesOnly) {
    return ResponseEntity.ok(
        useCase
            .rows(
                id,
                page == null ? 0 : page,
                size == null ? 50 : size,
                Boolean.TRUE.equals(issuesOnly),
                users.current())
            .stream()
            .map(WebMapper::row)
            .toList());
  }

  @Override
  public ResponseEntity<ImportRowDTO> overrideProductImportRow(
      String id, Integer lineNumber, RowActionOverrideRequest request) {
    var action = com.binitech.imports.domain.RowAction.valueOf(request.getAction().name());
    return ResponseEntity.ok(
        WebMapper.row(useCase.overrideRowAction(id, lineNumber, action, users.current())));
  }

  @Override
  public ResponseEntity<Void> cancelProductImport(String id) {
    useCase.cancel(id, users.current());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Resource> downloadProductImportTemplate() {
    return download("modelo-importacao-produtos.csv", useCase.template());
  }

  @Override
  public ResponseEntity<Resource> downloadProductImportErrors(String id) {
    return download("erros-importacao-" + id + ".csv", useCase.errorReport(id, users.current()));
  }

  private ResponseEntity<Resource> download(String fileName, byte[] bytes) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(new ByteArrayResource(bytes));
  }
}
