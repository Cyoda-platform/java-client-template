package com.java_template.application.entity.cartorder.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class CartOrder implements CyodaEntity {
    public static final String ENTITY_NAME = "CartOrder";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String orderId; // business id
    private String customerId; // links to User.userId
    private List<Item> items = new ArrayList<>(); // list of order items
    private Double subtotal; 
    private Double tax;
    private Double total;
    private String status; // Cart, PendingPayment, Confirmed, Shipped, Completed, Cancelled
    private String createdAt; // ISO timestamp
    private String updatedAt; // ISO timestamp

    public CartOrder() {}

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
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null) return false;
        return true;
    }

    @Data
    public static class Item {
        private String productId;
        private Integer quantity;
        private Double unitPrice;

        public Item() {}
    }
}
