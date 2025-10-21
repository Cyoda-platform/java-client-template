package com.java_template.application.entity.note.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Note entity that represents notes
 * associated with companies in the CRM system.
 */
@Data
public class Note implements CyodaEntity {
    public static final String ENTITY_NAME = Note.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String noteId;
    
    // Required core business fields
    private String companyId; // Reference to the company this note belongs to
    private String title;
    private String content;
    
    // Optional fields
    private String author;
    private String category; // e.g., "meeting", "call", "email", "general"
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
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return noteId != null && !noteId.trim().isEmpty() &&
               companyId != null && !companyId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               content != null && !content.trim().isEmpty();
    }
}
