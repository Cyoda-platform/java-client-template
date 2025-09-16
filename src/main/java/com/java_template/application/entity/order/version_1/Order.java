package com.java_template.application.entity.order.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order entity representing a purchase order for pets in the store.
 * Implements CyodaEntity for workflow integration.
 */
@Data
public class Order implements CyodaEntity {

    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long id;
    private Long petId;
    private Long userId;
    private Integer quantity;
    private LocalDateTime shipDate;
    private Boolean complete;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return petId != null && quantity != null && quantity > 0;
    }
}
