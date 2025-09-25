package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.participant.version_1.Participant;
import com.java_template.application.entity.study.version_1.Study;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor for enrolling a participant in a study
 * 
 * Handles participant enrollment, updates study enrollment counts,
 * and performs necessary validations.
 */
@Component
public class ParticipantEnrollmentProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantEnrollmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ParticipantEnrollmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Participant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid participant entity wrapper")
                .map(this::processParticipantEnrollment)
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

        // Validate that participant is in consented state
        String currentState = entityWithMetadata.metadata().getState();
        if (!"consented".equals(currentState)) {
            logger.error("Participant {} is not in consented state. Current state: {}", participant.getParticipantId(), currentState);
            return false;
        }

        logger.info("Successfully validated participant for enrollment: {}", participant.getParticipantId());
        return true;
    }

    /**
     * Processes the participant enrollment
     */
    private EntityWithMetadata<Participant> processParticipantEnrollment(EntityWithMetadata<Participant> entityWithMetadata) {
        Participant participant = entityWithMetadata.entity();
        logger.info("Processing participant enrollment for participant: {}", participant.getParticipantId());

        try {
            // Set enrollment information
            if (participant.getEnrollment() == null) {
                participant.setEnrollment(new Participant.EnrollmentInfo());
            }
            
            participant.getEnrollment().setEnrollmentDate(LocalDate.now());
            participant.getEnrollment().setEnrollmentStatus("Enrolled");
            participant.getEnrollment().setEligibilityConfirmed(true);

            // Update study enrollment count
            updateStudyEnrollmentCount(participant);

            // Update audit fields
            participant.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Successfully enrolled participant: {}", participant.getParticipantId());
            return entityWithMetadata;
            
        } catch (Exception e) {
            logger.error("Error enrolling participant: {}", participant.getParticipantId(), e);
            throw new RuntimeException("Failed to enroll participant: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the study enrollment count
     */
    private void updateStudyEnrollmentCount(Participant participant) {
        try {
            String studyId = participant.getStudyId();
            if (studyId == null || studyId.trim().isEmpty()) {
                logger.warn("No study ID found for participant: {}", participant.getParticipantId());
                return;
            }

            // Find the study by business ID
            EntityWithMetadata<Study> studyEntity = entityService.findByBusinessId(
                createStudyModelSpec(), 
                "studyId", 
                studyId, 
                Study.class
            );

            if (studyEntity != null) {
                Study study = studyEntity.entity();
                
                // Increment current enrollment
                Integer currentEnrollment = study.getCurrentEnrollment();
                if (currentEnrollment == null) {
                    currentEnrollment = 0;
                }
                study.setCurrentEnrollment(currentEnrollment + 1);
                
                // Update actual enrollment if needed
                Integer actualEnrollment = study.getActualEnrollment();
                if (actualEnrollment == null || actualEnrollment < study.getCurrentEnrollment()) {
                    study.setActualEnrollment(study.getCurrentEnrollment());
                }

                study.setUpdatedAt(LocalDateTime.now());

                // Save the updated study (without transition to stay in same state)
                entityService.save(studyEntity, Study.class);
                
                logger.info("Updated enrollment count for study: {} to {}", studyId, study.getCurrentEnrollment());
            } else {
                logger.warn("Study not found for ID: {}", studyId);
            }
            
        } catch (Exception e) {
            logger.error("Error updating study enrollment count for participant: {}", participant.getParticipantId(), e);
            // Don't fail the enrollment if study update fails
        }
    }

    /**
     * Creates model specification for Study entity
     */
    private ModelSpec createStudyModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Study.ENTITY_NAME);
        modelSpec.setVersion(Study.ENTITY_VERSION);
        return modelSpec;
    }
}
