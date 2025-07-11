package com.java_template.application.entity.pet;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

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
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(1000);
        return new OperationSpecification.Entity(modelSpec, "pet");
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
