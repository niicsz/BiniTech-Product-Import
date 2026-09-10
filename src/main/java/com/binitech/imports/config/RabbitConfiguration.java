package com.binitech.imports.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
  public static final String EXCHANGE = "product-import.exchange";
  public static final String QUEUE = "product-import.jobs";
  public static final String ROUTING_KEY = "product-import.process";
  public static final String DEAD_QUEUE = "product-import.jobs.dead";

  @Bean
  DirectExchange productImportExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  Queue productImportQueue() {
    return QueueBuilder.durable(QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(DEAD_QUEUE)
        .build();
  }

  @Bean
  Queue productImportDeadQueue() {
    return QueueBuilder.durable(DEAD_QUEUE).build();
  }

  @Bean
  Binding productImportBinding(Queue productImportQueue, DirectExchange productImportExchange) {
    return BindingBuilder.bind(productImportQueue).to(productImportExchange).with(ROUTING_KEY);
  }

  @Bean
  Jackson2JsonMessageConverter productImportMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  RabbitTemplate productImportRabbitTemplate(
      ConnectionFactory factory, Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(factory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    return template;
  }
}
