package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.searchrequest.version_1.SearchRequest;
import com.java_template.application.entity.transformedpet.version_1.TransformedPet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PublishToUIProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishToUIProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PublishToUIProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PublishToUI for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchRequest entity) {
        return entity != null && "VALIDATED".equals(entity.getState()) || "TRANSFORMING".equals(entity.getState()) || "RESULTS_READY".equals(entity.getState());
    }

    private SearchRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchRequest> context) {
        SearchRequest entity = context.entity();
        try {
            // query transformed pets for this search request
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.searchRequestId", "EQUALS", entity.getTechnicalId())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                TransformedPet.ENTITY_NAME, String.valueOf(TransformedPet.ENTITY_VERSION), condition, true
            );
            ArrayNode items = itemsFuture.get();
            int total = items == null ? 0 : items.size();

            Map<String, Object> resultSummary = new HashMap<>();
            resultSummary.put("totalCount", total);
            resultSummary.put("page", entity.getPage() == null ? 1 : entity.getPage());
            resultSummary.put("pageSize", entity.getPageSize() == null ? 20 : entity.getPageSize());
            resultSummary.put("transformedPets", items == null ? new ArrayNode(null) : items);

            try { entity.setResultSummary(resultSummary); } catch (Exception e) { logger.debug("SearchRequest does not expose setResultSummary", e); }

            // set RESULTS_READY if there are results
            if (total > 0) {
                entity.setState("RESULTS_READY");
            } else {
                entity.setState("NO_RESULTS");
            }

            logger.info("PublishToUI completed for SearchRequest {} - total {}", entity.getTechnicalId(), total);
        } catch (Exception e) {
            logger.error("Error during publish to UI for SearchRequest {}", entity.getTechnicalId(), e);
        }
        return entity;
    }
}
