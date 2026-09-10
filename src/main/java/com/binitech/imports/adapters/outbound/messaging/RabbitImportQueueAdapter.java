package com.binitech.imports.adapters.outbound.messaging;

import com.binitech.imports.application.ports.outbound.ImportQueuePort;
import com.binitech.imports.config.RabbitConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitImportQueueAdapter implements ImportQueuePort {
  private final RabbitTemplate rabbit;

  public RabbitImportQueueAdapter(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  @Override
  public void enqueue(String jobId) {
    rabbit.convertAndSend(
        RabbitConfiguration.EXCHANGE, RabbitConfiguration.ROUTING_KEY, new ImportJobMessage(jobId));
  }

  public record ImportJobMessage(String jobId) {}
}
