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
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SendMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Business defaults
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

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

        // Set sending state and updatedAt
        try {
            entity.setState(sendingState);
        } catch (Exception e) {
            logger.debug("Unable to set state on entity: {}", e.getMessage());
        }
        try {
            entity.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.debug("Unable to set updatedAt on entity: {}", e.getMessage());
        }

        // Ensure deliveryStatus structure
        if (entity.getDeliveryStatus() == null) {
            Map<String, Object> ds = new HashMap<>();
            ds.put("attempts", 0);
            ds.put("status", "PENDING");
            ds.put("lastAttempt", null);
            ds.put("lastError", null);
            ds.put("perRecipient", new HashMap<String, String>());
            entity.setDeliveryStatus(ds);
        }

        // Normalize perRecipient map
        Map<String, String> perRecipient = null;
        try {
            Object pr = entity.getDeliveryStatus().get("perRecipient");
            if (pr instanceof Map) {
                //noinspection unchecked
                perRecipient = (Map<String, String>) pr;
            }
        } catch (Exception ignored) {
        }
        if (perRecipient == null) {
            perRecipient = new HashMap<>();
            entity.getDeliveryStatus().put("perRecipient", perRecipient);
        }

        int prevAttempts = 0;
        try {
            Object prev = entity.getDeliveryStatus().get("attempts");
            prevAttempts = prev instanceof Integer ? (Integer) prev : ((Number) prev).intValue();
        } catch (Exception ignored) {
        }

        int currentAttempt = prevAttempts + 1;

        List<String> mailList = entity.getMailList();
        List<String> failedRecipients = new ArrayList<>();
        List<String> succeededRecipients = new ArrayList<>();

        // Simulate send per-recipient with idempotency: skip already SENT recipients
        for (String recipient : mailList) {
            try {
                String existing = perRecipient.get(recipient);
                if ("SENT".equalsIgnoreCase(existing)) {
                    logger.debug("Skipping already-sent recipient {} for mail {}", recipient, entity.getTechnicalId());
                    succeededRecipients.add(recipient);
                    continue;
                }

                // Perform simple validation before sending
                boolean valid = recipient != null && recipient.contains("@") && recipient.indexOf(' ') == -1;
                if (!valid) {
                    failedRecipients.add(recipient);
                    perRecipient.put(recipient, "FAILED_INVALID_ADDRESS");
                    logger.warn("Invalid recipient address {} for mail {}", recipient, entity.getTechnicalId());
                    continue;
                }

                // Simulate sending: treat fail@example.com as a transient failure
                boolean sendSuccess = !"fail@example.com".equalsIgnoreCase(recipient);

                if (sendSuccess) {
                    perRecipient.put(recipient, "SENT");
                    succeededRecipients.add(recipient);
                    logger.debug("Sent mail {} to {}", entity.getTechnicalId(), recipient);
                } else {
                    perRecipient.put(recipient, "FAILED");
                    failedRecipients.add(recipient);
                    logger.debug("Simulated failure sending mail {} to {}", entity.getTechnicalId(), recipient);
                }

            } catch (Exception e) {
                logger.error("Error sending to recipient {} for mail {}: {}", recipient, entity.getTechnicalId(), e.getMessage());
                failedRecipients.add(recipient);
                try {
                    perRecipient.put(recipient, "FAILED_EXCEPTION");
                } catch (Exception ignored) {
                }
            }
        }

        // Update deliveryStatus fields
        try {
            entity.getDeliveryStatus().put("attempts", currentAttempt);
            entity.getDeliveryStatus().put("lastAttempt", Instant.now().toString());

            if (failedRecipients.isEmpty()) {
                entity.getDeliveryStatus().put("status", "SENT");
                entity.getDeliveryStatus().put("lastError", null);
                try {
                    entity.setState("SENT");
                } catch (Exception e) {
                    logger.debug("Unable to set state to SENT on entity: {}", e.getMessage());
                }
                logger.info("Mail {} sent successfully to {} recipients", entity.getTechnicalId(), succeededRecipients.size());
            } else {
                entity.getDeliveryStatus().put("status", "FAILED");
                String lastError = failedRecipients.stream().collect(Collectors.joining(","));
                entity.getDeliveryStatus().put("lastError", lastError);

                // Determine if we've exhausted attempts (per policy)
                int maxAttempts = DEFAULT_MAX_ATTEMPTS;
                if (currentAttempt >= maxAttempts) {
                    // Do not set PERMANENTLY_FAILED here; workflow will transition based on attempts. Keep FAILED state.
                    logger.info("Mail {} reached max attempts ({}). Marking as FAILED with permanent flag in deliveryStatus.", entity.getTechnicalId(), currentAttempt);
                    entity.getDeliveryStatus().put("permanent", true);
                }

                try {
                    entity.setState("FAILED");
                } catch (Exception e) {
                    logger.debug("Unable to set state to FAILED on entity: {}", e.getMessage());
                }

                logger.info("Mail {} failed to send to {} recipients on attempt {}", entity.getTechnicalId(), failedRecipients.size(), currentAttempt);
            }

            // Persist perRecipient map
            entity.getDeliveryStatus().put("perRecipient", perRecipient);

        } catch (Exception e) {
            logger.error("Unexpected error while updating deliveryStatus for mail {}: {}", entity.getTechnicalId(), e.getMessage());
        }

        try {
            entity.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.debug("Unable to set updatedAt on entity: {}", e.getMessage());
        }

        // Metrics/logging
        logger.info("SendMailProcessor completed for mail {}: attempts={}, status={}", entity.getTechnicalId(), entity.getDeliveryStatus().get("attempts"), entity.getDeliveryStatus().get("status"));

        return entity;
    }
}
