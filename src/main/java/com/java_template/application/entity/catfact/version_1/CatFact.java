package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String factText;
    private Integer length;
    private String source;
    private LocalDateTime retrievedDate;
    private Integer usageCount;
    private LocalDateTime lastUsedDate;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return factText != null && !factText.trim().isEmpty() && 
               length != null && length >= 10 && length <= 500 &&
               usageCount != null && usageCount >= 0;
    }
}
