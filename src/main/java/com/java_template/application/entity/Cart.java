package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";

    private String cartId;
    private String customerId;
    private List<CartItem> items;
    private String status;
    private LocalDateTime createdAt;

    public Cart() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return cartId != null && !cartId.isBlank()
            && customerId != null && !customerId.isBlank()
            && status != null && !status.isBlank();
    }
}
