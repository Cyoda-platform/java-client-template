package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendEmailProcessor(SerializerFactory serializerFactory,
                              EntityService entityService,
                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CatFact.class)
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

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();
        if (entity == null) {
            logger.warn("CatFact entity is null in execution context");
            return null;
        }

        // Only send if validationStatus is VALID. If invoked by orchestration, this ensures we don't send invalid facts.
        String validationStatus = entity.getValidationStatus();
        if (validationStatus == null || !validationStatus.equalsIgnoreCase("VALID")) {
            logger.info("CatFact {} has validationStatus='{}' - skipping send", entity.getTechnicalId(), validationStatus);
            return entity;
        }

        try {
            // Build condition to fetch active subscribers
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );

            CompletableFuture<List<DataPayload>> subsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = subsFuture.get();
            List<Subscriber> subscribers = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Subscriber s = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                        if (s != null) {
                            subscribers.add(s);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to deserialize subscriber payload: {}", ex.getMessage(), ex);
                    }
                }
            }

            // Send email to each active subscriber (simulated by logging)
            int sentCount = 0;
            String factText = entity.getText() != null ? entity.getText() : "";
            for (Subscriber s : subscribers) {
                try {
                    String email = s.getEmail();
                    if (email == null || email.isBlank()) {
                        logger.warn("Skipping subscriber with missing email: {}", s);
                        continue;
                    }
                    // Simulate sending email
                    logger.info("Sending Weekly Cat Fact to {} <{}>", s.getName(), email);
                    logger.debug("Email subject='Weekly Cat Fact', body='{}'", factText);
                    // In a real implementation, integrate with an email provider here.
                    sentCount++;
                } catch (Exception ex) {
                    logger.error("Failed to send email to subscriber {}: {}", s, ex.getMessage(), ex);
                    // continue with other subscribers
                }
            }

            // Increment sendCount on the CatFact entity. The entity will be persisted by Cyoda automatically.
            Integer current = entity.getSendCount();
            if (current == null) current = 0;
            entity.setSendCount(current + (sentCount > 0 ? 1 : 0));

            logger.info("SendEmailProcessor completed for CatFact {} - attempted sends: {}, new sendCount: {}",
                entity.getTechnicalId(), sentCount, entity.getSendCount());

        } catch (Exception e) {
            logger.error("Error while processing SendEmailProcessor for CatFact {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            // Do not modify unrelated entities here. Return entity so state persists if needed.
        }

        return entity;
    }
}