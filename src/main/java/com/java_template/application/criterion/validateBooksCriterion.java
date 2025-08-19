package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

@Component
public class validateBooksCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public validateBooksCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
                .evaluateEntity(FetchJob.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<FetchJob> context) {
        FetchJob job = context.entity();
        try {
            OffsetDateTime rangeStart = job.getLastRunAt();
            OffsetDateTime rangeEnd = OffsetDateTime.now(ZoneOffset.UTC);
            boolean snapshot = false;
            if (job.getParameters() != null && job.getParameters().containsKey("reportRange")) {
                Object v = job.getParameters().get("reportRange");
                if (v != null && "snapshot" .equalsIgnoreCase(String.valueOf(v))) snapshot = true;
            }

            ArrayNode booksArray;
            if (snapshot || rangeStart == null) {
                CompletableFuture<ArrayNode> fut = entityService.getItems(Book.ENTITY_NAME, String.valueOf(Book.ENTITY_VERSION));
                booksArray = fut.get();
            } else {
                SearchConditionRequest cond = SearchConditionRequest.group("AND",
                        Condition.of("$.retrievedAt", "GREATER_THAN", rangeStart.toString()),
                        Condition.of("$.retrievedAt", "LESS_THAN", rangeEnd.toString())
                );
                CompletableFuture<ArrayNode> fut = entityService.getItemsByCondition(Book.ENTITY_NAME, String.valueOf(Book.ENTITY_VERSION), cond, true);
                booksArray = fut.get();
            }

            if (booksArray == null) return EvaluationOutcome.fail("no books fetched", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);

            int total = booksArray.size();
            int invalid = 0;
            for (JsonNode n : booksArray) {
                try {
                    Book b = objectMapper.treeToValue(n, Book.class);
                    if (b.getSourceStatus() == null || !"ok".equalsIgnoreCase(b.getSourceStatus())) invalid++;
                    // require required fields
                    if (b.getId() == null || b.getTitle() == null || b.getTitle().isBlank() || b.getPageCount() == null || b.getPageCount() <= 0 || b.getPublishDate() == null) invalid++;
                } catch (Exception ex) {
                    invalid++;
                }
            }

            double invalidPct = total == 0 ? 0.0 : ((double) invalid) / total;
            double threshold = 0.2; // default allow up to 20% invalid
            if (job.getParameters() != null && job.getParameters().containsKey("invalidThreshold")) {
                try { threshold = Double.parseDouble(String.valueOf(job.getParameters().get("invalidThreshold"))); } catch (Exception ignored) {}
            }

            if (invalidPct > threshold) {
                return EvaluationOutcome.fail("invalid records percentage " + invalidPct + " exceeds threshold " + threshold, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            return EvaluationOutcome.success();
        } catch (Exception ex) {
            logger.error("validateBooksCriterion: unexpected error", ex);
            return EvaluationOutcome.fail("exception during validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
