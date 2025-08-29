package com.java_template.application.processor;

import com.java_template.application.entity.consent.version_1.Consent;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.audit.version_1.Audit;
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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SendVerificationEmail implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendVerificationEmail.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SendVerificationEmail(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Consent for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Consent.class)
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

    private boolean isValidEntity(Consent entity) {
        return entity != null && entity.isValid();
    }

    private Consent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Consent> context) {
        Consent consent = context.entity();
        if (consent == null) {
            logger.warn("Consent entity is null in context");
            return null;
        }

        // Generate verification token and attach it to consent.evidence_ref
        String token = UUID.randomUUID().toString();
        consent.setEvidence_ref(token);
        // Ensure status is at least pending_verification
        if (consent.getStatus() == null || consent.getStatus().isBlank()) {
            consent.setStatus("pending_verification");
        }

        // Attempt to resolve user to obtain email address for sending
        try {
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.userId", "EQUALS", consent.getUser_id())
            );
            CompletableFuture<List<DataPayload>> usersFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                User.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> dataPayloads = usersFuture.get();

            String email = null;
            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                DataPayload userPayload = dataPayloads.get(0);
                try {
                    User user = objectMapper.treeToValue(userPayload.getData(), User.class);
                    if (user != null) {
                        email = user.getEmail();
                    }
                } catch (Exception ex) {
                    logger.error("Failed to deserialize User payload for consent {}: {}", consent.getConsent_id(), ex.getMessage(), ex);
                }
            }

            // Simulate sending email (actual sending infrastructure not available here).
            if (email != null && !email.isBlank()) {
                logger.info("Sending verification email to {} for consent {} (token={})", email, consent.getConsent_id(), token);
            } else {
                logger.warn("No email found for user {}. Will still record verification token for consent {}", consent.getUser_id(), consent.getConsent_id());
            }

            // Append an audit record for this send attempt
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("send_verification_email");
            // ActorId: system initiating the send; use user id if available
            audit.setActorId(consent.getUser_id() != null ? consent.getUser_id() : "system");
            audit.setEntityRef((consent.getConsent_id() != null ? consent.getConsent_id() : "") + ":Consent");
            audit.setEvidenceRef(token);
            audit.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
            Map<String, Object> meta = (email != null) ? Map.of("email", email) : Map.of();
            audit.setMetadata(meta);

            CompletableFuture<UUID> auditFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                Audit.ENTITY_VERSION,
                audit
            );
            // Wait for audit persistence to complete; if it fails we log but do not fail the processor.
            try {
                auditFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to persist audit for consent {}: {}", consent.getConsent_id(), e.getMessage(), e);
            }

        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Error while resolving user for consent {}: {}", consent.getConsent_id(), ex.getMessage(), ex);
            // leave consent with evidence_ref and pending_verification; the processor is ASYNC_RETRY in the workflow,
            // so failures can be retried by the infrastructure if needed.
        } catch (Exception ex) {
            logger.error("Unexpected error in SendVerificationEmail for consent {}: {}", consent.getConsent_id(), ex.getMessage(), ex);
        }

        return consent;
    }
}