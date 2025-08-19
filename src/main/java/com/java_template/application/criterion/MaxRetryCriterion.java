package com.java_template.application.criterion;

import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MaxRetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MaxRetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(LookupJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<LookupJob> context) {
        LookupJob job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("LookupJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        int attempts = job.getAttempts() != null ? job.getAttempts() : 0;
        int maxAttempts = Config.MAX_ATTEMPTS; // use system config

        if (attempts < maxAttempts) {
            return EvaluationOutcome.success();
        }

        logger.debug("MaxRetryCriterion: attempts={} >= maxAttempts={} for job={}", attempts, maxAttempts, job.getTechnicalId());
        return EvaluationOutcome.fail("Max retry attempts exceeded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
