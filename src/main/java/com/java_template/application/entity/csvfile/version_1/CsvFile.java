package com.java_template.application.entity.csvfile.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;
import java.util.HashMap;

@Data
public class CsvFile implements CyodaEntity {
    public static final String ENTITY_NAME = "CsvFile";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // internal id referencing the persisted CSV entity
    private String sourceType; // upload | url | email origin indicator
    private String sourceLocation; // upload path or URL or email metadata
    private String filename; // original file name
    private String uploadedAt; // datetime (ISO string)
    private Long sizeBytes; // file size
    private Map<String, String> detectedSchema = new HashMap<>(); // column name -> type sample
    private Integer rowCount; // rows counted after ingest
    private String status; // PENDING | VALID | INVALID | STORED
    private String errorMessage; // validation or ingest error

    public CsvFile() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (sourceType == null || sourceType.isBlank()) return false;
        if (sourceLocation == null || sourceLocation.isBlank()) return false;
        if (filename == null || filename.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (sizeBytes == null || sizeBytes < 0) return false;
        if (rowCount == null || rowCount < 0) return false;
        return true;
    }
}
