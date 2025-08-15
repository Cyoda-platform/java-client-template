package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String userId; // customer business id (serialized UUID)
    private List<OrderItem> items; // list of order items
    private Double subtotal;
    private Double tax;
    private Double shipping;
    private Double total;
    private String status; // cart checkout_pending reserved paid shipped completed cancelled
    private String createdAt; // DateTime as ISO string
    private String reservedInventoryTx; // reference to inventory reservation
    private String paymentRef; // payment reference if any

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
        if (userId == null || userId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        // validate items
        for (OrderItem item : items) {
            if (item == null) return false;
            if (item.getProductId() == null || item.getProductId().isBlank()) return false;
            if (item.getQty() == null || item.getQty() <= 0) return false;
            if (item.getUnitPrice() == null || item.getUnitPrice() < 0) return false;
        }
        if (total == null || total < 0) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId;
        private Integer qty;
        private Double unitPrice;
        private Double lineTotal;

        public OrderItem() {}
    }
}
