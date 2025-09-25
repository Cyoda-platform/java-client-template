package com.java_template.application.criterion;

import com.java_template.application.entity.participant.version_1.Participant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;

/**
 * Criterion for validating participant eligibility for clinical studies
 * 
 * Checks if a participant meets basic eligibility requirements including
 * demographics, medical history, and consent status.
 */
@Component
public class ParticipantEligibilityCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantEligibilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public ParticipantEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Participant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid participant entity wrapper")
                .map(this::checkParticipantEligibility)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Participant> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("Entity or metadata is null");
            return false;
        }

        Participant participant = entityWithMetadata.entity();
        if (!participant.isValid()) {
            logger.error("Participant entity validation failed for participant: {}", participant.getParticipantId());
            return false;
        }

        logger.info("Successfully validated participant for eligibility check: {}", participant.getParticipantId());
        return true;
    }

    /**
     * Checks participant eligibility criteria
     */
    private boolean checkParticipantEligibility(EntityWithMetadata<Participant> entityWithMetadata) {
        Participant participant = entityWithMetadata.entity();
        logger.info("Checking eligibility for participant: {}", participant.getParticipantId());

        try {
            // Check demographics requirements
            if (!hasValidDemographics(participant)) {
                logger.warn("Participant {} has invalid demographics", participant.getParticipantId());
                return false;
            }

            // Check age requirements (example: 18-75 years old)
            if (!meetsAgeRequirements(participant)) {
                logger.warn("Participant {} does not meet age requirements", participant.getParticipantId());
                return false;
            }

            // Check consent status
            if (!hasValidConsent(participant)) {
                logger.warn("Participant {} does not have valid consent", participant.getParticipantId());
                return false;
            }

            // Check for exclusionary medical conditions
            if (hasExclusionaryConditions(participant)) {
                logger.warn("Participant {} has exclusionary medical conditions", participant.getParticipantId());
                return false;
            }

            // Check for prohibited medications
            if (hasProhibitedMedications(participant)) {
                logger.warn("Participant {} is taking prohibited medications", participant.getParticipantId());
                return false;
            }

            logger.info("Participant {} meets all eligibility criteria", participant.getParticipantId());
            return true;

        } catch (Exception e) {
            logger.error("Error checking eligibility for participant: {}", participant.getParticipantId(), e);
            return false;
        }
    }

    /**
     * Checks if participant has valid demographics
     */
    private boolean hasValidDemographics(Participant participant) {
        if (participant.getDemographics() == null) {
            return false;
        }

        Participant.Demographics demographics = participant.getDemographics();
        return demographics.getDateOfBirth() != null &&
               demographics.getGender() != null && !demographics.getGender().trim().isEmpty();
    }

    /**
     * Checks if participant meets age requirements
     */
    private boolean meetsAgeRequirements(Participant participant) {
        if (participant.getDemographics() == null || participant.getDemographics().getDateOfBirth() == null) {
            return false;
        }

        LocalDate dateOfBirth = participant.getDemographics().getDateOfBirth();
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();

        // Example age requirements: 18-75 years old
        return age >= 18 && age <= 75;
    }

    /**
     * Checks if participant has valid consent
     */
    private boolean hasValidConsent(Participant participant) {
        if (participant.getConsents() == null || participant.getConsents().isEmpty()) {
            return false;
        }

        // Check if there's at least one valid main study consent
        return participant.getConsents().stream()
                .anyMatch(consent -> "Main Study".equals(consent.getConsentType()) && 
                                   Boolean.TRUE.equals(consent.getConsentGiven()) &&
                                   consent.getConsentDate() != null);
    }

    /**
     * Checks for exclusionary medical conditions
     */
    private boolean hasExclusionaryConditions(Participant participant) {
        if (participant.getMedicalHistory() == null || 
            participant.getMedicalHistory().getConditions() == null) {
            return false;
        }

        // Example exclusionary conditions
        String[] exclusionaryConditions = {
            "Active Cancer",
            "Severe Heart Disease",
            "Uncontrolled Diabetes",
            "Active Infection"
        };

        return participant.getMedicalHistory().getConditions().stream()
                .anyMatch(condition -> {
                    for (String exclusionary : exclusionaryConditions) {
                        if (condition.getCondition() != null && 
                            condition.getCondition().contains(exclusionary) &&
                            Boolean.TRUE.equals(condition.getOngoing())) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    /**
     * Checks for prohibited medications
     */
    private boolean hasProhibitedMedications(Participant participant) {
        if (participant.getMedications() == null) {
            return false;
        }

        // Example prohibited medications
        String[] prohibitedMeds = {
            "Warfarin",
            "Immunosuppressants",
            "Experimental Drugs"
        };

        return participant.getMedications().stream()
                .anyMatch(medication -> {
                    for (String prohibited : prohibitedMeds) {
                        if (medication.getMedicationName() != null && 
                            medication.getMedicationName().contains(prohibited) &&
                            Boolean.TRUE.equals(medication.getConcomitant())) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
