package com.java_template.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Attachment DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO implements CyodaEntity {

    public static final String ENTITY_NAME = "Attachment";
    public static final Integer ENTITY_VERSION = 1;
    private static final ModelSpec MODEL_SPEC = new ModelSpec().withName(ENTITY_NAME).withVersion(ENTITY_VERSION);

    private UUID id;
    private UUID projectId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private LocalDateTime uploadedAt;
    /** ID of the corresponding EdgeMessage in Cyoda that holds the file content */
    private UUID messageId;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        return new OperationSpecification.Entity(MODEL_SPEC, ENTITY_NAME);
    }
}

