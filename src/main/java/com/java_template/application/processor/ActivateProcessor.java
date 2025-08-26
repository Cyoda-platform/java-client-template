package com.java_template.application.processor;

import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
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
public class ActivateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchFilter for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchFilter.class)
            .validate(this::isValidEntity, "Invalid SearchFilter state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchFilter entity) {
        return entity != null && entity.isValid();
    }

    private SearchFilter processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchFilter> context) {
        SearchFilter entity = context.entity();

        // If already active, nothing to change; otherwise set active.
        if (Boolean.TRUE.equals(entity.getIsActive())) {
            logger.info("SearchFilter {} is already active.", entity.getId());
            return entity;
        }

        // Activate the search filter so it can be used for scheduling triggers/alerts.
        entity.setIsActive(Boolean.TRUE);
        logger.info("Activated SearchFilter {} (set isActive=true).", entity.getId());

        // No external updates to this entity should be performed here.
        // If scheduling or creating other entities is required, that should be done via injected services
        // and not by updating this triggering entity directly.

        return entity;
    }
}