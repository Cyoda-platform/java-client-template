package com.java_template.entity.pet;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.ModelKey;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pet entity for workflow processing.
 */
@Data
public class Pet implements CyodaEntity {

    private Long id;
    private String name;
    private String status;
    private String category;
    private List<String> tags = new ArrayList<>();
    private List<String> photoUrls = new ArrayList<>();
    private String lastModified;

    public Pet() {}
    

    
    @Override
    public ModelKey getModelKey() {
        return new ModelKey("pet", "1.0");
    }

    @Override
    public boolean isValid() {
        return id != null && name != null && !name.trim().isEmpty();
    }
    
    // Business logic methods
    public void normalizeStatus() {
        if (status != null) {
            this.status = status.toLowerCase();
        }
    }
    
    public void addLastModifiedTimestamp() {
        this.lastModified = Instant.now().toString();
    }
    
    public boolean hasStatus() {
        return status != null && !status.trim().isEmpty();
    }

}
