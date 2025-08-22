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
public class SuspendOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SuspendOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        try {
            String currentStatus = null;
            try {
                currentStatus = entity.getAccountStatus();
            } catch (Exception e) {
                // protect against missing getter implementations; treat as unknown
                logger.debug("Unable to read accountStatus for owner during suspend attempt", e);
            }

            // Idempotent: if already suspended, do nothing.
            if (currentStatus == null || !"suspended".equalsIgnoreCase(currentStatus)) {
                try {
                    entity.setAccountStatus("suspended");
                    logger.info("Owner suspended (id={}): previousStatus={}", safeGetId(entity), currentStatus);
                } catch (Exception e) {
                    logger.error("Failed to set accountStatus to suspended for owner id={}", safeGetId(entity), e);
                }
            } else {
                logger.info("SuspendOwnerProcessor called but owner already suspended (id={})", safeGetId(entity));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in SuspendOwnerProcessor for owner: {}", safeGetId(entity), ex);
        }

        return entity;
    }

    // helper to avoid NPEs when logging id if getter is unavailable
    private String safeGetId(Owner entity) {
        try {
            return entity.getId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}