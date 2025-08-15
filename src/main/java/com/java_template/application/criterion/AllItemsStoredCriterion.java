package com.java_template.application.criterion;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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
public class AllItemsStoredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AllItemsStoredCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
        IngestJob job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("IngestJob missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        List<String> tids = job.getCreatedItemTechnicalIds();
        if (tids == null || tids.isEmpty()) {
            // nothing to wait for
            return EvaluationOutcome.success();
        }
        for (String tid : tids) {
            HNItem item = HNItemRepository.getInstance().findByTechnicalId(tid);
            if (item == null) {
                return EvaluationOutcome.fail("HNItem not found: " + tid, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            String status = item.getStatus();
            if (status == null) {
                return EvaluationOutcome.fail("HNItem status unknown: " + tid, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            if (!"STORED".equalsIgnoreCase(status)) {
                // if any item is not yet stored, the criterion fails (means job should not transition to COMPLETED)
                return EvaluationOutcome.fail("Not all items stored", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }
}
