package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

@Component
public class ArchiveOldFactsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveOldFactsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Archive facts older than this many days
    private static final int ARCHIVE_DAYS = 365;

    public ArchiveOldFactsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ArchiveOldFacts for request: {}", request.getId());

        return serializer.withRequest(request)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorExecutionContext context) {
        try {
            // Find all ready facts and archive those older than threshold
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ready")
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(CatFact.ENTITY_NAME, String.valueOf(CatFact.ENTITY_VERSION), condition, true);
            ArrayNode items = future.get();
            if (items == null) return null;
            for (int i = 0; i < items.size(); i++) {
                ObjectNode node = (ObjectNode) items.get(i);
                if (!node.has("retrieved_date")) continue;
                String retrieved = node.get("retrieved_date").asText();
                try {
                    OffsetDateTime rt = OffsetDateTime.parse(retrieved);
                    if (rt.isBefore(OffsetDateTime.now().minus(ARCHIVE_DAYS, ChronoUnit.DAYS))) {
                        // mark archived
                        node.put("archived", true);
                        node.put("status", "archived");
                        // persist update
                        entityService.updateItem(CatFact.ENTITY_NAME, String.valueOf(CatFact.ENTITY_VERSION), java.util.UUID.fromString(node.get("technicalId").asText()), node);
                        logger.info("Archived CatFact technicalId={}", node.get("technicalId").asText());
                    }
                } catch (Exception ex) {
                    logger.warn("Skipping archival for node due to parse error: {}", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.error("Error archiving old facts: {}", ex.getMessage(), ex);
        }
        return null;
    }
}
