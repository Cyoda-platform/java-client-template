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
    private String title; // book title
    private String description; // full description
    private Integer pageCount; // number of pages
    private String excerpt; // short excerpt
    private OffsetDateTime publishDate; // publication date
    private OffsetDateTime retrievedAt; // when fetched
    private String sourceStatus; // ok/missing/invalid
    private Double popularityScore; // computed score for ranking

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
