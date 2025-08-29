package com.java_template.application.entity.article.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Article implements CyodaEntity {
    public static final String ENTITY_NAME = "Article"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String articleId;   // technical id
    private String authorId;    // foreign key (serialized UUID as String)
    private String title;
    private String body;
    private String createdAt;   // ISO-8601 timestamp as String
    private String status;
    private List<String> tags = new ArrayList<>();

    public Article() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: articleId, authorId, title, createdAt, status
        return !isNullOrBlank(articleId)
                && !isNullOrBlank(authorId)
                && !isNullOrBlank(title)
                && !isNullOrBlank(createdAt)
                && !isNullOrBlank(status);
    }

    private boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }
}