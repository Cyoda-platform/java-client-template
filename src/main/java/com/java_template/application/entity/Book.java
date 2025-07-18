package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Data
public class Book implements CyodaEntity {

    private UUID technicalId;
    private String bookId;
    private String title;
    private List<String> authors = new ArrayList<>();
    private String coverImageUrl;
    private Integer publicationYear;
    private List<String> genres = new ArrayList<>();
    private String description;
    private String publisher;
    private String isbn;

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
        return bookId != null && !bookId.isBlank() && title != null && !title.isBlank();
    }
}