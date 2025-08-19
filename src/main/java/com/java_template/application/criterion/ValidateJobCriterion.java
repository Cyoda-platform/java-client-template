package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class ValidateJobCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Set<String> SUPPORTED_METRICS = new HashSet<>(Arrays.asList("totalCount", "avgPrice", "totalValue"));

    public ValidateJobCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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

        if (job.getMetricsRequested() == null || job.getMetricsRequested().isEmpty()) {
            return EvaluationOutcome.fail("At least one metric must be requested", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        for (String m : job.getMetricsRequested()) {
            if (!SUPPORTED_METRICS.contains(m)) {
                return EvaluationOutcome.fail("Unsupported metric: " + m, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        JsonNode filters = job.getFilters();
        if (filters != null && !filters.isObject()) {
            return EvaluationOutcome.fail("Filters must be a JSON object", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
