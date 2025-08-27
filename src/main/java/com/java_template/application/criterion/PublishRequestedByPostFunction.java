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
import java.util.Map;

@Component
public class PublishRequestedByPostFunction implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PublishRequestedByPostFunction(SerializerFactory serializerFactory) {
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
         PostVersion version = context.entity();

         // Basic validation: content must be present to allow finalization
         if (version.getContentRich() == null || version.getContentRich().isBlank()) {
             return EvaluationOutcome.fail("PostVersion content is required for finalization", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure identifiers are present (defensive, though entity.isValid() should already enforce these)
         if (version.getVersionId() == null || version.getVersionId().isBlank()) {
             return EvaluationOutcome.fail("versionId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (version.getPostId() == null || version.getPostId().isBlank()) {
             return EvaluationOutcome.fail("postId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (version.getAuthorId() == null || version.getAuthorId().isBlank()) {
             return EvaluationOutcome.fail("authorId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For finalization the version must have been normalized and chunked.
         String normalized = version.getNormalizedText();
         List<Map<String, Object>> chunks = version.getChunksMeta();
         if (normalized == null || normalized.isBlank()) {
             return EvaluationOutcome.fail("PostVersion has not been normalized; run normalize_and_chunk before finalization", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (chunks == null || chunks.isEmpty()) {
             return EvaluationOutcome.fail("PostVersion has no chunk metadata; run normalize_and_chunk to produce chunks", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If embeddingsRef exists but no chunks/normalized text, it's inconsistent
         if (version.getEmbeddingsRef() != null && (normalized == null || normalized.isBlank() || chunks.isEmpty())) {
             return EvaluationOutcome.fail("Embeddings reference present but version lacks normalized text or chunks", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> allow publish request to proceed to finalize_version/enqueue_embeddings
         return EvaluationOutcome.success();
    }
}