package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Order implements CyodaEntity {
    private String orderId;
    private String customerName;
    private String productCode;
    private Integer quantity;
    private String orderDate; // DateTime represented as ISO string
    private String status; // e.g. NEW, PROCESSING, SHIPPED

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("order");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "order");
    }

    @Override
    public boolean isValid() {
        return orderId != null && !orderId.isBlank()
            && customerName != null && !customerName.isBlank()
            && productCode != null && !productCode.isBlank()
            && quantity != null && quantity > 0
            && orderDate != null && !orderDate.isBlank()
            && status != null && !status.isBlank();
    }
}
