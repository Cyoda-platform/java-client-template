package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

public class ReleaseStockProcessorTest {

    static class StubSerializer implements ProcessorSerializer {
        @Override public String getType() { return "stub"; }
        @Override public <T> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz) { return null; }
        @Override public com.fasterxml.jackson.databind.JsonNode extractPayload(EntityProcessorCalculationRequest request) { return null; }
        @Override public <T> com.fasterxml.jackson.databind.JsonNode entityToJsonNode(T entity) { return null; }
        @Override public com.java_template.common.serializer.ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request) { return null; }
        @Override public ProcessorSerializer.ProcessingChain withRequest(EntityProcessorCalculationRequest request) {
            return new ProcessorSerializer.ProcessingChain() {
                @Override public ProcessorSerializer.ProcessingChain map(java.util.function.Function<ProcessorSerializer.ProcessorExecutionContext, com.fasterxml.jackson.databind.JsonNode> mapper) { return this; }
                @Override public <T extends com.java_template.common.workflow.CyodaEntity> ProcessorSerializer.EntityProcessingChain<T> toEntity(Class<T> clazz) {
                    return new ProcessorSerializer.EntityProcessingChain<>() {
                        @Override public ProcessorSerializer.EntityProcessingChain<T> map(java.util.function.Function<ProcessorSerializer.ProcessorEntityExecutionContext<T>, T> mapper) { return this; }
                        @Override public ProcessorSerializer.EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator) { return this; }
                        @Override public ProcessorSerializer.ProcessingChain toJsonFlow(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) { return null; }
                        @Override public ProcessorSerializer.EntityProcessingChain<T> withErrorHandler(java.util.function.BiFunction<Throwable, T, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }
                        @Override public EntityProcessorCalculationResponse complete() { return Mockito.mock(EntityProcessorCalculationResponse.class); }
                        @Override public EntityProcessorCalculationResponse complete(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) { return Mockito.mock(EntityProcessorCalculationResponse.class); }
                    };
                }
                @Override public ProcessorSerializer.ProcessingChain withErrorHandler(java.util.function.BiFunction<Throwable, com.fasterxml.jackson.databind.JsonNode, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }
                @Override public EntityProcessorCalculationResponse complete() { return Mockito.mock(EntityProcessorCalculationResponse.class); }
            };
        }
    }

    @Test
    public void testProcessSuccess() {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        when(sf.getDefaultProcessorSerializer()).thenReturn(new StubSerializer());
        EntityService es = Mockito.mock(EntityService.class);

        ReleaseStockProcessor proc = new ReleaseStockProcessor(sf, es);
        EntityProcessorCalculationRequest req = Mockito.mock(EntityProcessorCalculationRequest.class);
        com.java_template.common.workflow.CyodaEventContext<EntityProcessorCalculationRequest> ctx = Mockito.mock(com.java_template.common.workflow.CyodaEventContext.class);
        when(ctx.getEvent()).thenReturn(req);

        EntityProcessorCalculationResponse resp = proc.process(ctx);
        assertNotNull(resp);
    }
}
