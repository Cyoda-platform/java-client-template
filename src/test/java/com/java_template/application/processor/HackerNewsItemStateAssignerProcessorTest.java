package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HackerNewsItemStateAssignerProcessorTest {

    private static class TestProcessorSerializer implements ProcessorSerializer {
        private HackerNewsItem processedEntity;

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode extractPayload(EntityProcessorCalculationRequest request) {
            return null;
        }

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> com.fasterxml.jackson.databind.JsonNode entityToJsonNode(T entity) {
            return new ObjectMapper().valueToTree(entity);
        }

        @Override
        public String getType() { return "test"; }

        @Override
        public <R> R executeFunction(EntityProcessorCalculationRequest request, java.util.function.Function<ProcessorExecutionContext, R> function) { return null; }

        @Override
        public com.java_template.common.serializer.ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request) { return com.java_template.common.serializer.ResponseBuilder.forProcessor(request); }

        @Override
        public ProcessingChain withRequest(EntityProcessorCalculationRequest request) {
            return new ProcessingChain() {
                @Override
                public ProcessingChain map(java.util.function.Function<ProcessorExecutionContext, com.fasterxml.jackson.databind.JsonNode> mapper) { return this; }

                @Override
                public <T extends com.java_template.common.workflow.CyodaEntity> EntityProcessingChain<T> toEntity(Class<T> clazz) {
                    HackerNewsItem e = new HackerNewsItem();
                    e.setId(123L);
                    e.setType("story");
                    return new EntityProcessingChain<T>() {
                        private T ent = (T) e;

                        @Override
                        public EntityProcessingChain<T> map(java.util.function.Function<ProcessorEntityExecutionContext<T>, T> mapper) {
                            ProcessorEntityExecutionContext<T> ctx = new ProcessorEntityExecutionContext<>(null, ent);
                            ent = mapper.apply(ctx);
                            if (ent instanceof HackerNewsItem) processedEntity = (HackerNewsItem) ent;
                            return this;
                        }

                        @Override
                        public EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator) { return validate(validator, "Entity validation failed"); }

                        @Override
                        public EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator, String errorMessage) {
                            Boolean ok = validator.apply(ent);
                            if (ok == null || !ok) throw new IllegalArgumentException(errorMessage);
                            return this;
                        }

                        @Override
                        public ProcessingChain toJsonFlow(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) { return null; }

                        @Override
                        public EntityProcessingChain<T> withErrorHandler(java.util.function.BiFunction<java.lang.Throwable, T, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }

                        @Override
                        public EntityProcessorCalculationResponse complete() { return Mockito.mock(EntityProcessorCalculationResponse.class); }
                    };
                }

                @Override
                public ProcessingChain withErrorHandler(java.util.function.BiFunction<java.lang.Throwable, com.fasterxml.jackson.databind.JsonNode, com.java_template.common.serializer.ErrorInfo> errorHandler) { return this; }

                @Override
                public EntityProcessorCalculationResponse complete() { return Mockito.mock(EntityProcessorCalculationResponse.class); }
            };
        }
    }

    @Test
    void testProcess_AssignsValidState() {
        // Arrange
        TestProcessorSerializer testSerializer = new TestProcessorSerializer();
        SerializerFactory serializerFactory = mock(SerializerFactory.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(testSerializer);

        EntityService entityService = mock(EntityService.class);
        HackerNewsItemStateAssignerProcessor processor = new HackerNewsItemStateAssignerProcessor(serializerFactory, entityService);

        CyodaEventContextStub context = new CyodaEventContextStub();

        // Act
        EntityProcessorCalculationResponse resp = processor.process(context);

        // Assert
        assertNotNull(resp);
        assertNotNull(testSerializer.processedEntity);
        assertEquals("VALID", testSerializer.processedEntity.getState());
    }

    private static class CyodaEventContextStub implements com.java_template.common.workflow.CyodaEventContext<EntityProcessorCalculationRequest> {
        @Override public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
        @Override public EntityProcessorCalculationRequest getEvent() { return Mockito.mock(EntityProcessorCalculationRequest.class); }
    }
}
