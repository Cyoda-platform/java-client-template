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

import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class SourceReachableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceReachableCriterion(SerializerFactory serializerFactory) {
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
        if (job == null || job.getSourceUrl() == null) {
            return EvaluationOutcome.fail("Source URL missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            URL url = new URL(job.getSourceUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            if (status >= 200 && status < 400) {
                return EvaluationOutcome.success();
            } else if (status >= 400 && status < 500) {
                return EvaluationOutcome.fail("Client error reaching source: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            } else {
                return EvaluationOutcome.fail("Source unreachable: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Error reaching source {}: {}", job.getSourceUrl(), e.getMessage());
            return EvaluationOutcome.fail("Unable to reach source: " + e.getMessage(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
