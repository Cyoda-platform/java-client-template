package com.java_template.application.entity.order.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String orderId;
    private String orderNumber;
    private String userId; // serialized UUID reference
    private String shippingAddressId; // serialized UUID reference
    private List<OrderLine> lines = new ArrayList<>();
    private Totals totals;
    private String status; // WAITING_TO_FULFILL, PICKING, SENT
    private String createdAt;
    private String updated_at;
    private List<StateTransition> state_transitions = new ArrayList<>();

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
        if (totals.getItems() == null || totals.getItems() < 0) return false;
        if (totals.getGrand() == null || totals.getGrand() < 0) return false;
        return true;
    }

    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;

        public OrderLine() {}
    }

    @Data
    public static class Totals {
        private Integer items;
        private Double grand;

        public Totals() {}
    }

    @Data
    public static class StateTransition {
        private String from;
        private String to;
        private String actor;
        private String timestamp;
        private String note;

        public StateTransition() {}
    }
}
