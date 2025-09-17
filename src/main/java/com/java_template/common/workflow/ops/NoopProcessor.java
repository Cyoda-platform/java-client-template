package com.java_template.common.workflow.ops;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Test processor implementation that performs no operations and returns
 * the input payload unchanged for testing and debugging workflow execution.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class NoopProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NoopProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NoopProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("NoopProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("NoopProcessor processing request: {}", request.getId());

        return serializer.withRequest(request).complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return Config.INCLUDE_DEFAULT_OPERATIONS || className.equals(opSpec.operationName());
    }

}
