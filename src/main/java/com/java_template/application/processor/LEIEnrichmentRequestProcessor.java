package com.java_template.application.processor;

import com.java_template.application.entity.LEIEnrichmentRequest;
import com.java_template.application.entity.Company;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class LEIEnrichmentRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LEIEnrichmentRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("LEIEnrichmentRequestProcessor initialized with SerializerFactory, EntityService and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LEIEnrichmentRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(LEIEnrichmentRequest.class)
                .validate(this::isValidEntity, "Invalid LEIEnrichmentRequest entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LEIEnrichmentRequestProcessor".equals(modelSpec.operationName()) &&
                "leienrichmentrequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(LEIEnrichmentRequest entity) {
        if (entity.getBusinessId() == null || entity.getBusinessId().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        return true;
    }

    private LEIEnrichmentRequest processEntityLogic(LEIEnrichmentRequest entity) {
        // Simulate LEI enrichment lookup by businessId
        logger.info("Enriching LEI for businessId: {}", entity.getBusinessId());

        // For demonstration, assume LEI found if businessId ends with '8', else not available
        String lei = entity.getBusinessId().endsWith("8") ? "5493001KJTIIGC8Y1R12" : "Not Available";
        entity.setLei(lei);

        // Update status accordingly
        entity.setStatus("COMPLETED");

        // In a real scenario, persist enriched Company entity here via entityService.addItem if needed
        // But do not update the current LEIEnrichmentRequest entity via entityService

        return entity;
    }
}
