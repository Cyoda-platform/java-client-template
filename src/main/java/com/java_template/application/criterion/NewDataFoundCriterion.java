package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NewDataFoundCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NewDataFoundCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("Job entity is null in NewDataFoundCriterion");
             return EvaluationOutcome.fail("Job entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         // This criterion applies after an ingestion run: we expect the job to have succeeded.
         if (status == null || !status.equalsIgnoreCase("SUCCEEDED")) {
             logger.debug("Job {} not in SUCCEEDED state (status={})", job.getJobId(), status);
             return EvaluationOutcome.fail("Job not in SUCCEEDED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String summary = job.getSummary();
         if (summary == null || summary.isBlank()) {
             logger.debug("Job {} summary is empty", job.getJobId());
             return EvaluationOutcome.fail("No summary available to determine ingested records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Expect summaries in form like "ingested 5 laureates" — extract first integer
         Pattern p = Pattern.compile("(\\d+)");
         Matcher m = p.matcher(summary);
         if (!m.find()) {
             logger.debug("Job {} summary did not contain an ingested count: {}", job.getJobId(), summary);
             return EvaluationOutcome.fail("No new laureates ingested", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         try {
             int count = Integer.parseInt(m.group(1));
             if (count <= 0) {
                 logger.debug("Job {} ingested zero laureates according to summary: {}", job.getJobId(), summary);
                 return EvaluationOutcome.fail("No new laureates ingested", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             logger.info("Job {} ingested {} laureates", job.getJobId(), count);
             return EvaluationOutcome.success();
         } catch (NumberFormatException ex) {
             logger.warn("Failed to parse ingested count from job {} summary '{}'", job.getJobId(), summary, ex);
             return EvaluationOutcome.fail("Unable to determine number of ingested laureates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }
}