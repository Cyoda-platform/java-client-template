package com.java_template.application.entity.rfi.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RFI (Request for Information) entity for messaging and document requests
 * Supports threaded conversations and document requests with due dates
 */
@Data
public class RFI implements CyodaEntity {
    public static final String ENTITY_NAME = RFI.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String rfiId;
    
    // Parent entity association
    private String parentType; // submission, study
    private String parentId;
    
    // RFI details
    private String title;
    private String message;
    private List<String> requestedDocuments;
    private LocalDateTime dueAt;
    private String status; // open, answered, closed
    
    // Participants and thread
    private List<String> participants;
    private List<Message> thread;
    
    // Audit fields
    private String createdBy;
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
        return rfiId != null && !rfiId.trim().isEmpty() &&
               parentType != null && !parentType.trim().isEmpty() &&
               parentId != null && !parentId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty();
    }

    /**
     * Nested class for message thread
     */
    @Data
    public static class Message {
        private String messageId;
        private String author;
        private String body;
        private List<String> attachments;
        private LocalDateTime createdAt;
    }
}
