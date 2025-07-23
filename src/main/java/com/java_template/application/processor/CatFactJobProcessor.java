package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.CatFactJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class CatFactJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public CatFactJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("CatFactJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFactJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CatFactJob.class)
                .validate(CatFactJob::isValid, "Invalid CatFactJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CatFactJobProcessor".equals(modelSpec.operationName()) &&
                "catfactjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private CatFactJob processEntityLogic(CatFactJob catFactJob) {
        logger.info("Processing CatFactJob with technicalId: {}", catFactJob.getTechnicalId());

        try {
            // Simulate API call - replace with actual HTTP call in real implementation
            String fetchedFact = "Cats sleep 70% of their lives."; // placeholder fact
            catFactJob.setCatFactText(fetchedFact);
            catFactJob.setStatus("PROCESSING");
            logger.info("Fetched cat fact: {}", fetchedFact);

            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.status", "IEQUALS", "ACTIVE"));
                CompletableFuture<ArrayNode> activeSubsFuture = entityService.getItemsByCondition(
                        "Subscriber",
                        Config.ENTITY_VERSION,
                        condition,
                        true
                );
                ArrayNode activeSubscribers = activeSubsFuture.get();

                for (JsonNode node : activeSubscribers) {
                    String email = node.get("email") != null ? node.get("email").asText() : null;
                    if (email != null) {
                        logger.info("Sending cat fact email to subscriber: {}", email);
                    }
                }
            } catch (Exception e) {
                logger.error("Error sending emails to subscribers: {}", e.getMessage());
            }

            catFactJob.setStatus("COMPLETED");
            logger.info("CatFactJob {} completed successfully", catFactJob.getTechnicalId());
        } catch (Exception e) {
            catFactJob.setStatus("FAILED");
            logger.error("Failed to process CatFactJob {}: {}", catFactJob.getTechnicalId(), e.getMessage());
        }

        return catFactJob;
    }
}
