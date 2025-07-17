package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Book implements CyodaEntity {
    private int id;
    private String title;
    private String description;
    private int pageCount;
    private String excerpt;
    private String publishDate;

    public Book() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("book");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "book");
    }

    @Override
    public boolean isValid() {
        // Basic validation: id should be positive, title should not be empty
        return id > 0 && title != null && !title.isEmpty();
    }
}