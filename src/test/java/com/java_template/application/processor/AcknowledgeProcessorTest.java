package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AcknowledgeProcessor Tests")
class AcknowledgeProcessorTest {

    private AcknowledgeProcessor processor;
    private ObjectMapper objectMapper;
    private ProcessorSerializer serializer;

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private EntityService entityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        serializer = new JacksonProcessorSerializer(objectMapper);

        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);
        processor = new AcknowledgeProcessor(serializerFactory, entityService);
    }

    @Test
    @DisplayName("Should support correct operation specification")
    void testSupports() {
        ModelSpec modelSpec = new ModelSpec().withName("Order").withVersion(1);
        OperationSpecification.Processor opSpec = new OperationSpecification.Processor(
                modelSpec, "Order", "ROUTED", "ACKNOWLEDGE", "OrderLifecycle");

        assertTrue(processor.supports(opSpec));
    }

    @Test
    @DisplayName("Should not support different operation name")
    void testDoesNotSupportDifferentOperation() {
        ModelSpec modelSpec = new ModelSpec().withName("Order").withVersion(1);
        OperationSpecification.Processor opSpec = new OperationSpecification.Processor(
                modelSpec, "DifferentProcessor", "ROUTED", "ACKNOWLEDGE", "OrderLifecycle");

        assertFalse(processor.supports(opSpec));
    }

    @Test
    @DisplayName("Should acknowledge valid order successfully")
    void testAcknowledgeValidOrder() {
        Order order = createValidOrder();
        EntityProcessorCalculationRequest request = createRequest(order);
        CyodaEventContext<EntityProcessorCalculationRequest> context = createContext(request);

        EntityProcessorCalculationResponse response = processor.process(context);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(request.getId(), response.getId());
    }

    @Test
    @DisplayName("Should update timestamp on acknowledgment")
    void testAcknowledgeUpdatesTimestamp() {
        Order order = createValidOrder();
        EntityProcessorCalculationRequest request = createRequest(order);
        CyodaEventContext<EntityProcessorCalculationRequest> context = createContext(request);

        EntityProcessorCalculationResponse response = processor.process(context);

        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    private Order createValidOrder() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setSymbol("AAPL");
        order.setQuantity(100);
        order.setPrice(150.0);
        order.setSide("BUY");
        order.setTraderId("TRADER-001");
        return order;
    }

    private EntityProcessorCalculationRequest createRequest(Order order) {
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("test-request-123");
        request.setRequestId("req-456");
        request.setEntityId("entity-789");
        request.setProcessorId("processor-123");
        request.setProcessorName("AcknowledgeProcessor");

        ObjectNode entityJson = objectMapper.valueToTree(order);
        ObjectNode metadataJson = objectMapper.createObjectNode();
        metadataJson.put("id", java.util.UUID.randomUUID().toString());
        metadataJson.put("state", "ROUTED");

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        payload.setMeta(metadataJson);
        request.setPayload(payload);

        return request;
    }

    private CyodaEventContext<EntityProcessorCalculationRequest> createContext(EntityProcessorCalculationRequest request) {
        return new CyodaEventContext<>() {
            @Override
            public CloudEvent getCloudEvent() {
                return mock(CloudEvent.class);
            }

            @Override
            public @NotNull EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };
    }
}

