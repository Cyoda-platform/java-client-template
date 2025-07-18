package com.java_template.application.criterion;

import com.java_template.application.entity.InventoryMetrics;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.BiFunction;

@Component
public class InventoryMetricsValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public InventoryMetricsValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("InventoryMetricsValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking InventoryMetrics validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(InventoryMetrics.class, this::validateInventoryMetrics)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler(this::handleValidationError)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryMetricsValidationCriterion".equals(modelSpec.operationName()) &&
                "inventoryMetrics".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateInventoryMetrics(InventoryMetrics metrics) {
        if (metrics.getTotalItems() < 0) {
            return EvaluationOutcome.fail("Total items cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        if (metrics.getAveragePrice() < 0) {
            return EvaluationOutcome.fail("Average price cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        if (metrics.getTotalValue() < 0) {
            return EvaluationOutcome.fail("Total value cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        return EvaluationOutcome.success();
    }

    private ErrorInfo handleValidationError(Throwable error, InventoryMetrics entity) {
        logger.debug("InventoryMetrics validation failed for request: {}", entity, error);
        return ErrorInfo.validationError("InventoryMetrics validation failed: " + error.getMessage());
    }
}
