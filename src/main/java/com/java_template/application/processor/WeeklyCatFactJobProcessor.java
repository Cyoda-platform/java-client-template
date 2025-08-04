package com.java_template.application.processor;

import com.java_template.application.entity.WeeklyCatFactJob;
import com.java_template.application.entity.CatFact;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WeeklyCatFactJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public WeeklyCatFactJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyCatFactJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeeklyCatFactJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyCatFactJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyCatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyCatFactJob> context) {
        WeeklyCatFactJob job = context.entity();
        UUID technicalId = context.request().getEntityId();

        logger.info("Processing WeeklyCatFactJob id {}", technicalId);
        try {
            // Step 2: Data Ingestion - call external Cat Fact API
            var restTemplate = new org.springframework.web.client.RestTemplate();
            var response = restTemplate.getForObject("https://catfact.ninja/fact", java.util.Map.class);

            if (response == null || !response.containsKey("fact")) {
                throw new RuntimeException("Failed to retrieve cat fact from API");
            }

            String fact = (String) response.get("fact");

            // Create and save CatFact entity
            CatFact catFact = new CatFact();
            catFact.setFact(fact);
            catFact.setRetrievedDate(LocalDateTime.now());

            CompletableFuture<UUID> catFactIdFuture = entityService.addItem(
                    CatFact.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    catFact
            );
            UUID catFactId = catFactIdFuture.get();

            logger.info("Retrieved and saved CatFact id {}: {}", catFactId, fact);

            // Count active subscribers
            Condition condition = Condition.of("$.status", "EQUALS", "ACTIVE");
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", condition);
            CompletableFuture<ArrayNode> activeSubscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    searchRequest,
                    true
            );
            ArrayNode activeSubscribersNodes = activeSubscribersFuture.get();

            int activeSubscribers = activeSubscribersNodes == null ? 0 : activeSubscribersNodes.size();

            // Compose email content and send emails (simulate)
            logger.info("Sending cat fact email to {} active subscribers", activeSubscribers);
            // Email sending logic simulated by logging

            // Update job status and details
            job.setCatFact(fact);
            job.setSubscriberCount(activeSubscribers);
            job.setEmailSentDate(LocalDateTime.now());
            job.setStatus("COMPLETED");

            // Skipping update of WeeklyCatFactJob entity as updates are not supported

            logger.info("WeeklyCatFactJob id {} completed successfully", technicalId);
        } catch (Exception e) {
            job.setStatus("FAILED");
            // Skipping update of WeeklyCatFactJob entity as updates are not supported
            logger.error("WeeklyCatFactJob id {} failed: {}", technicalId, e.getMessage());
        }

        return job;
    }
}
