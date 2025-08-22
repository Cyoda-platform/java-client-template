package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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

import java.util.Set;

@Component
public class UserSanctionCheckCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // In-memory sanction lists (kept small and readable). In real systems this would be a callout to a sanctions/blacklist service.
    private static final Set<String> SANCTIONED_EMAIL_DOMAINS = Set.of(
        "sanctioned.example",
        "blocked-domain.example"
    );

    private static final Set<String> SANCTIONED_NAMES = Set.of(
        "bad actor",
        "prohibited entity"
    );

    private static final Set<String> SANCTIONED_PHONES = Set.of(
        "+19999999999",
        "+18888888888"
    );

    public UserSanctionCheckCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             logger.warn("UserSanctionCheckCriterion invoked with null entity");
             return EvaluationOutcome.success(); // nothing to check
         }

         // Check email domain against sanctioned domains
         String email = user.getEmail();
         if (email != null && !email.isBlank()) {
             int at = email.lastIndexOf('@');
             if (at >= 0 && at + 1 < email.length()) {
                 String domain = email.substring(at + 1).toLowerCase();
                 if (SANCTIONED_EMAIL_DOMAINS.contains(domain)) {
                     String msg = "User email domain is sanctioned: " + domain;
                     logger.info("Sanction hit for user.id={} reason=email-domain", user.getId());
                     return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         }

         // Check normalized name against sanctioned names (simple containment/equals check)
         String name = user.getName();
         if (name != null && !name.isBlank()) {
             String normalized = name.trim().toLowerCase();
             for (String badName : SANCTIONED_NAMES) {
                 if (normalized.equals(badName) || normalized.contains(badName)) {
                     String msg = "User name matches sanctioned list: " + name;
                     logger.info("Sanction hit for user.id={} reason=name", user.getId());
                     return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         }

         // Check phone against exact sanctioned phone numbers
         String phone = user.getPhone();
         if (phone != null && !phone.isBlank()) {
             String normalizedPhone = phone.trim();
             if (SANCTIONED_PHONES.contains(normalizedPhone)) {
                 String msg = "User phone is on sanctions list: " + normalizedPhone;
                 logger.info("Sanction hit for user.id={} reason=phone", user.getId());
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // No matches found -> user passes sanction check
         return EvaluationOutcome.success();
    }
}