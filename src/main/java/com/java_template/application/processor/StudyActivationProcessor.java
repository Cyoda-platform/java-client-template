package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.submission.version_1.Submission;
import com.java_template.application.entity.study.version_1.Study;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor for activating studies from approved submissions
 * Creates a study entity from the approved submission data
 */
@Component
public class StudyActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StudyActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StudyActivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission wrapper")
                .map(this::processStudyActivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Submission> entityWithMetadata) {
        Submission entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Submission> processStudyActivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Activating study from submission: {}", submission.getSubmissionId());

        // Create study from submission
        Study study = createStudyFromSubmission(submission);
        
        // Save the new study
        EntityWithMetadata<Study> savedStudy = entityService.create(study);
        
        logger.info("Study {} created successfully from submission {}", 
                   study.getStudyId(), submission.getSubmissionId());

        // Update submission timestamp
        submission.setUpdatedAt(LocalDateTime.now());

        return entityWithMetadata;
    }

    private Study createStudyFromSubmission(Submission submission) {
        Study study = new Study();
        
        // Generate unique study ID
        study.setStudyId("STUDY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        
        // Copy core information from submission
        study.setSourceSubmissionId(submission.getSubmissionId());
        study.setTitle(submission.getTitle());
        study.setProtocolId(submission.getProtocolId());
        study.setPhase(submission.getPhase());
        study.setTherapeuticArea(submission.getTherapeuticArea());
        study.setSites(submission.getSites());
        
        // Create default study arms
        List<Study.StudyArm> arms = new ArrayList<>();
        Study.StudyArm defaultArm = new Study.StudyArm();
        defaultArm.setArmId("ARM-001");
        defaultArm.setName("Treatment Arm");
        defaultArm.setDescription("Primary treatment arm");
        arms.add(defaultArm);
        study.setArms(arms);
        
        // Create default visit schedule
        List<Study.VisitSchedule> visitSchedule = new ArrayList<>();
        
        Study.VisitSchedule baseline = new Study.VisitSchedule();
        baseline.setVisitCode("V1");
        baseline.setName("Baseline");
        baseline.setWindowMinusDays(0);
        baseline.setWindowPlusDays(3);
        baseline.setProcedures(List.of("SCREENING", "CONSENT", "VITALS", "LAB"));
        visitSchedule.add(baseline);
        
        Study.VisitSchedule followUp = new Study.VisitSchedule();
        followUp.setVisitCode("V2");
        followUp.setName("Follow-up");
        followUp.setWindowMinusDays(2);
        followUp.setWindowPlusDays(3);
        followUp.setProcedures(List.of("VITALS", "LAB", "AE_ASSESSMENT"));
        visitSchedule.add(followUp);
        
        study.setVisitSchedule(visitSchedule);
        
        // Set audit fields
        study.setCreatedAt(LocalDateTime.now());
        study.setUpdatedAt(LocalDateTime.now());
        
        return study;
    }
}
