package com.java_template.application.entity.searchrequest.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.Objects;

@Data
public class SearchRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "SearchRequest";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String species; // filter e.g., dog
    private String status; // filter e.g., available
    private Integer categoryId; // filter
    private String sortBy; // optional
    private Integer page; // optional
    private Integer pageSize; // optional
    private String userId; // requester
    private Boolean notifyOnNoResults; // save/search alert
    private String createdAt; // timestamp

    public SearchRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: require a requester and at least one filter (species, status or categoryId)
        if (userId == null || userId.isBlank()) {
            return false;
        }
        boolean hasFilter = (species != null && !species.isBlank())
                || (status != null && !status.isBlank())
                || (categoryId != null);
        return hasFilter;
    }
}
