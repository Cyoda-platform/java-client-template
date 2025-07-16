package com.java_template.application.criterion;

import com.java_template.application.entity.EntityName;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
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

@Component
public class ExampleCriterionName implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ExampleCriterionName(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ExampleCriterionName initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EntityName validity for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EntityName.class, this::validateEntityName)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .withErrorHandler((error, entityName) -> {
                logger.debug("EntityName validation failed for request: {}", request.getId(), error);
                return ErrorInfo.validationError("EntityName validation failed: " + error.getMessage());
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ExampleCriterionName".equals(modelSpec.operationName()) &&
               "entityName".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Helper methods like validateEntityName etc.
}