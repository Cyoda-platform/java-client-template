package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FetchCatFactProcessor - Handles fetching cat facts from the API
 * 
 * This processor is triggered when a CatFact entity transitions through the workflow.
 * It validates the fact data and updates timestamps.
 */
@Component
public class FetchCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchCatFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidCatFact, "Invalid CatFact")
                .map(ctx -> {
                    CatFact fact = ctx.entityResponse().entity();
                    // Ensure retrievedAt is set
                    if (fact.getRetrievedAt() == null) {
                        fact.setRetrievedAt(LocalDateTime.now());
                    }
                    logger.info("CatFact processed: {}", fact.getFactId());
                    return ctx.entityResponse();
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCatFact(com.java_template.common.dto.EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact fact = entityWithMetadata.entity();
        return fact != null && fact.isValid(entityWithMetadata.metadata());
    }
}

