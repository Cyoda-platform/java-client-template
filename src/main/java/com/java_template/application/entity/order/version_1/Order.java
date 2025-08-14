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

    private String id;
    private String userId;
    private String orderDate;
    private Double totalAmount;
    private List<OrderItem> items;

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
        return id != null && !id.isBlank()
            && userId != null && !userId.isBlank()
            && orderDate != null && !orderDate.isBlank()
            && totalAmount != null
            && items != null && !items.isEmpty()
            && items.stream().allMatch(OrderItem::isValid);
    }
}

@Data
class OrderItem {
    private String productId;
    private Integer quantity;
    private Double price;

    public boolean isValid() {
        return productId != null && !productId.isBlank()
            && quantity != null && quantity > 0
            && price != null;
    }
}
