package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long id;
    private String name;
    private Category category;
    private List<String> photoUrls;
    private List<Tag> tags;
    private String description;
    private BigDecimal price;
    private LocalDate birthDate;
    private String breed;
    private String color;
    private Double weight;
    private Boolean vaccinated;
    private Boolean neutered;
    private Boolean microchipped;
    private String specialNeeds;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               photoUrls != null && !photoUrls.isEmpty() &&
               category != null;
    }

    @Data
    public static class Category {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        private Boolean active;
    }

    @Data
    public static class Tag {
        private Long id;
        private String name;
        private String color;
        private String description;
        private Boolean active;
    }
}
