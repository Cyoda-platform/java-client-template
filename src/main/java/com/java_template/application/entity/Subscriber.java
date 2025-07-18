package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;
import java.util.UUID;

@Data
public class Subscriber implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String contactInfo; // email or phone number for notifications
    private List<String> teamPreferences; // teams subscriber wants notifications about
    private String status; // SubscriberStatusEnum as String (ACTIVE, INACTIVE)

    public Subscriber() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("subscriber");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "subscriber");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && contactInfo != null && !contactInfo.isBlank() && status != null && !status.isBlank();
    }
}
