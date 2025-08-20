package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import java.time.OffsetDateTime;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String orderId;
    private String orderNumber; // unique
    private String userId; // serialized UUID
    private String shippingAddressId; // serialized UUID
    private List<OrderLine> lines;
    private Totals totals;
    private String status; // WAITING_TO_FULFILL -> PICKING -> SENT
    private OffsetDateTime createdAt;

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
        if (orderId == null || orderId.isBlank()) return false;
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (shippingAddressId == null || shippingAddressId.isBlank()) return false;
        if (lines == null || lines.isEmpty()) return false;
        if (totals == null) return false;
        return true;
    }

    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;
    }

    @Data
    public static class Totals {
        private Integer items;
        private Double grand;
    }
}
