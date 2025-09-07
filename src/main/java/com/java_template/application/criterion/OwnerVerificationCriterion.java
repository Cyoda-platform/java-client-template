package com.java_template.application.criterion;

import com.java_template.application.entity.owner.version_1.Owner;
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

/**
 * OwnerVerificationCriterion - Determines if an owner account can be automatically verified
 * 
 * Validates:
 * - Email address format and completeness
 * - All required contact information is complete and valid
 * - Phone number format is valid
 * - Address information is complete
 */
@Component
public class OwnerVerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OwnerVerificationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Owner verification criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Owner.class, this::validateOwner)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOwner(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
        Owner owner = context.entityWithMetadata().entity();
        
        // Check if owner is null (structural validation)
        if (owner == null) {
            logger.warn("Owner is null");
            return EvaluationOutcome.fail("Owner entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 1. Check email verification status and format
        EvaluationOutcome emailValidation = validateEmail(owner);
        if (!emailValidation.isSuccess()) {
            return emailValidation;
        }

        // 2. Validate contact completeness
        EvaluationOutcome contactValidation = validateContactCompleteness(owner);
        if (!contactValidation.isSuccess()) {
            return contactValidation;
        }

        // 3. Check phone number format
        EvaluationOutcome phoneValidation = validatePhoneNumber(owner);
        if (!phoneValidation.isSuccess()) {
            return phoneValidation;
        }

        // 4. Validate address information
        EvaluationOutcome addressValidation = validateAddressInformation(owner);
        if (!addressValidation.isSuccess()) {
            return addressValidation;
        }

        logger.debug("Owner {} passed all verification criteria", owner.getOwnerId());
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateEmail(Owner owner) {
        if (owner.getEmail() == null || owner.getEmail().trim().isEmpty()) {
            logger.warn("Owner {} has no email address", owner.getOwnerId());
            return EvaluationOutcome.fail("Email address is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String email = owner.getEmail().trim();
        
        // Basic email format validation
        if (!isValidEmailFormat(email)) {
            logger.warn("Owner {} has invalid email format: {}", owner.getOwnerId(), email);
            return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check for valid domain
        if (!hasValidEmailDomain(email)) {
            logger.warn("Owner {} has invalid email domain: {}", owner.getOwnerId(), email);
            return EvaluationOutcome.fail("Email domain is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateContactCompleteness(Owner owner) {
        // Check firstName and lastName
        if (owner.getFirstName() == null || owner.getFirstName().trim().isEmpty()) {
            logger.warn("Owner {} has no first name", owner.getOwnerId());
            return EvaluationOutcome.fail("First name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (owner.getLastName() == null || owner.getLastName().trim().isEmpty()) {
            logger.warn("Owner {} has no last name", owner.getOwnerId());
            return EvaluationOutcome.fail("Last name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check phone number presence
        if (owner.getPhoneNumber() == null || owner.getPhoneNumber().trim().isEmpty()) {
            logger.warn("Owner {} has no phone number", owner.getOwnerId());
            return EvaluationOutcome.fail("Phone number is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePhoneNumber(Owner owner) {
        String phoneNumber = owner.getPhoneNumber().trim();
        
        if (!isValidPhoneFormat(phoneNumber)) {
            logger.warn("Owner {} has invalid phone format: {}", owner.getOwnerId(), phoneNumber);
            return EvaluationOutcome.fail("Phone number format is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateAddressInformation(Owner owner) {
        // Check address completeness
        if (owner.getAddress() == null || owner.getAddress().trim().isEmpty()) {
            logger.warn("Owner {} has no address", owner.getOwnerId());
            return EvaluationOutcome.fail("Address is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (owner.getCity() == null || owner.getCity().trim().isEmpty()) {
            logger.warn("Owner {} has no city", owner.getOwnerId());
            return EvaluationOutcome.fail("City is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (owner.getZipCode() == null || owner.getZipCode().trim().isEmpty()) {
            logger.warn("Owner {} has no zip code", owner.getOwnerId());
            return EvaluationOutcome.fail("Zip code is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate zip code format
        if (!isValidZipCodeFormat(owner.getZipCode())) {
            logger.warn("Owner {} has invalid zip code format: {}", owner.getOwnerId(), owner.getZipCode());
            return EvaluationOutcome.fail("Zip code format is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean isValidEmailFormat(String email) {
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && email.indexOf("@") < email.lastIndexOf(".");
    }

    private boolean hasValidEmailDomain(String email) {
        String domain = email.substring(email.indexOf("@") + 1);
        return domain.length() > 3 && domain.contains(".");
    }

    private boolean isValidPhoneFormat(String phone) {
        // Allow various phone formats: +1-555-0123, (555) 123-4567, 555.123.4567, etc.
        return phone.matches("^[+]?[0-9\\s\\-\\(\\)\\.]{10,}$");
    }

    private boolean isValidZipCodeFormat(String zipCode) {
        // Support US zip codes: 12345 or 12345-6789
        return zipCode.matches("^\\d{5}(-\\d{4})?$");
    }
}
