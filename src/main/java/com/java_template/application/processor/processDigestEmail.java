package com.java_template.application.processor;

import com.java_template.application.entity.DigestEmail;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class processDigestEmail implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public processDigestEmail(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("processDigestEmail initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmail for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(DigestEmail.class)
            .validate(DigestEmail::isValid, "Invalid DigestEmail entity")
            .map(this::processDigestEmailLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "processDigestEmail".equals(modelSpec.operationName()) &&
               "digestEmail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestEmail processDigestEmailLogic(DigestEmail entity) {
        // Business logic: simulate sending email, update status to SENT or FAILED
        try {
            // Simulate sending email (replace with real email sending logic if available)
            logger.info("Sending email for DigestEmail with requestId: {}", entity.getDigestRequestId());

            // Simulated send success
            boolean sendSuccess = true;

            if (sendSuccess) {
                entity.setStatus("SENT");
            } else {
                entity.setStatus("FAILED");
            }

        } catch (Exception e) {
            logger.error("Error sending DigestEmail: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}
