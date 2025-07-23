package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class PetOrder implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String orderId;
    private String petId;
    private String customerName;
    private Integer quantity;
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
        if (orderId == null || orderId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (customerName == null || customerName.isBlank()) return false;
        if (quantity == null || quantity <= 0) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
