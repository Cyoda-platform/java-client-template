package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.participant.version_1.Participant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Participant entity
 * 
 * Provides CRUD operations and specialized endpoints for participant management.
 */
@RestController
@RequestMapping("/ui/participant")
@CrossOrigin(origins = "*")
public class ParticipantController {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ParticipantController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new participant
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Participant>> createParticipant(@RequestBody Participant participant) {
        logger.info("Creating new participant: {}", participant.getParticipantId());
        
        try {
            EntityWithMetadata<Participant> savedParticipant = entityService.save(participant, Participant.class);
            logger.info("Successfully created participant with technical ID: {}", savedParticipant.metadata().getId());
            return ResponseEntity.ok(savedParticipant);
        } catch (Exception e) {
            logger.error("Error creating participant: {}", participant.getParticipantId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get participant by technical ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Participant>> getParticipantById(@PathVariable UUID id) {
        logger.info("Retrieving participant by technical ID: {}", id);
        
        try {
            EntityWithMetadata<Participant> participant = entityService.findById(createModelSpec(), id, Participant.class);
            if (participant != null) {
                return ResponseEntity.ok(participant);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving participant by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get participant by business ID (participantId)
     */
    @GetMapping("/business/{participantId}")
    public ResponseEntity<EntityWithMetadata<Participant>> getParticipantByBusinessId(@PathVariable String participantId) {
        logger.info("Retrieving participant by business ID: {}", participantId);
        
        try {
            EntityWithMetadata<Participant> participant = entityService.findByBusinessId(
                createModelSpec(), 
                "participantId", 
                participantId, 
                Participant.class
            );
            if (participant != null) {
                return ResponseEntity.ok(participant);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving participant by business ID: {}", participantId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update participant
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Participant>> updateParticipant(
            @PathVariable UUID id, 
            @RequestBody Participant participant,
            @RequestParam(required = false) String transition) {
        logger.info("Updating participant with technical ID: {}", id);
        
        try {
            EntityWithMetadata<Participant> existingParticipant = entityService.findById(createModelSpec(), id, Participant.class);
            if (existingParticipant == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Participant> updatedParticipant;
            if (transition != null && !transition.trim().isEmpty()) {
                updatedParticipant = entityService.save(participant, transition, Participant.class);
            } else {
                updatedParticipant = entityService.save(participant, Participant.class);
            }
            
            logger.info("Successfully updated participant: {}", participant.getParticipantId());
            return ResponseEntity.ok(updatedParticipant);
        } catch (Exception e) {
            logger.error("Error updating participant: {}", participant.getParticipantId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search participants by study
     */
    @GetMapping("/study/{studyId}")
    public ResponseEntity<List<EntityWithMetadata<Participant>>> getParticipantsByStudy(@PathVariable String studyId) {
        logger.info("Retrieving participants for study: {}", studyId);
        
        try {
            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.studyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(studyId));
            conditions.add(condition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Participant>> results = entityService.search(createModelSpec(), groupCondition, Participant.class);
            logger.info("Found {} participants for study: {}", results.size(), studyId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving participants for study: {}", studyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search participants by criteria
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Participant>>> searchParticipants(@RequestBody ParticipantSearchRequest searchRequest) {
        logger.info("Searching participants with criteria");
        
        try {
            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getStudyId() != null && !searchRequest.getStudyId().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.studyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStudyId()));
                conditions.add(condition);
            }

            if (searchRequest.getSiteId() != null && !searchRequest.getSiteId().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.siteId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSiteId()));
                conditions.add(condition);
            }

            if (searchRequest.getGender() != null && !searchRequest.getGender().trim().isEmpty()) {
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.demographics.gender")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getGender()));
                conditions.add(condition);
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Participant>> results = entityService.search(createModelSpec(), groupCondition, Participant.class);
            logger.info("Found {} participants matching criteria", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching participants", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get participant enrollment status
     */
    @GetMapping("/{id}/enrollment-status")
    public ResponseEntity<ParticipantEnrollmentStatus> getParticipantEnrollmentStatus(@PathVariable UUID id) {
        logger.info("Retrieving enrollment status for participant: {}", id);
        
        try {
            EntityWithMetadata<Participant> participant = entityService.findById(createModelSpec(), id, Participant.class);
            if (participant == null) {
                return ResponseEntity.notFound().build();
            }

            ParticipantEnrollmentStatus status = new ParticipantEnrollmentStatus();
            status.setParticipantId(participant.entity().getParticipantId());
            status.setState(participant.metadata().getState());
            
            if (participant.entity().getEnrollment() != null) {
                status.setEnrollmentStatus(participant.entity().getEnrollment().getEnrollmentStatus());
                status.setEnrollmentDate(participant.entity().getEnrollment().getEnrollmentDate());
                status.setEligibilityConfirmed(participant.entity().getEnrollment().getEligibilityConfirmed());
            }

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error retrieving enrollment status for participant: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete participant by technical ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParticipant(@PathVariable UUID id) {
        logger.info("Deleting participant with technical ID: {}", id);
        
        try {
            entityService.delete(createModelSpec(), id);
            logger.info("Successfully deleted participant with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting participant with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Creates model specification for Participant entity
     */
    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Participant.ENTITY_NAME);
        modelSpec.setVersion(Participant.ENTITY_VERSION);
        return modelSpec;
    }

    /**
     * Search request DTO
     */
    @Getter
    @Setter
    public static class ParticipantSearchRequest {
        private String studyId;
        private String siteId;
        private String gender;
        private String enrollmentStatus;
    }

    /**
     * Enrollment status DTO
     */
    @Getter
    @Setter
    public static class ParticipantEnrollmentStatus {
        private String participantId;
        private String state;
        private String enrollmentStatus;
        private java.time.LocalDate enrollmentDate;
        private Boolean eligibilityConfirmed;
    }
}
