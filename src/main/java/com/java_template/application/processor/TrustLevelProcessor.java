package com.java_template.application.processor;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TrustLevelProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TrustLevelProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TrustLevelProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
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

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        try {
            // Only consider promotion if user is currently ACTIVE (case-insensitive)
            if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("Active")) {
                logger.debug("User {} status is not Active (status={}), skipping trust evaluation.", user.getUserId(), user.getStatus());
                return user;
            }

            boolean identityVerified = false;
            boolean goodPastBehavior = false;

            // IdentityVerifiedCriterion:
            // - Basic check: registeredAt is older than 180 days (6 months)
            // - registeredAt must be a valid ISO-8601 timestamp
            String registeredAt = user.getRegisteredAt();
            if (registeredAt != null && !registeredAt.isBlank()) {
                try {
                    OffsetDateTime reg = OffsetDateTime.parse(registeredAt);
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    long daysBetween = ChronoUnit.DAYS.between(reg, now);
                    if (daysBetween >= 180) {
                        identityVerified = true;
                    } else {
                        logger.debug("User {} registered {} days ago, needs >=180 days for identity criterion.", user.getUserId(), daysBetween);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse registeredAt for user {}: {}", user.getUserId(), e.getMessage());
                }
            } else {
                logger.debug("User {} has no registeredAt, identity verification criterion failed.", user.getUserId());
            }

            // PastBehaviorCriterion:
            // - User must have at least one adopted pet recorded
            List<String> adopted = user.getAdoptedPetIds();
            if (adopted != null && !adopted.isEmpty()) {
                // ensure entries are non-blank
                long validCount = adopted.stream().filter(id -> id != null && !id.isBlank()).count();
                if (validCount >= 1) {
                    goodPastBehavior = true;
                } else {
                    logger.debug("User {} adoptedPetIds present but contain no valid ids.", user.getUserId());
                }
            } else {
                logger.debug("User {} has no adoptedPetIds.", user.getUserId());
            }

            // If both criteria satisfied, elevate to Trusted
            if (identityVerified && goodPastBehavior) {
                logger.info("Promoting user {} to Trusted (identityVerified={}, goodPastBehavior={})", user.getUserId(), identityVerified, goodPastBehavior);
                user.setStatus("Trusted");
            } else {
                logger.debug("User {} does not meet trust criteria (identityVerified={}, goodPastBehavior={})", user.getUserId(), identityVerified, goodPastBehavior);
            }

        } catch (Exception ex) {
            logger.error("Error while evaluating trust level for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            // Don't throw; return entity unchanged so workflow can handle errors via serializer
        }

        return user;
    }
}