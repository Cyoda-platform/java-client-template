package com.java_template.application.processor;

import com.java_template.application.entity.LEIEnrichmentRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

@Component
public class LEIEnrichmentRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public LEIEnrichmentRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("LEIEnrichmentRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LEIEnrichmentRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(LEIEnrichmentRequest.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processLEIEnrichmentRequestLogic)
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
        if (entity.getLei() == null) return false;
        return true;
    }

    private LEIEnrichmentRequest processLEIEnrichmentRequestLogic(LEIEnrichmentRequest entity) {
        logger.info("Processing LEIEnrichmentRequest entity for businessId: {}", entity.getBusinessId());

        try {
            String lei = fetchLEIForBusinessId(entity.getBusinessId());

            if (lei == null || lei.isBlank()) {
                entity.setLei("Not Available");
            } else {
                entity.setLei(lei);
                entity.setLeiSource("GLEIF");
            }
            entity.setStatus("COMPLETED");

            logger.info("Completed LEI enrichment for businessId: {}", entity.getBusinessId());
        } catch (Exception e) {
            logger.error("Error processing LEIEnrichmentRequest for businessId: {}", entity.getBusinessId(), e);
            entity.setStatus("FAILED");
        }

        return entity;
    }

    private String fetchLEIForBusinessId(String businessId) {
        logger.info("Simulating LEI API call for businessId: {}", businessId);

        if ("1234567-8".equals(businessId)) {
            return "5493001KJTIIGC8Y1R12";
        }
        return null;
    }
}
