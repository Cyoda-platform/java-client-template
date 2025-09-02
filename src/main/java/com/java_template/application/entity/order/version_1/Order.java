package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long id;
    private Long petId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerAddress;
    private Integer quantity;
    private LocalDateTime orderDate;
    private LocalDateTime shipDate;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String shippingMethod;
    private String trackingNumber;
    private String notes;
    private Boolean complete;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return customerName != null && !customerName.trim().isEmpty() &&
               customerEmail != null && !customerEmail.trim().isEmpty() &&
               petId != null &&
               quantity != null && quantity > 0 &&
               totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
