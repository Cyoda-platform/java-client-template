package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String name;
    private String species;
    private String breed;
    private String sex;
    private String size;
    private Integer age_value;
    private String age_unit;
    private String availability_status;
    private String health_status;
    private String created_at;
    private Location location;
    private List<String> photos;
    private List<String> temperament_tags;

    public Pet() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (availability_status == null || availability_status.isBlank()) return false;
        if (created_at == null || created_at.isBlank()) return false;

        // Validate age
        if (age_value == null || age_value < 0) return false;
        if (age_unit == null || age_unit.isBlank()) return false;

        // Validate location
        if (location == null) return false;
        if (location.getCity() == null || location.getCity().isBlank()) return false;
        if (location.getLat() == null || location.getLon() == null) return false;

        // Validate collections (allow empty but not null, and no blank items)
        if (photos == null) return false;
        for (String p : photos) {
            if (p == null || p.isBlank()) return false;
        }
        if (temperament_tags == null) return false;
        for (String t : temperament_tags) {
            if (t == null || t.isBlank()) return false;
        }

        return true;
    }

    @Data
    public static class Location {
        private String city;
        private Double lat;
        private Double lon;
        private String postal;
    }
}