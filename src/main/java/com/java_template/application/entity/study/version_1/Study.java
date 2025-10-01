package com.java_template.application.entity.study.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Study entity for operational trial management
 * Created from approved submissions for conducting clinical trials
 */
@Data
public class Study implements CyodaEntity {
    public static final String ENTITY_NAME = Study.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String studyId;
    
    // Core study information
    private String sourceSubmissionId;
    private String title;
    private String protocolId;
    private String phase;
    private String therapeuticArea;
    
    // Study structure
    private List<StudyArm> arms;
    private List<VisitSchedule> visitSchedule;
    private List<String> sites;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return studyId != null && !studyId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               protocolId != null && !protocolId.trim().isEmpty();
    }

    /**
     * Nested class for study arms
     */
    @Data
    public static class StudyArm {
        private String armId;
        private String name;
        private String description;
    }

    /**
     * Nested class for visit schedule definition
     */
    @Data
    public static class VisitSchedule {
        private String visitCode;
        private String name;
        private Integer windowMinusDays;
        private Integer windowPlusDays;
        private List<String> procedures;
    }
}
