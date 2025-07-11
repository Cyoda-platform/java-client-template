package com.java_template.common.workflow.ops;

import com.java_template.application.serializer.CriterionSerializer;
import com.java_template.application.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A CyodaCriterion that always returns true.
 * Updated to use the new Jackson serializer architecture with sealed interfaces.
 * Demonstrates simple criteria implementation with modern serializer patterns.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class AlwaysTrueCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(AlwaysTrueCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public AlwaysTrueCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("AlwaysTrueCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("AlwaysTrueCriterion check for request: {}", request.getId());

        // Use the new Jackson serializer with sealed interfaces
        return serializer.matchResponse(request).withSuccessfulMatch("AlwaysTrueCriterion always matches").build();
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return Config.INCLUDE_DEFAULT_OPERATIONS || className.equals(opsSpec.operationName());
    }

}
