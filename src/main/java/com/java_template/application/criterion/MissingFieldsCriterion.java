package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper = new ObjectMapper();

    public MissingFieldsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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
            if (entity == null || entity.getRawJson() == null) {
                return EvaluationOutcome.fail("HNItem rawJson missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            JsonNode parsed = mapper.readTree(entity.getRawJson());
            List<String> missing = new ArrayList<>();
            if (!parsed.hasNonNull("id")) missing.add("id");
            if (!parsed.hasNonNull("type")) missing.add("type");

            if (!missing.isEmpty()) {
                String message = "Missing required fields: " + String.join(", ", missing);
                // create validation record
                ValidationRecord record = new ValidationRecord();
                record.setTechnicalId(UUID.randomUUID().toString());
                if (parsed.hasNonNull("id")) {
                    record.setHnId(parsed.get("id").asLong());
                }
                record.setHnItemTechnicalId(entity.getTechnicalId());
                record.setIsValid(false);
                record.setMissingFields(missing);
                String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                record.setCheckedAt(now);
                record.setMessage(message);
                record.setCreatedAt(now);
                ValidationRecordRepository.getInstance().save(record);

                // attach failure reason
                return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error in MissingFieldsCriterion for HNItem {}: {}", entity == null ? "<null>" : entity.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Validation error: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
