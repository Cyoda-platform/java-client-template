package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class PetOrder implements CyodaEntity {

    private Long petId;
    private String customerName;
    private Integer quantity;
    private LocalDateTime orderDate;
    private String status;

    public PetOrder() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petOrder");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petOrder");
    }

    @Override
    public boolean isValid() {
        return petId != null
                && !(customerName == null || customerName.isBlank())
                && quantity != null && quantity > 0
                && orderDate != null
                && !(status == null || status.isBlank());
    }
}
