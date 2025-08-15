package com.java_template.application.entity.order.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String orderNumber;
    private String userId; // serialized UUID
    private String customerId; // serialized UUID (legacy)
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal total;
    private String currency;
    private String status; // e.g., PENDING, PAID
    private String paymentId; // serialized UUID
    private String shipmentId; // serialized UUID
    private String paymentStatus; // e.g., AUTHORIZED, CAPTURED, FAILED
    private String createdAt;

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if ((userId == null || userId.isBlank()) && (customerId == null || customerId.isBlank())) return false;
        if (items == null) return false;
        if (total == null) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId; // serialized UUID
        private Integer quantity;
        private BigDecimal unitPrice;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (unitPrice == null) return false;
            return true;
        }
    }
}
