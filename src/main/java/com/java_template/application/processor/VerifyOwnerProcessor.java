package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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

@Component
public class VerifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
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
        Owner entity = context.entity();

        // Business logic:
        // If contact info is valid -> set verificationStatus = "VERIFIED"
        // Otherwise set verificationStatus = "PENDING_VERIFICATION"
        try {
            if (entity.getContactInfo() != null && entity.getContactInfo().isValid()) {
                logger.info("Owner {} contact valid - setting status to VERIFIED", entity.getId());
                entity.setVerificationStatus("VERIFIED");
            } else {
                logger.info("Owner {} contact invalid or incomplete - setting status to PENDING_VERIFICATION", entity.getId());
                entity.setVerificationStatus("PENDING_VERIFICATION");
            }
        } catch (Exception ex) {
            // In case of unexpected errors, mark as pending verification to avoid accidental verification
            logger.warn("Error while verifying owner {}: {}. Setting status to PENDING_VERIFICATION", entity.getId(), ex.getMessage());
            entity.setVerificationStatus("PENDING_VERIFICATION");
        }

        return entity;
    }
}