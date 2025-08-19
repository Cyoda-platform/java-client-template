package com.java_template.application.entity.book.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Book implements CyodaEntity {
    public static final String ENTITY_NAME = "Book";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer id; // API source id
    private String title;
    private String description;
    private Integer pageCount;
    private String excerpt;
    private OffsetDateTime publishDate;
    private OffsetDateTime retrievedAt;
    private String sourceStatus;
    private Double popularityScore;

    public Book() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null) return false;
        if (title == null || title.isBlank()) return false;
        if (pageCount == null || pageCount <= 0) return false;
        if (publishDate == null) return false;
        return true;
    }
}
