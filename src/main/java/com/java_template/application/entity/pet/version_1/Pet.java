package com.java_template.application.entity.pet.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.application.entity.category.version_1.Category;
import com.java_template.application.entity.tag.version_1.Tag;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

/**
 * Pet entity representing a pet in the store.
 * Implements CyodaEntity for workflow integration.
 */
@Data
public class Pet implements CyodaEntity {

    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long id;
    private String name;
    private Category category;
    private List<String> photoUrls;
    private List<Tag> tags;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               photoUrls != null && !photoUrls.isEmpty();
    }
}
