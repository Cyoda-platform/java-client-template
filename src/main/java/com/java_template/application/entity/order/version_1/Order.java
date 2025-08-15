package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Fields
    private String id; // UUID as string
    private String orderNumber;
    private String customerId; // references User.id
    private List<Item> items = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal taxes;
    private BigDecimal shipping;
    private BigDecimal total;
    private String currency;
    private String paymentStatus; // Pending|Paid|Failed
    private String fulfillmentStatus; // Pending|Confirmed|Shipped|Cancelled
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601

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
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (total == null) return false;
        if (items == null) return false;
        for (Item it : items) {
            if (it.getProductId() == null || it.getProductId().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() < 1) return false;
        }
        return true;
    }

    @Data
    public static class Item {
        private String productId; // UUID as string
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
