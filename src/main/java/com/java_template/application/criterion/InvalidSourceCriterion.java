package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InvalidSourceCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public InvalidSourceCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("InvalidSourceCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InvalidSourceCriterion".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet entity) {
        // Business logic: Validate pet source URL format in tags (simulate source validity check)
        boolean validSourceFound = false;
        if (entity.getTags() != null) {
            for (String tag : entity.getTags()) {
                if (tag != null && tag.startsWith("source:")) {
                    String sourceUrl = tag.substring(7);
                    if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
                        validSourceFound = true;
                        break;
                    }
                }
            }
        }
        if (!validSourceFound) {
            return EvaluationOutcome.fail("Invalid or missing source URL in pet tags", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
