package com.java_template.application.processor;

import com.java_template.application.entity.cartorder.version_1.CartOrder;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MarkShippedProcessorTest {

    @Test
    public void testProcess_markShipped() {
        SerializerFactory factory = mock(SerializerFactory.class);
        ProcessorSerializer serializer = mock(ProcessorSerializer.class);
        when(factory.getDefaultProcessorSerializer()).thenReturn(serializer);

        MarkShippedProcessor processor = new MarkShippedProcessor(factory);

        EntityProcessorCalculationRequest request = mock(EntityProcessorCalculationRequest.class);
        CannedProcessingChain<CartOrder> chain = new CannedProcessingChain<>(new CartOrder(), request);
        when(serializer.withRequest(request)).thenReturn(chain);

        CannedContext ctx = new CannedContext(request);

        CartOrder order = chain.getEntity();
        order.setOrderId("o1");
        order.setCustomerId("c1");

        EntityProcessorCalculationResponse resp = processor.process(ctx);
        assertNotNull(resp);
        assertEquals("Shipped", order.getStatus());
    }

    static class CannedContext implements CyodaEventContext<EntityProcessorCalculationRequest> {
        private final EntityProcessorCalculationRequest req;
        public CannedContext(EntityProcessorCalculationRequest req) { this.req = req; }
        @Override public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
        @Override public EntityProcessorCalculationRequest getEvent() { return req; }
    }

    static class CannedProcessingChain<T> implements ProcessorSerializer.ProcessingChain, ProcessorSerializer.EntityProcessingChain<T> {
        private T entity;
        private final EntityProcessorCalculationRequest req;

        public CannedProcessingChain(T entity, EntityProcessorCalculationRequest req) {
            this.entity = entity;
            this.req = req;
        }

        public T getEntity() { return entity; }

        @Override public ProcessorSerializer.ProcessingChain map(java.util.function.Function<ProcessorSerializer.ProcessorExecutionContext, com.fasterxml.jackson.databind.JsonNode> mapper) { return this; }
        @Override public <R extends com.java_template.common.workflow.CyodaEntity> ProcessorSerializer.EntityProcessingChain<R> toEntity(Class<R> clazz) {
            @SuppressWarnings("unchecked")
            ProcessorSerializer.EntityProcessingChain<R> cast = (ProcessorSerializer.EntityProcessingChain<R>) this;
            return cast;
        }
        @Override public ProcessorSerializer.ProcessingChain withErrorHandler(java.util.function.BiFunction<Throwable, com.fasterxml.jackson.databind.JsonNode, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }
        @Override public EntityProcessorCalculationResponse complete() { return mock(EntityProcessorCalculationResponse.class); }

        @Override public ProcessorSerializer.EntityProcessingChain<T> map(java.util.function.Function<ProcessorSerializer.ProcessorEntityExecutionContext<T>, T> mapper) {
            ProcessorSerializer.ProcessorEntityExecutionContext<T> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(req, entity);
            entity = mapper.apply(ctx);
            return this;
        }

        @Override public ProcessorSerializer.EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator) { return validate(validator, ""); }
        @Override public ProcessorSerializer.EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator, String errorMessage) { return this; }
        @Override public ProcessorSerializer.ProcessingChain toJsonFlow(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) { return this; }
        @Override public ProcessorSerializer.EntityProcessingChain<T> withErrorHandler(java.util.function.BiFunction<Throwable, T, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }
        @Override public EntityProcessorCalculationResponse complete(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) { return mock(EntityProcessorCalculationResponse.class); }
    }
}
