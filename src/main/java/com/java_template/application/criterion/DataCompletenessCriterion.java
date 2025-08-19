package com.java_template.application.criterion;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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
public class DataCompletenessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DataCompletenessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ExtractionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        if (job == null || job.getParameters() == null) {
            return EvaluationOutcome.fail("Job parameters missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (job.getParameters().getEndpoints() == null || job.getParameters().getEndpoints().isEmpty()) {
            return EvaluationOutcome.fail("No endpoints configured", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If rawData present, ensure products array exists
        if (job.getParameters().getRawData() != null) {
            if (!job.getParameters().getRawData().has("products") || !job.getParameters().getRawData().get("products").isArray()) {
                return EvaluationOutcome.fail("Raw data missing products array", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
