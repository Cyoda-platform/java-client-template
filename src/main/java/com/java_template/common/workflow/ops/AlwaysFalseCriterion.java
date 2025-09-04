package com.java_template.common.workflow.ops;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
 * ABOUTME: Test criterion implementation that always returns false evaluation result
 * for testing and debugging workflow execution paths.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class AlwaysFalseCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(AlwaysFalseCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public AlwaysFalseCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("AlwaysFalseCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("AlwaysFalseCriterion check for request: {}", request.getId());

        // Use the new Jackson serializer with sealed interfaces - always returns non-match
        return serializer.responseBuilder(request).withNonMatch().build();
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return Config.INCLUDE_DEFAULT_OPERATIONS || className.equals(opsSpec.operationName());
    }
}
