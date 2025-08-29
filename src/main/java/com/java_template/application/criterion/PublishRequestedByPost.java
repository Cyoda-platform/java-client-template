package com.java_template.application.criterion;

import com.java_template.application.entity.postversion.version_1.PostVersion;
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

import java.util.List;

@Component
public class PublishRequestedByPost implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PublishRequestedByPost(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PostVersion.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PostVersion> context) {
         PostVersion entity = context.entity();

         // Basic identity validation
         if (entity.getVersion_id() == null || entity.getVersion_id().isBlank()) {
             return EvaluationOutcome.fail("version_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPost_id() == null || entity.getPost_id().isBlank()) {
             return EvaluationOutcome.fail("post_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAuthor_id() == null || entity.getAuthor_id().isBlank()) {
             return EvaluationOutcome.fail("author_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreated_at() == null || entity.getCreated_at().isBlank()) {
             return EvaluationOutcome.fail("created_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure version has been normalized and chunked before finalization
         String normalized = entity.getNormalized_text();
         List<PostVersion.ChunkMeta> chunks = entity.getChunks_meta();
         if (normalized == null || normalized.isBlank() || chunks == null || chunks.isEmpty()) {
             return EvaluationOutcome.fail("PostVersion must be normalized and chunked before publish is requested", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Content presence check (content_rich may be blank but should at least exist)
         if (entity.getContent_rich() == null) {
             return EvaluationOutcome.fail("content_rich is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}