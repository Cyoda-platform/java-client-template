package com.java_template.application.criterion;

import com.java_template.application.entity.study.version_1.Study;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Criterion for validating study eligibility requirements
 * 
 * Checks if a study meets all necessary criteria for activation,
 * including required fields, sponsor information, and regulatory approvals.
 */
@Component
public class StudyEligibilityCriterion implements CyodaCriterion {
    private static final Logger logger = LoggerFactory.getLogger(StudyEligibilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public StudyEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::checkStudyEligibility)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Study> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("Entity or metadata is null");
            return false;
        }

        Study study = entityWithMetadata.entity();
        if (!study.isValid()) {
            logger.error("Study entity validation failed for study: {}", study.getStudyId());
            return false;
        }

        logger.info("Successfully validated study for eligibility check: {}", study.getStudyId());
        return true;
    }

    /**
     * Checks study eligibility criteria
     */
    private boolean checkStudyEligibility(EntityWithMetadata<Study> entityWithMetadata) {
        Study study = entityWithMetadata.entity();
        logger.info("Checking eligibility for study: {}", study.getStudyId());

        try {
            // Check required basic information
            if (!hasRequiredBasicInfo(study)) {
                logger.warn("Study {} missing required basic information", study.getStudyId());
                return false;
            }

            // Check sponsor information
            if (!hasValidSponsorInfo(study)) {
                logger.warn("Study {} missing valid sponsor information", study.getStudyId());
                return false;
            }

            // Check protocol information
            if (!hasValidProtocolInfo(study)) {
                logger.warn("Study {} missing valid protocol information", study.getStudyId());
                return false;
            }

            // Check enrollment information
            if (!hasValidEnrollmentInfo(study)) {
                logger.warn("Study {} missing valid enrollment information", study.getStudyId());
                return false;
            }

            // Check regulatory approvals if required
            if (!hasRequiredRegulatoryApprovals(study)) {
                logger.warn("Study {} missing required regulatory approvals", study.getStudyId());
                return false;
            }

            logger.info("Study {} meets all eligibility criteria", study.getStudyId());
            return true;

        } catch (Exception e) {
            logger.error("Error checking eligibility for study: {}", study.getStudyId(), e);
            return false;
        }
    }

    /**
     * Checks if study has required basic information
     */
    private boolean hasRequiredBasicInfo(Study study) {
        return study.getTitle() != null && !study.getTitle().trim().isEmpty() &&
               study.getPhase() != null && !study.getPhase().trim().isEmpty() &&
               study.getStudyType() != null && !study.getStudyType().trim().isEmpty() &&
               study.getPrimaryObjective() != null && !study.getPrimaryObjective().trim().isEmpty();
    }

    /**
     * Checks if study has valid sponsor information
     */
    private boolean hasValidSponsorInfo(Study study) {
        if (study.getSponsor() == null) {
            return false;
        }

        Study.SponsorInfo sponsor = study.getSponsor();
        return sponsor.getName() != null && !sponsor.getName().trim().isEmpty() &&
               sponsor.getType() != null && !sponsor.getType().trim().isEmpty() &&
               sponsor.getContactPerson() != null && !sponsor.getContactPerson().trim().isEmpty() &&
               sponsor.getEmail() != null && !sponsor.getEmail().trim().isEmpty();
    }

    /**
     * Checks if study has valid protocol information
     */
    private boolean hasValidProtocolInfo(Study study) {
        return study.getProtocolNumber() != null && !study.getProtocolNumber().trim().isEmpty() &&
               study.getProtocolVersion() != null && !study.getProtocolVersion().trim().isEmpty() &&
               study.getProtocolDate() != null;
    }

    /**
     * Checks if study has valid enrollment information
     */
    private boolean hasValidEnrollmentInfo(Study study) {
        return study.getPlannedEnrollment() != null && study.getPlannedEnrollment() > 0 &&
               study.getPlannedStartDate() != null;
    }

    /**
     * Checks if study has required regulatory approvals
     * For now, this is a simple check - in real implementation this would be more complex
     */
    private boolean hasRequiredRegulatoryApprovals(Study study) {
        // For Phase I studies, regulatory approvals might not be required yet
        if ("Phase I".equals(study.getPhase())) {
            return true;
        }

        // For other phases, check if we have at least one regulatory approval
        return study.getRegulatoryApprovals() != null && !study.getRegulatoryApprovals().isEmpty();
    }
}
