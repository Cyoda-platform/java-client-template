package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;
import java.util.Map;

@Data
public class Order implements CyodaEntity {
    private String orderId;
    private String customerId;
    private List<Map<String, Object>> items;
    private String shippingAddress;
    private String paymentMethod;
    private String createdAt;
    private String status;

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
        if (orderId == null || orderId.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (shippingAddress == null || shippingAddress.isBlank()) return false;
        if (paymentMethod == null || paymentMethod.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
