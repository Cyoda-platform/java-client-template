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

import java.time.Instant;

@Component
public class DigestEmailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestEmailProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmail for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(DigestEmail.class)
                .validate(this::isValidEntity, "Invalid DigestEmail entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestEmailProcessor".equals(modelSpec.operationName()) &&
                "digestEmail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(DigestEmail entity) {
        // Use entity's own isValid method if available
        return entity.isValid();
    }

    // Business logic copied from functional requirement for DigestEmail processing
    private DigestEmail processEntityLogic(DigestEmail entity) {
        // The DigestEmail entity has fields: jobTechnicalId, emailContent, sentAt, deliveryStatus
        // Business logic: Attempt to send the email via configured email service
        // Update deliveryStatus based on outcome

        // Here we simulate sending email - in real scenario, this might involve calling an email service
        // For demonstration, we mark sentAt as now and deliveryStatus as SENT

        entity.setSentAt(Instant.now());
        entity.setDeliveryStatus("SENT");

        logger.info("DigestEmail sent for jobTechnicalId: {}", entity.getJobTechnicalId());

        return entity;
    }
}
