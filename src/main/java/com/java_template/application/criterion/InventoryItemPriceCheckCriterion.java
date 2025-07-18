package com.java_template.application.criterion;

import com.java_template.application.entity.InventoryItem;
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
public class InventoryItemPriceCheckCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public InventoryItemPriceCheckCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("InventoryItemPriceCheckCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking InventoryItem price validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(InventoryItem.class, this::validateInventoryItemPrice)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler(this::handleValidationError)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryItemPriceCheckCriterion".equals(modelSpec.operationName()) &&
                "inventoryItem".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateInventoryItemPrice(InventoryItem item) {
        if (item.getPrice() < 0) {
            return EvaluationOutcome.fail("Price cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        return EvaluationOutcome.success();
    }

    private ErrorInfo handleValidationError(Throwable error, InventoryItem entity) {
        logger.debug("InventoryItem price validation failed for request: {}", entity, error);
        return ErrorInfo.validationError("InventoryItem price validation failed: " + error.getMessage());
    }
}
