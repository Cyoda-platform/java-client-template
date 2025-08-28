package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Component
public class NotifyVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyVerificationProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner owner = context.entity();

        try {
            String vStatus = owner.getVerificationStatus();
            if (vStatus != null) {
                String status = vStatus.trim().toLowerCase();

                // When verified: ensure role is set and notify owner via notification-service (if contactEmail present)
                if ("verified".equals(status)) {
                    if (owner.getRole() == null || owner.getRole().isBlank()) {
                        owner.setRole("user");
                        logger.info("Set default role 'user' for ownerId={}", owner.getOwnerId());
                    }

                    String email = owner.getContactEmail();
                    if (email != null && !email.isBlank()) {
                        try {
                            Map<String, String> payload = Map.of(
                                "to", email,
                                "subject", "Your account has been verified",
                                "body", "Hello " + (owner.getName() != null ? owner.getName() : "") +
                                        ", your account verification is complete. Thank you!"
                            );
                            String json = objectMapper.writeValueAsString(payload);

                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://notification-service/send"))
                                    .timeout(Duration.ofSeconds(5))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(json))
                                    .build();

                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            logger.info("Notification sent to {} statusCode={}", email, resp.statusCode());
                        } catch (Exception ex) {
                            // Do not fail processing on notification errors; log for investigation
                            logger.error("Failed to send verification notification to {}: {}", owner.getContactEmail(), ex.getMessage(), ex);
                        }
                    } else {
                        logger.info("No contactEmail present for ownerId={}, skipping notification", owner.getOwnerId());
                    }
                } else if ("pending".equals(status)) {
                    // If pending, optionally send a pending notification reminder if email exists
                    String email = owner.getContactEmail();
                    if (email != null && !email.isBlank()) {
                        try {
                            Map<String, String> payload = Map.of(
                                "to", email,
                                "subject", "Verification in progress",
                                "body", "Hello " + (owner.getName() != null ? owner.getName() : "") +
                                        ", your account verification is currently pending. We will notify you when it is complete."
                            );
                            String json = objectMapper.writeValueAsString(payload);

                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://notification-service/send"))
                                    .timeout(Duration.ofSeconds(5))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(json))
                                    .build();

                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            logger.info("Pending notification sent to {} statusCode={}", email, resp.statusCode());
                        } catch (Exception ex) {
                            logger.error("Failed to send pending notification to {}: {}", owner.getContactEmail(), ex.getMessage(), ex);
                        }
                    }
                } else if ("unverified".equals(status) || "rejected".equals(status)) {
                    // Notify about rejection or unverified status if contact exists
                    String email = owner.getContactEmail();
                    if (email != null && !email.isBlank()) {
                        try {
                            Map<String, String> payload = Map.of(
                                "to", email,
                                "subject", "Verification status: " + vStatus,
                                "body", "Hello " + (owner.getName() != null ? owner.getName() : "") +
                                        ", your account verification status is: " + vStatus + ". Please contact support for assistance."
                            );
                            String json = objectMapper.writeValueAsString(payload);

                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://notification-service/send"))
                                    .timeout(Duration.ofSeconds(5))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(json))
                                    .build();

                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            logger.info("Unverified/rejected notification sent to {} statusCode={}", email, resp.statusCode());
                        } catch (Exception ex) {
                            logger.error("Failed to send unverified/rejected notification to {}: {}", owner.getContactEmail(), ex.getMessage(), ex);
                        }
                    }
                } else {
                    logger.debug("No notification rule for verificationStatus='{}' for ownerId={}", vStatus, owner.getOwnerId());
                }
            } else {
                logger.debug("Owner verificationStatus is null for ownerId={}", owner.getOwnerId());
            }
        } catch (Exception e) {
            logger.error("Error while processing NotifyVerificationProcessor for ownerId={}: {}", owner != null ? owner.getOwnerId() : "unknown", e.getMessage(), e);
            // do not throw to allow workflow to continue; returning entity as-is
        }

        return owner;
    }
}