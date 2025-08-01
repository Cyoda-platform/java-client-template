package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";

    private String customerId;
    private List<OrderItem> orderItems;
    private BigDecimal totalAmount;
    private String orderDate;
    private String status;

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (customerId == null || customerId.isBlank()) return false;
        if (orderItems == null || orderItems.isEmpty()) return false;
        if (totalAmount == null || totalAmount.signum() < 0) return false;
        if (orderDate == null || orderDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
