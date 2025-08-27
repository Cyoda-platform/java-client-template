package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";

    private String orderId;
    private String customerId;
    private List<OrderLine> items;
    private Double totalAmount;
    private ShippingAddress shippingAddress;
    private String status;
    private String createdAt;
    private String updatedAt;

    @Data
    public static class OrderLine {
        private String sku;
        private Integer quantity;
        private Double price;
    }

    @Data
    public static class ShippingAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }

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
        if (orderId == null || orderId.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (totalAmount == null || totalAmount < 0) return false;
        if (shippingAddress == null) return false;
        if (shippingAddress.getLine1() == null || shippingAddress.getLine1().isBlank()) return false;
        if (shippingAddress.getCity() == null || shippingAddress.getCity().isBlank()) return false;
        if (shippingAddress.getPostcode() == null || shippingAddress.getPostcode().isBlank()) return false;
        if (shippingAddress.getCountry() == null || shippingAddress.getCountry().isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
