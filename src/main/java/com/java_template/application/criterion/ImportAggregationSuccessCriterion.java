package com.java_template.application.criterion;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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

import java.util.Map;

@Component
public class ImportAggregationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportAggregationSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
        ImportJob job = context.entity();
        if (job == null) return EvaluationOutcome.fail("ImportJob missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        Object summary = job.getResultSummary();
        if (!(summary instanceof Map)) return EvaluationOutcome.fail("No summary available", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        Map map = (Map) summary;
        Object errors = map.get("errors");
        if (errors instanceof Integer && ((Integer) errors) == 0) {
            return EvaluationOutcome.success();
        }
        // if errors key not present assume success if processed > 0
        Object processed = map.get("processed");
        if (processed instanceof Integer && ((Integer) processed) > 0) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Import did not complete successfully", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}