package com.binitech.imports.adapters.outbound.http;

import com.binitech.imports.application.ports.outbound.ProductCatalogPort;
import com.binitech.imports.domain.*;
import com.binitech.imports.domain.exception.BusinessException;
import com.binitech.imports.domain.exception.ExternalServiceUnavailableException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpProductCatalogAdapter implements ProductCatalogPort {
  private static final int LOOKUP_BATCH = 500;
  private final RestClient client;
  private final String serviceKey;

  public HttpProductCatalogAdapter(RestClient client, String serviceKey) {
    this.client = client;
    this.serviceKey = serviceKey;
  }

  @Override
  public void assertAuthorized(SessionIdentity identity) {
    invoke(
        () -> {
          client
              .post()
              .uri("/api/internal/product-import/authorize")
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-Product-Import-Service-Key", serviceKey)
              .body(IdentityRequest.from(identity))
              .retrieve()
              .toBodilessEntity();
          return null;
        });
  }

  @Override
  public Map<String, ExistingProduct> findExisting(Set<String> barcodes, SessionIdentity identity) {
    List<String> source = new ArrayList<>(barcodes);
    Map<String, ExistingProduct> result = new HashMap<>();
    for (int start = 0; start < source.size(); start += LOOKUP_BATCH) {
      List<String> batch = source.subList(start, Math.min(source.size(), start + LOOKUP_BATCH));
      LookupResponse response =
          invoke(
              () ->
                  client
                      .post()
                      .uri("/api/internal/product-import/lookup")
                      .contentType(MediaType.APPLICATION_JSON)
                      .header("X-Product-Import-Service-Key", serviceKey)
                      .body(new LookupRequest(IdentityRequest.from(identity), batch))
                      .retrieve()
                      .body(LookupResponse.class));
      if (response != null && response.products() != null) {
        response.products().forEach(product -> result.put(product.barcode(), product.toDomain()));
      }
    }
    return result;
  }

  @Override
  public List<ApplyResult> apply(
      String jobId,
      List<ImportRow> rows,
      ImportConfiguration configuration,
      SessionIdentity identity) {
    List<ApplyCommand> commands =
        rows.stream().map(row -> ApplyCommand.from(jobId, row, configuration)).toList();
    ApplyResponse response =
        invoke(
            () ->
                client
                    .post()
                    .uri("/api/internal/product-import/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Product-Import-Service-Key", serviceKey)
                    .body(
                        new ApplyRequest(
                            IdentityRequest.from(identity),
                            configuration.mode().name(),
                            configuration.stockMode().name(),
                            configuration.updateFields().stream().map(Enum::name).toList(),
                            commands))
                    .retrieve()
                    .body(ApplyResponse.class));
    if (response == null || response.results() == null)
      throw new ExternalServiceUnavailableException("O PDV não retornou o resultado do lote.");
    return response.results().stream()
        .map(
            r ->
                new ApplyResult(
                    r.lineNumber(), ResultAction.valueOf(r.action()), r.productId(), r.error()))
        .toList();
  }

  private <T> T invoke(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403)
        throw new AccessDeniedException("Usuário sem permissão para importar produtos.");
      if (exception.getStatusCode().value() == 402)
        throw new BusinessException("Tenant bloqueado ou com assinatura pendente.");
      if (exception.getStatusCode().is4xxClientError())
        throw new BusinessException("O PDV recusou os dados do lote.");
      throw new ExternalServiceUnavailableException("Serviço do PDV indisponível.", exception);
    } catch (RestClientException exception) {
      throw new ExternalServiceUnavailableException("Serviço do PDV indisponível.", exception);
    }
  }

  private record IdentityRequest(String userId, String tenantId) {
    static IdentityRequest from(SessionIdentity i) {
      return new IdentityRequest(i.userId(), i.tenantId());
    }
  }

  private record LookupRequest(IdentityRequest identity, List<String> barcodes) {}

  private record LookupResponse(List<ProductResponse> products) {}

  private record ProductResponse(
      String id,
      String barcode,
      String name,
      BigDecimal price,
      BigDecimal cost,
      int stockQuantity,
      String category,
      boolean active,
      String ownerId) {
    ExistingProduct toDomain() {
      return new ExistingProduct(
          id, barcode, name, price, cost, stockQuantity, category, active, ownerId);
    }
  }

  private record ApplyRequest(
      IdentityRequest identity,
      String mode,
      String stockMode,
      List<String> updateFields,
      List<ApplyCommand> commands) {}

  private record ApplyCommand(
      String operationId,
      int lineNumber,
      String barcode,
      String name,
      BigDecimal price,
      BigDecimal cost,
      Integer stockQuantity,
      String category,
      Boolean active) {
    static ApplyCommand from(String jobId, ImportRow row, ImportConfiguration ignored) {
      return new ApplyCommand(
          jobId + ":" + row.getLineNumber(),
          row.getLineNumber(),
          row.getBarcode(),
          row.getName(),
          row.getPrice(),
          row.getCost(),
          row.getStockQuantity(),
          row.getCategory(),
          row.getActive());
    }
  }

  private record ApplyResponse(List<ResultResponse> results) {}

  private record ResultResponse(int lineNumber, String action, String productId, String error) {}
}
