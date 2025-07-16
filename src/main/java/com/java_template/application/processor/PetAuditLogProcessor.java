package com.java_template.application.processor;

import com.java_template.application.entity.PetAudit;
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

@Component
public class PetAuditLogProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetAuditLogProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetAuditLogProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetAudit for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetAudit.class)
                .withErrorHandler(this::handlePetAuditError)
                .validate(this::isValidPetAudit, "Invalid pet audit state")
                .map(this::applyAuditLog)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAuditLogProcessor".equals(modelSpec.operationName()) &&
               "petAudit".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPetAudit(PetAudit petAudit) {
        // Simple validation example
        return petAudit != null && petAudit.getAuditDetails() != null && !petAudit.getAuditDetails().isEmpty();
    }

    private PetAudit applyAuditLog(PetAudit petAudit) {
        // Example transformation or initial setup
        petAudit.setAuditStatus("LOGGED");
        return petAudit;
    }

    private boolean businessValidation(PetAudit petAudit) {
        // Custom business logic validation example
        return petAudit.getAuditStatus().equals("LOGGED");
    }

    private ErrorInfo handlePetAuditError(Throwable t, PetAudit petAudit) {
        logger.error("Error processing PetAudit entity", t);
        return new ErrorInfo("PetAuditProcessingError", t.getMessage());
    }
}
