package com.java_template.entity.petfetchrequest;


import com.java_template.common.workflow.CyodaEntity;
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
    public String getEntityType() {
        return "petfetchrequest";
    }

    @Override
    public Class<? extends CyodaEntity> getClazz() {
        return PetFetchRequest.class;
    }

    @Override
    public String getName() {
        return PetFetchRequest.class.getSimpleName();
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
