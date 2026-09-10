package com.binitech.imports.adapters.inbound.messaging;

import com.binitech.imports.adapters.outbound.messaging.RabbitImportQueueAdapter.ImportJobMessage;
import com.binitech.imports.application.ports.inbound.ImportProcessorPort;
import com.binitech.imports.config.RabbitConfiguration;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductImportConsumer {
  private final ImportProcessorPort processor;

  public ProductImportConsumer(ImportProcessorPort processor) {
    this.processor = processor;
  }

  @RabbitListener(queues = RabbitConfiguration.QUEUE)
  public void consume(ImportJobMessage message) {
    processor.process(message.jobId());
  }
}
