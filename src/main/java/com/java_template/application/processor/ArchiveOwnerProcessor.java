package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArchiveOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveOwnerProcessor(SerializerFactory serializerFactory) {
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

        // Business logic for archiving an Owner:
        // - If already archived, do nothing.
        // - Otherwise mark verificationStatus as "ARCHIVED".
        // - Add an "Archived" profile badge if not already present for traceability.
        String currentStatus = entity.getVerificationStatus();
        if (currentStatus != null && currentStatus.equalsIgnoreCase("ARCHIVED")) {
            logger.info("Owner {} is already archived.", entity.getId());
            return entity;
        }

        entity.setVerificationStatus("ARCHIVED");

        List<String> badges = entity.getProfileBadges();
        if (badges == null) {
            badges = new ArrayList<>();
            badges.add("Archived");
            entity.setProfileBadges(badges);
        } else {
            boolean hasArchived = false;
            for (String b : badges) {
                if (b != null && b.equalsIgnoreCase("Archived")) {
                    hasArchived = true;
                    break;
                }
            }
            if (!hasArchived) {
                badges.add("Archived");
            }
        }

        logger.info("Owner {} archived successfully.", entity.getId());
        return entity;
    }
}