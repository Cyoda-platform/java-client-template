package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SubscriberCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    @Autowired
    private EntityService entityService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public SubscriberCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber entity = context.entity();
        
        // Validate email format
        if (!isValidEmailFormat(entity.getEmail())) {
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Check email uniqueness (exclude unsubscribed users)
        if (!isEmailUnique(entity.getEmail())) {
            return EvaluationOutcome.fail("Email already exists", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        // Validate verification token if present
        if (entity.getPreferences() != null && entity.getPreferences().containsKey("verificationToken")) {
            String token = (String) entity.getPreferences().get("verificationToken");
            if (!isValidVerificationToken(token)) {
                return EvaluationOutcome.fail("Invalid verification token", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        
        // Validate required fields
        if (entity.getEmail() == null || entity.getEmail().trim().isEmpty()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate subscription date is not in future
        if (entity.getSubscriptionDate() != null && entity.getSubscriptionDate().isAfter(java.time.LocalDateTime.now())) {
            return EvaluationOutcome.fail("Subscription date cannot be in the future", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
    
    private boolean isValidEmailFormat(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    private boolean isEmailUnique(String email) {
        try {
            // Search for existing subscribers with the same email (excluding unsubscribed)
            Condition emailCondition = Condition.of("$.email", "EQUALS", email);
            Condition notUnsubscribedCondition = Condition.of("$.meta.state", "NOT_EQUALS", "unsubscribed");
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(emailCondition, notUnsubscribedCondition));
            
            Optional<EntityResponse<Subscriber>> existingSubscriber = entityService.getFirstItemByCondition(
                Subscriber.class, 
                Subscriber.ENTITY_NAME, 
                Subscriber.ENTITY_VERSION, 
                condition, 
                true
            );
            
            return existingSubscriber.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking email uniqueness: {}", e.getMessage(), e);
            return false; // Fail safe - assume not unique if we can't check
        }
    }
    
    private boolean isValidVerificationToken(String token) {
        // Basic validation - token should be non-empty and have reasonable length
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // Check token length (should be reasonable for a UUID or similar)
        if (token.length() < 10 || token.length() > 100) {
            return false;
        }
        
        // In a real implementation, we would:
        // 1. Check token format (UUID, etc.)
        // 2. Verify token expiration (24 hours)
        // 3. Check token against stored values
        
        return true;
    }
}
