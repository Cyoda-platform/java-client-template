package com.java_template.application.criterion;

import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class DataSufficientCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    private static final Set<String> PRICE_METRICS = new HashSet<>(Arrays.asList("avgPrice", "totalValue"));

    public DataSufficientCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(InventoryReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventoryReportJob> context) {
        InventoryReportJob job = context.entity();
        if (job == null) return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.VALIDATION_FAILURE);

        try {
            // Build a condition for the search similar to FetchInventoryProcessor
            SearchConditionRequest condition = null;
            if (job.getFilters() != null && job.getFilters().has("category")) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "EQUALS", job.getFilters().get("category").asText())
                );
            }

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture;
            if (condition != null) {
                itemsFuture = entityService.getItemsByCondition(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION),
                    condition,
                    true
                );
            } else {
                itemsFuture = entityService.getItems(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION)
                );
            }

            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                return EvaluationOutcome.fail("No inventory items match the requested filters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // If requested metrics require price data, ensure at least one non-null unitPrice
            boolean requiresPrice = false;
            if (job.getMetricsRequested() != null) {
                for (String m : job.getMetricsRequested()) {
                    if (PRICE_METRICS.contains(m)) {
                        requiresPrice = true; break;
                    }
                }
            }
            if (requiresPrice) {
                boolean anyPrice = false;
                for (int i = 0; i < items.size(); i++) {
                    com.fasterxml.jackson.databind.JsonNode node = items.get(i);
                    if (node.has("unitPrice") && !node.get("unitPrice").isNull()) { anyPrice = true; break; }
                }
                if (!anyPrice) {
                    return EvaluationOutcome.fail("No price data available for requested metrics", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }

            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error checking data sufficiency for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Error checking data sufficiency: " + e.getMessage(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
