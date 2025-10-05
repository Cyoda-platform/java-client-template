package com.java_template.application.processor;

import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: This processor validates a new Party entity during creation,
 * ensuring all required fields are present and business rules are met.
 */
@Component
public class ValidateNewParty implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateNewParty.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateNewParty(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Party> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Party> context) {

        EntityWithMetadata<Party> entityWithMetadata = context.entityResponse();
        Party party = entityWithMetadata.entity();

        logger.debug("Validating new party: {}", party.getPartyId());

        // Validate required fields
        if (party.getPartyId() == null || party.getPartyId().trim().isEmpty()) {
            throw new IllegalArgumentException("Party ID is required");
        }

        if (party.getLegalName() == null || party.getLegalName().trim().isEmpty()) {
            throw new IllegalArgumentException("Legal name is required");
        }

        if (party.getJurisdiction() == null || party.getJurisdiction().trim().isEmpty()) {
            throw new IllegalArgumentException("Jurisdiction is required");
        }

        // Validate LEI format if provided (20 character alphanumeric)
        if (party.getLei() != null && !party.getLei().trim().isEmpty()) {
            String lei = party.getLei().trim();
            if (lei.length() != 20 || !lei.matches("^[A-Z0-9]{20}$")) {
                throw new IllegalArgumentException("LEI must be 20 alphanumeric characters");
            }
        }

        // Validate jurisdiction format (ISO country code)
        String jurisdiction = party.getJurisdiction().trim().toUpperCase();
        if (jurisdiction.length() != 2 || !jurisdiction.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("Jurisdiction must be a 2-letter ISO country code");
        }
        party.setJurisdiction(jurisdiction);

        logger.info("Party {} validation completed successfully", party.getPartyId());

        return entityWithMetadata;
    }
}
