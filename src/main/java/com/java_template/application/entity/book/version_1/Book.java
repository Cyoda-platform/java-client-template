package com.java_template.application.entity.book.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Book implements CyodaEntity {
    public static final String ENTITY_NAME = "Book"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Integer id; // technical id
    private String title;
    private String description;
    private String excerpt;
    private String fetchTimestamp; // ISO timestamp as String
    private Boolean isPopular;
    private Integer pageCount;
    private Double popularityScore;
    private String publishDate; // ISO date as String

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
        // Required fields
        if (id == null) return false;
        if (title == null || title.isBlank()) return false;
        if (pageCount == null || pageCount < 0) return false;
        if (publishDate == null || publishDate.isBlank()) return false;
        if (fetchTimestamp == null || fetchTimestamp.isBlank()) return false;
        // Optional numeric validations
        if (popularityScore != null) {
            if (popularityScore.isNaN() || popularityScore < 0.0) return false;
        }
        return true;
    }
}