package com.java_template.application.entity.weeklysend.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class WeeklySend implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklySend";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // domain id for this send/campaign
    private String catfact_id; // links to CatFact.id
    private String scheduled_date; // planned send date ISO
    private String actual_send_date; // when send occurred ISO
    private Integer recipients_count;
    private Integer opens_count;
    private Integer clicks_count;
    private Integer unsubscribes_count;
    private Integer bounces_count;
    private String status; // draft / scheduled / sending / sent / failed

    public WeeklySend() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (this.id == null || this.id.isBlank()) return false;
        if (this.catfact_id == null || this.catfact_id.isBlank()) return false;
        if (this.scheduled_date == null || this.scheduled_date.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
