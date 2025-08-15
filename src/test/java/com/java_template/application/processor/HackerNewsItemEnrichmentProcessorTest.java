package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HackerNewsItemEnrichmentProcessorTest {

    private static class TestProcessorSerializer implements ProcessorSerializer {
        private final ObjectMapper mapper = new ObjectMapper();
        private HackerNewsItem processedEntity;

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz) {
            // not used in this test
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode extractPayload(EntityProcessorCalculationRequest request) {
            return null;
        }

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> com.fasterxml.jackson.databind.JsonNode entityToJsonNode(T entity) {
            return mapper.valueToTree(entity);
        }

        @Override
        public String getType() {
            return "test";
        }

        @Override
        public <R> R executeFunction(EntityProcessorCalculationRequest request, java.util.function.Function<ProcessorExecutionContext, R> function) {
            return null;
        }

        @Override
        public com.java_template.common.serializer.ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request) {
            return com.java_template.common.serializer.ResponseBuilder.forProcessor(request);
        }

        @Override
        public ProcessingChain withRequest(EntityProcessorCalculationRequest request) {
            return new ProcessingChain() {
                @Override
                public ProcessingChain map(java.util.function.Function<ProcessorExecutionContext, com.fasterxml.jackson.databind.JsonNode> mapper) {
                    return this;
                }

                @Override
                public <T extends com.java_template.common.workflow.CyodaEntity> EntityProcessingChain<T> toEntity(Class<T> clazz) {
                    // create a default entity for testing
                    HackerNewsItem e = new HackerNewsItem();
                    e.setOriginalJson("{\"id\":123,\"type\":\"story\"}");
                    return new EntityProcessingChain<T>() {
                        private T ent = (T) e;

                        @Override
                        public EntityProcessingChain<T> map(java.util.function.Function<ProcessorEntityExecutionContext<T>, T> mapper) {
                            try {
                                ProcessorEntityExecutionContext<T> ctx = new ProcessorEntityExecutionContext<>(null, ent);
                                ent = mapper.apply(ctx);
                                if (ent instanceof HackerNewsItem) {
                                    processedEntity = (HackerNewsItem) ent;
                                }
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                            return this;
                        }

                        @Override
                        public EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator) {
                            return validate(validator, "Entity validation failed");
                        }

                        @Override
                        public EntityProcessingChain<T> validate(java.util.function.Function<T, Boolean> validator, String errorMessage) {
                            Boolean ok = validator.apply(ent);
                            if (ok == null || !ok) throw new IllegalArgumentException(errorMessage);
                            return this;
                        }

                        @Override
                        public ProcessingChain toJsonFlow(java.util.function.Function<T, com.fasterxml.jackson.databind.JsonNode> converter) {
                            return null;
                        }

                        @Override
                        public EntityProcessingChain<T> withErrorHandler(java.util.function.BiFunction<java.lang.Throwable, T, com.java_template.common.serializer.ErrorInfo> errorHandler) {
                            return this;
                        }

                        @Override
                        public EntityProcessorCalculationResponse complete() {
                            return Mockito.mock(EntityProcessorCalculationResponse.class);
                        }
                    };
                }

                @Override
                public ProcessingChain withErrorHandler(java.util.function.BiFunction<java.lang.Throwable, com.fasterxml.jackson.databind.JsonNode, com.java_template.common.serializer.ErrorInfo> errorHandler) {
                    return this;
                }

                @Override
                public EntityProcessorCalculationResponse complete() {
                    return Mockito.mock(EntityProcessorCalculationResponse.class);
                }
            };
        }
    }

    @Test
    void testProcess_SuccessfulEnrichmentAddsItem() throws Exception {
        // Arrange
        TestProcessorSerializer testSerializer = new TestProcessorSerializer();
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(testSerializer);

        EntityService entityService = Mockito.mock(EntityService.class);
        ObjectMapper mapper = new ObjectMapper();

        // mock datastore search to return empty
        ArrayNode empty = mapper.createArrayNode();
        when(entityService.getItemsByCondition(anyString(), anyString(), any(), eq(true)))
            .thenReturn(CompletableFuture.completedFuture(empty));
        when(entityService.addItem(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        HackerNewsItemEnrichmentProcessor processor = new HackerNewsItemEnrichmentProcessor(serializerFactory, entityService, mapper);

        CyodaEventContextStub context = new CyodaEventContextStub();

        // Act
        EntityProcessorCalculationResponse resp = processor.process(context);

        // Assert
        assertNotNull(resp);
        // verify addItem was invoked
        verify(entityService, times(1)).addItem(eq(HackerNewsItem.ENTITY_NAME), eq(String.valueOf(HackerNewsItem.ENTITY_VERSION)), any());
    }

    // Minimal stub context returning a mocked request
    private static class CyodaEventContextStub implements com.java_template.common.workflow.CyodaEventContext<EntityProcessorCalculationRequest> {
        @Override
        public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }

        @Override
        public EntityProcessorCalculationRequest getEvent() {
            return Mockito.mock(EntityProcessorCalculationRequest.class);
        }
    }
}
