package com.java_template.application.entity.searchactivity.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class SearchActivity implements CyodaEntity {
    public static final String ENTITY_NAME = "SearchActivity";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String activityId; // search event id
    private String userId; // nullable for anonymous
    private String queryText; // raw user query
    private String timestamp; // when search happened
    private String filters; // JSON serialized filters (genres[], yearRange, authors[])
    private List<String> resultBookIds; // books returned
    private List<String> clickedBookIds; // books clicked

    public SearchActivity() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return activityId != null && !activityId.isBlank()
            && queryText != null && !queryText.isBlank()
            && timestamp != null && !timestamp.isBlank();
    }
}
