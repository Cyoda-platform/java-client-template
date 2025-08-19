package com.java_template.application.processor;

import com.java_template.application.entity.delivery.version_1.Delivery;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final int DEFAULT_MAX_RETRIES = 2;

    public SendEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Delivery.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Delivery entity) {
        return entity != null && entity.isValid();
    }

    private Delivery processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Delivery> context) {
        Delivery delivery = context.entity();
        try {
            // increment attempts
            int attempts = delivery.getAttempts() != null ? delivery.getAttempts() : 0;
            attempts += 1;
            delivery.setAttempts(attempts);

            // Simulate sending: success for even attempts, failure for odd attempts (prototype behaviour)
            boolean success = (attempts % 2) == 0;

            if (success) {
                delivery.setStatus("SENT");
                delivery.setSentAt(Instant.now().toString());
                logger.info("Delivery {} sent successfully on attempt {}", delivery.getId(), attempts);
                // In a real implementation we'd create an OutboundEvent here
            } else {
                Map<String, Object> rp = delivery.getRetriesPolicy();
                int maxRetries = DEFAULT_MAX_RETRIES;
                if (rp != null && rp.get("maxRetries") instanceof Number) {
                    maxRetries = ((Number) rp.get("maxRetries")).intValue();
                }
                int maxAttempts = 1 + maxRetries;
                if (attempts < maxAttempts) {
                    // schedule retry (for prototype just log)
                    logger.info("Delivery {} failed on attempt {} — scheduling retry", delivery.getId(), attempts);
                } else {
                    delivery.setStatus("FAILED");
                    delivery.setLastError(Map.of("code", "SEND_FAILED", "message", "Delivery attempts exhausted"));
                    logger.info("Delivery {} marked FAILED after {} attempts", delivery.getId(), attempts);
                }
            }
        } catch (Exception ex) {
            logger.error("Error processing Delivery {}: {}", delivery.getId(), ex.getMessage(), ex);
        }
        return delivery;
    }
}
