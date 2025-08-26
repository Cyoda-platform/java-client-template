package com.java_template.application.entity.searchfilter.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class SearchFilter implements CyodaEntity {
    public static final String ENTITY_NAME = "SearchFilter";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String name;
    private String userId; // serialized UUID reference
    private String createdAt; // ISO timestamp as String

    private Integer ageMin;
    private Integer ageMax;
    private String ageUnitPreference;

    private List<String> breeds;
    private Boolean isActive;
    private LocationCenter locationCenter;
    private Integer pageSize;
    private Integer radiusKm;
    private String sex;
    private List<String> size;
    private String sortBy;
    private String species;
    private List<String> temperamentTags;
    private Boolean vaccinationRequired;

    public SearchFilter() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic string presence checks for required string fields
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        // Boolean required flags
        if (isActive == null) return false;
        if (vaccinationRequired == null) return false;

        // Location validation
        if (locationCenter == null) return false;
        if (locationCenter.getLat() == null || locationCenter.getLon() == null) return false;
        if (locationCenter.getCity() == null || locationCenter.getCity().isBlank()) return false;

        // Numeric validations
        if (pageSize != null && pageSize <= 0) return false;
        if (radiusKm != null && radiusKm < 0) return false;
        if (ageMin != null && ageMin < 0) return false;
        if (ageMax != null && ageMax < 0) return false;
        if (ageMin != null && ageMax != null && ageMin > ageMax) return false;

        // List element validations (if present, elements must not be blank)
        if (breeds != null) {
            for (String b : breeds) {
                if (b == null || b.isBlank()) return false;
            }
        }
        if (size != null) {
            for (String s : size) {
                if (s == null || s.isBlank()) return false;
            }
        }
        if (temperamentTags != null) {
            for (String t : temperamentTags) {
                if (t == null || t.isBlank()) return false;
            }
        }

        // Optional simple string fields validation if present
        if (ageUnitPreference != null && ageUnitPreference.isBlank()) return false;
        if (sex != null && sex.isBlank()) return false;
        if (sortBy != null && sortBy.isBlank()) return false;
        if (species != null && species.isBlank()) return false;

        return true;
    }

    @Data
    public static class LocationCenter {
        private String city;
        private Double lat;
        private Double lon;
    }
}