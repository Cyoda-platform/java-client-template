package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private LocalDateTime requestTimestamp;
    private String petDataQuery;
    private List<String> emailRecipients;
    private String status; // use String for status enum representation

    public DigestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && technicalId != null && requestTimestamp != null
                && petDataQuery != null && !petDataQuery.isBlank()
                && emailRecipients != null && !emailRecipients.isEmpty()
                && status != null && !status.isBlank();
    }
}
