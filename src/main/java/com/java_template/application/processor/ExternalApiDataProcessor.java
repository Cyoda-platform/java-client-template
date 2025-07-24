package com.java_template.application.processor;

import com.java_template.application.entity.ExternalApiData;
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
public class ExternalApiDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public ExternalApiDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ExternalApiDataProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExternalApiData for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ExternalApiData.class)
                .validate(this::isValidEntity, "Invalid ExternalApiData state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ExternalApiDataProcessor".equals(modelSpec.operationName()) &&
                "externalApiData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(ExternalApiData entity) {
        return entity.isValid();
    }

    private ExternalApiData processEntityLogic(ExternalApiData entity) {
        // Business logic for ExternalApiData processing
        // Validate responseData format (simplified example)
        if (entity.getResponseData() != null && !entity.getResponseData().isBlank()) {
            // Simulate transformation/enrichment
            String enrichedData = entity.getResponseData().trim();
            entity.setResponseData(enrichedData);
            entity.setFetchedAt(Instant.now());
        } else {
            logger.warn("Response data is empty or null for jobTechnicalId: {}", entity.getJobTechnicalId());
        }
        return entity;
    }
}
