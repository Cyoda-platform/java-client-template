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

@Component
public class AuthorMarksReady implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AuthorMarksReady(SerializerFactory serializerFactory) {
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
        // Use exact criterion name match
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PostVersion> context) {
         PostVersion entity = context.entity();

         // Required identifiers and relationships
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

         // Content presence and basic quality checks
         String content = entity.getContent_rich();
         if (content == null || content.isBlank()) {
             return EvaluationOutcome.fail("content_rich must be provided by the author before marking ready", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String trimmed = content.trim();
         if (trimmed.length() < 20) {
             // Content is present but likely too short to meaningfully proceed to normalization/chunking.
             return EvaluationOutcome.fail("content_rich is too short to be marked ready", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If chunks_meta already present it indicates normalization/chunking has run;
         // this is allowed but should be consistent: each chunk must have a non-blank chunk_ref.
         if (entity.getChunks_meta() != null) {
             for (PostVersion.ChunkMeta cm : entity.getChunks_meta()) {
                 if (cm == null) {
                     return EvaluationOutcome.fail("chunks_meta contains null entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (cm.getChunk_ref() == null || cm.getChunk_ref().isBlank()) {
                     return EvaluationOutcome.fail("chunk_meta.chunk_ref is required when chunks_meta is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (cm.getText() == null) {
                     return EvaluationOutcome.fail("chunk_meta.text must not be null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // All checks passed — author can mark this version as ready for publish.
         return EvaluationOutcome.success();
    }
}