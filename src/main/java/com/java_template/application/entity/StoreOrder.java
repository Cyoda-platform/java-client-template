package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class StoreOrder implements CyodaEntity {
    private Long orderId;
    private Long petId;
    private Integer quantity;
    private LocalDateTime shipDate;
    private String status;
    private Boolean complete;

    public StoreOrder() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("storeOrder");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "storeOrder");
    }

    @Override
    public boolean isValid() {
        if (petId == null) return false;
        if (quantity == null || quantity <= 0) return false;
        if (status == null || status.isBlank()) return false;
        if (complete == null) return false;
        return true;
    }
}
