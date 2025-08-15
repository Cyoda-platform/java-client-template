package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SendMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendMailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendMail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid entity state for sending")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.getMailList() != null && !entity.getMailList().isEmpty();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail entity = context.entity();

        // Determine which template to use based on isHappy
        boolean isHappy = Boolean.TRUE.equals(entity.getIsHappy());
        String sendingState = isHappy ? "SENDING_HAPPY" : "SENDING_GLOOMY";

        // Idempotency guard: if already SENT, do nothing
        if ("SENT".equals(entity.getState())) {
            logger.info("Mail {} already SENT, skipping send", entity.getTechnicalId());
            return entity;
        }

        // Mark sending state and persist logically
        entity.setState(sendingState);
        entity.setUpdatedAt(Instant.now().toString());

        // Prepare delivery status structure
        if (entity.getDeliveryStatus() == null) {
            Map<String, Object> ds = new HashMap<>();
            ds.put("attempts", 0);
            ds.put("status", "PENDING");
            ds.put("lastAttempt", null);
            ds.put("lastError", null);
            entity.setDeliveryStatus(ds);
        }

        try {
            List<String> mailList = entity.getMailList();
            List<String> perRecipientResults = new ArrayList<>();
            boolean overallSuccess = true;

            for (String recipient : mailList) {
                // Idempotency: if deliveryStatus contains a marker for recipient success, skip
                Object attemptsObj = entity.getDeliveryStatus().get("attempts");
                int attempts = attemptsObj instanceof Integer ? (Integer) attemptsObj : ((Number) attemptsObj).intValue();

                // Simple send simulation: succeed if recipient contains "@" and not equal to "fail@example.com"
                boolean success = recipient != null && recipient.contains("@") && !recipient.equalsIgnoreCase("fail@example.com");
                if (success) {
                    perRecipientResults.add(recipient + ":SENT");
                } else {
                    perRecipientResults.add(recipient + ":FAILED");
                    overallSuccess = false;
                }
            }

            // Update deliveryStatus
            int prevAttempts = entity.getDeliveryStatus().get("attempts") instanceof Integer ? (Integer) entity.getDeliveryStatus().get("attempts") : ((Number) entity.getDeliveryStatus().get("attempts")).intValue();
            int newAttempts = prevAttempts + 1;
            entity.getDeliveryStatus().put("attempts", newAttempts);
            entity.getDeliveryStatus().put("lastAttempt", Instant.now().toString());
            if (overallSuccess) {
                entity.getDeliveryStatus().put("status", "SENT");
                entity.getDeliveryStatus().put("lastError", null);
                entity.setState("SENT");
                logger.info("Mail {} sent successfully to {} recipients", entity.getTechnicalId(), mailList.size());
            } else {
                entity.getDeliveryStatus().put("status", "FAILED");
                entity.getDeliveryStatus().put("lastError", "DELIVERY_FAIL");
                entity.setState("FAILED");
                logger.info("Mail {} failed to send to some recipients", entity.getTechnicalId());
            }

            entity.getDeliveryStatus().put("perRecipient", perRecipientResults);

        } catch (Exception e) {
            logger.error("Unexpected error during send for mail {}", entity.getTechnicalId(), e);
            entity.getDeliveryStatus().put("status", "FAILED");
            entity.getDeliveryStatus().put("lastError", e.getMessage());
            entity.getDeliveryStatus().put("lastAttempt", Instant.now().toString());
            int prevAttempts = entity.getDeliveryStatus().get("attempts") instanceof Integer ? (Integer) entity.getDeliveryStatus().get("attempts") : ((Number) entity.getDeliveryStatus().get("attempts")).intValue();
            entity.getDeliveryStatus().put("attempts", prevAttempts + 1);
            entity.setState("FAILED");
        }

        entity.setUpdatedAt(Instant.now().toString());
        return entity;
    }
}
