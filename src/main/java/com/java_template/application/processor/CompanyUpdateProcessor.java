package com.java_template.application.processor;

import com.java_template.application.entity.company.version_1.Company;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: This processor handles company update operations, setting timestamps
 * and performing validation when companies are updated in the CRM system.
 */
@Component
public class CompanyUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompanyUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompanyUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Company.class)
                .validate(this::isValidEntityWithMetadata, "Invalid company entity wrapper")
                .map(this::processCompanyUpdateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Company
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Company> entityWithMetadata) {
        Company company = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return company != null && company.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for company update processing
     */
    private EntityWithMetadata<Company> processCompanyUpdateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Company> context) {

        EntityWithMetadata<Company> entityWithMetadata = context.entityResponse();
        Company company = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing company update: {} in state: {}", company.getCompanyId(), currentState);

        // Update timestamp
        company.setUpdatedAt(LocalDateTime.now());

        // Set created timestamp if not already set
        if (company.getCreatedAt() == null) {
            company.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Company {} updated successfully", company.getCompanyId());

        return entityWithMetadata;
    }
}
