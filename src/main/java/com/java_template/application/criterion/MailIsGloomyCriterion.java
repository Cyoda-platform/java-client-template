package com.java_template.application.criterion;

import com.java_template.application.entity.mail.version_1.Mail;
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
public class MailIsGloomyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailIsGloomyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
         Mail mail = context.entity();

         // Validate presence of isHappy
         if (mail.getIsHappy() == null) {
             return EvaluationOutcome.fail("isHappy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion applies only when isHappy == false
         if (mail.getIsHappy()) {
             return EvaluationOutcome.fail("Mail is not gloomy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic data quality checks for mailList (ensure recipients exist and are not blank)
         if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
             return EvaluationOutcome.fail("mailList must contain at least one recipient", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (String recipient : mail.getMailList()) {
             if (recipient == null || recipient.isBlank()) {
                 return EvaluationOutcome.fail("mailList contains blank or null recipient", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}