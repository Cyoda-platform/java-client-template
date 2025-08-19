package com.java_template.application.entity.book.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Book implements CyodaEntity {
    public static final String ENTITY_NAME = "Book";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String openLibraryId; // external source id
    private String title; // display title
    private List<String> authors; // author names
    private String coverImageUrl; // URL for cover
    private Integer publicationYear; // year of publication
    private List<String> genres; // genre tags
    private String summary; // short description
    private String lastIngestedAt; // timestamp

    public Book() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return openLibraryId != null && !openLibraryId.isBlank()
            && title != null && !title.isBlank();
    }
}
