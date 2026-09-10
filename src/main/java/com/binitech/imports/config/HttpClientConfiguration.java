package com.binitech.imports.config;

import com.binitech.imports.adapters.outbound.http.HttpAuthenticationAdapter;
import com.binitech.imports.adapters.outbound.http.HttpProductCatalogAdapter;
import com.binitech.imports.application.ports.outbound.AuthenticationPort;
import com.binitech.imports.application.ports.outbound.ProductCatalogPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {
  @Bean
  AuthenticationPort authenticationPort(
      @Value("${auth.service-url}") String url,
      @Value("${auth.connect-timeout}") Duration connectTimeout,
      @Value("${auth.read-timeout}") Duration readTimeout) {
    return new HttpAuthenticationAdapter(client(url, connectTimeout, readTimeout));
  }

  @Bean
  ProductCatalogPort productCatalogPort(
      @Value("${pdv.service-url}") String url,
      @Value("${pdv.service-key}") String key,
      @Value("${pdv.connect-timeout}") Duration connectTimeout,
      @Value("${pdv.read-timeout}") Duration readTimeout) {
    if (key == null || key.length() < 32)
      throw new IllegalArgumentException(
          "PRODUCT_IMPORT_SERVICE_KEY deve ter ao menos 32 caracteres.");
    return new HttpProductCatalogAdapter(client(url, connectTimeout, readTimeout), key);
  }

  private RestClient client(String url, Duration connectTimeout, Duration readTimeout) {
    if (!url.matches("https?://.+")) throw new IllegalArgumentException("URL de serviço inválida.");
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder()
        .baseUrl(url.replaceAll("/$", ""))
        .requestFactory(requestFactory)
        .build();
  }
}
