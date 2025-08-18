package com.java_template.application.entity.importevent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class ImportEvent implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportEvent";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String eventId; // unique event/audit id
    private Long itemId; // HN id associated with the event
    private String timestamp; // ISO8601 UTC
    private String status; // SUCCESS/FAILURE
    private List<String> errors; // validation or processing errors

    public ImportEvent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (eventId == null || eventId.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
