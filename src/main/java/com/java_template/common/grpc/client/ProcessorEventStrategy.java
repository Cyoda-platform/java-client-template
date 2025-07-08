package com.java_template.common.grpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.ModelKey;
import com.java_template.common.workflow.ProcessorFactory;
import org.cyoda.cloud.api.event.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.EntityProcessorCalculationResponse;
import io.cloudevents.v1.proto.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy for handling EntityProcessorCalculationRequest events.
 * This strategy parses the CloudEvent, finds the appropriate CyodaProcessor,
 * and delegates directly to the processor. The processor handles its own
 * request/response conversion using serializers as needed.
 */
@Component
public class ProcessorEventStrategy implements EventHandlingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorEventStrategy.class);
    private static final String CALC_REQ_EVENT_TYPE = "EntityProcessorCalculationRequest";

    private final ProcessorFactory processorFactory;
    private final ObjectMapper objectMapper;

    public ProcessorEventStrategy(ProcessorFactory processorFactory, ObjectMapper objectMapper) {
        this.processorFactory = processorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Object> handleEvent(CloudEvent cloudEvent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("[IN] Received event {}: \n{}", CALC_REQ_EVENT_TYPE, cloudEvent.getTextData());

                // Parse the request
                EntityProcessorCalculationRequest request = objectMapper.readValue(cloudEvent.getTextData(), EntityProcessorCalculationRequest.class);
                String processorName = request.getProcessorName();

                logger.info("Processing {}: {}", CALC_REQ_EVENT_TYPE, processorName);

                // Extract ModelKey from payload for processor selection
                ObjectNode payloadData = (ObjectNode) request.getPayload().getData();
                ModelKey modelKey = ModelKey.extractFromPayload(payloadData);

                // Get processor that supports this ModelKey
                CyodaProcessor processor = processorFactory.getProcessorForModel(processorName, modelKey);
                if (processor == null) {
                    throw new IllegalArgumentException("No processor found for name '" + processorName + "' that supports ModelKey " + modelKey);
                }

                // Delegate directly to processor - it handles its own serialization
                CompletableFuture<EntityProcessorCalculationResponse> futureResponse = processor.process(request);
                try {
                    return futureResponse.get(); // Wait for completion
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Processing was interrupted", e);
                } catch (Exception e) {
                    throw new RuntimeException("Error during processing", e);
                }

            } catch (IOException e) {
                logger.error("Error handling processor event: {}", cloudEvent, e);

                // Create error response
                try {
                    EntityProcessorCalculationResponse errorResponse = objectMapper.readValue(cloudEvent.getTextData(), EntityProcessorCalculationResponse.class);
                    errorResponse.setSuccess(false);
                    return errorResponse;
                } catch (Exception ex) {
                    logger.error("Error creating error response", ex);
                    return null;
                }
            }
        });
    }

    @Override
    public boolean supports(String eventType) {
        return CALC_REQ_EVENT_TYPE.equals(eventType);
    }

    @Override
    public String getStrategyName() {
        return "ProcessorEventStrategy";
    }

}
