package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class DeduplicationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         // Deduplication requires either a source id OR a full identity triplet (firstname, surname, year).
         // If neither is available, deduplication cannot proceed and should be marked as failed.
         if (entity == null) {
             return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If an authoritative source id exists, deduplication can proceed
         if (entity.getId() != null) {
             return EvaluationOutcome.success();
         }

         // Otherwise ensure firstname, surname and year are present and non-blank
         String firstname = entity.getFirstname();
         String surname = entity.getSurname();
         String year = entity.getYear();

         if (firstname == null || firstname.isBlank()
             || surname == null || surname.isBlank()
             || year == null || year.isBlank()) {
             return EvaluationOutcome.fail(
                 "Insufficient identity information for deduplication: require id or (firstname, surname, year)",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         return EvaluationOutcome.success();
    }
}