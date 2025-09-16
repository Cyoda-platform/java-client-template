package com.java_template.application.entity.catfact.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

import static com.java_template.common.config.Config.ENTITY_VERSION;

/**
 * Represents a cat fact retrieved from the Cat Fact API or stored in the system.
 * Manages fact lifecycle through workflow states.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatFact implements CyodaEntity {

    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Unique identifier for the cat fact
     */
    private Long id;

    /**
     * The actual cat fact content (required)
     */
    private String factText;

    /**
     * Source of the fact (e.g., "catfact.ninja")
     */
    private String source;

    /**
     * Date and time when the fact was retrieved
     */
    private LocalDateTime retrievedDate;

    /**
     * Whether this fact has been used in an email campaign (default: false)
     */
    private Boolean isUsed;

    /**
     * Length of the fact text
     */
    private Integer length;

    /**
     * Category of the cat fact (optional)
     */
    private String category;

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
        // Fact text is required
        if (factText == null || factText.trim().isEmpty()) {
            return false;
        }
        
        // Length should match fact text length
        if (length == null || length != factText.length()) {
            return false;
        }
        
        // Source should be set
        if (source == null || source.trim().isEmpty()) {
            return false;
        }
        
        // Retrieved date should be set
        if (retrievedDate == null) {
            return false;
        }
        
        // isUsed should be set
        if (isUsed == null) {
            return false;
        }
        
        return true;
    }
}
