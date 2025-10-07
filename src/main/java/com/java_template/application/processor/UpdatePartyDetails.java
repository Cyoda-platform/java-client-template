package com.java_template.application.processor;

import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
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

/**
 * ABOUTME: This processor handles updates to Party entity details,
 * performing validation and business rule enforcement.
 */
@Component
public class UpdatePartyDetails implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePartyDetails.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdatePartyDetails(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Party.class)
                .validate(this::isValidEntityWithMetadata, "Invalid party entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Party> entityWithMetadata) {
        Party entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Party> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Party> context) {

        EntityWithMetadata<Party> entityWithMetadata = context.entityResponse();
        Party party = entityWithMetadata.entity();

        logger.debug("Updating party details: {}", party.getPartyId());

        // Validate LEI format if provided (20 character alphanumeric)
        if (party.getLei() != null && !party.getLei().trim().isEmpty()) {
            String lei = party.getLei().trim();
            if (lei.length() != 20 || !lei.matches("^[A-Z0-9]{20}$")) {
                throw new IllegalArgumentException("LEI must be 20 alphanumeric characters");
            }
        }

        // Validate jurisdiction format (ISO country code)
        if (party.getJurisdiction() != null && !party.getJurisdiction().trim().isEmpty()) {
            String jurisdiction = party.getJurisdiction().trim().toUpperCase();
            if (jurisdiction.length() != 2 || !jurisdiction.matches("^[A-Z]{2}$")) {
                throw new IllegalArgumentException("Jurisdiction must be a 2-letter ISO country code");
            }
            party.setJurisdiction(jurisdiction);
        }

        // Validate contact email format if provided
        if (party.getContact() != null && party.getContact().getEmail() != null) {
            String email = party.getContact().getEmail().trim();
            if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException("Invalid email format");
            }
        }

        logger.info("Party {} details updated successfully", party.getPartyId());

        return entityWithMetadata;
    }
}
