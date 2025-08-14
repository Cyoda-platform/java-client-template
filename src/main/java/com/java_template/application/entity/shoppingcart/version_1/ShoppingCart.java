package com.java_template.application.entity.shoppingcart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;

@Data
public class ShoppingCart implements CyodaEntity {
    public static final String ENTITY_NAME = "ShoppingCart";
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String userId;
    private List<CartItem> items;

    public ShoppingCart() {}

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
            && items != null && !items.isEmpty()
            && items.stream().allMatch(CartItem::isValid);
    }
}

@Data
class CartItem {
    private String productId;
    private Integer quantity;

    public boolean isValid() {
        return productId != null && !productId.isBlank()
            && quantity != null && quantity > 0;
    }
}
