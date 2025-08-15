package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.application.processor.ValidationRecordRepository;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MissingFieldsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MissingFieldsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating MissingFieldsCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            if (entity == null) {
                return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Determine missing fields using only existing entity properties and rawJson
            List<String> missing = new ArrayList<>();
            if (entity.getId() == null) missing.add("id");
            if (entity.getType() == null || entity.getType().isBlank()) missing.add("type");

            if (!missing.isEmpty()) {
                String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                ValidationRecord record = new ValidationRecord();
                record.setTechnicalId(UUID.randomUUID().toString());
                record.setHnItemId(entity.getId());
                record.setCheckedAt(now);
                record.setIsValid(Boolean.FALSE);
                record.setMissingFields(missing);
                record.setMessage("Missing required fields: " + String.join(", ", missing));
                ValidationRecordRepository.getInstance().save(record);

                return EvaluationOutcome.fail(record.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error evaluating MissingFieldsCriterion for HNItem {}: {}", entity == null ? "<null>" : entity.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Evaluation error: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
