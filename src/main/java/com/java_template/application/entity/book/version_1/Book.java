package com.java_template.application.entity.book.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Book implements CyodaEntity {
    public static final String ENTITY_NAME = Book.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier from external API
    private Long bookId;
    
    // Book data from API
    private String title;
    private String description;
    private Integer pageCount;
    private String excerpt;
    private LocalDateTime publishDate;
    
    // Processing metadata
    private LocalDateTime retrievedAt;
    private Double analysisScore;
    private UUID reportId;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation - more detailed validation is done in criteria
        return bookId != null && bookId > 0;
    }
}
