package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Order implements CyodaEntity {
    private String orderId;
    private String petId;
    private Integer quantity;
    private LocalDateTime shipDate;
    private String status; // PLACED, APPROVED, DELIVERED

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("order");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "order");
    }

    @Override
    public boolean isValid() {
        return orderId != null && !orderId.isBlank()
            && petId != null && !petId.isBlank()
            && quantity != null && quantity > 0
            && shipDate != null
            && status != null && !status.isBlank();
    }
}
