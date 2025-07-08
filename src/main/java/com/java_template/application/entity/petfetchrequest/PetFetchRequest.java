package com.java_template.application.entity.petfetchrequest;


import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.ModelKey;
import lombok.Data;

/**
 * Pet fetch request entity for workflow processing.
 */
@Data
public class PetFetchRequest implements CyodaEntity {

    private String sourceUrl;
    private String status;
    private Boolean valid;
    private String requestId;
    private String createdAt;

    public PetFetchRequest() {
    }

    @Override
    public ModelKey getModelKey() {
        return new ModelKey("petfetchrequest", "1.0");
    }

    @Override
    public boolean isValid() {
        return sourceUrl != null && !sourceUrl.trim().isEmpty()
                && status != null && !status.trim().isEmpty();
    }

    // Business logic methods
    public void validateRequest() {
        this.valid = isValid();
    }

    public boolean isFetchRequestValid() {
        return isValid();
    }


}
