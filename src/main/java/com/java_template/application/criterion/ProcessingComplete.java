package com.java_template.application.criterion;

import com.java_template.application.entity.media.version_1.Media;
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
public class ProcessingComplete implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingComplete(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Media.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Media> context) {
         Media media = context.entity();

         // Basic required fields
         if (media.getMedia_id() == null || media.getMedia_id().isBlank()) {
             return EvaluationOutcome.fail("media_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (media.getFilename() == null || media.getFilename().isBlank()) {
             return EvaluationOutcome.fail("filename is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (media.getMime() == null || media.getMime().isBlank()) {
             return EvaluationOutcome.fail("mime is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (media.getCreated_at() == null || media.getCreated_at().isBlank()) {
             return EvaluationOutcome.fail("created_at timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Processing-specific checks
         String status = media.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required to determine processing state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"processed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("media not marked as processed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // After processing we expect a CDN reference and at least one version entry
         if (media.getCdn_ref() == null || media.getCdn_ref().isBlank()) {
             return EvaluationOutcome.fail("CDN reference missing after processing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (media.getVersions() == null || media.getVersions().isEmpty()) {
             return EvaluationOutcome.fail("no media versions recorded after processing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}