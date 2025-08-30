package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

public class DecrementStockProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare product existing in datastore
        String technicalId = UUID.randomUUID().toString();
        Product product = new Product();
        product.setName("Test Product");
        product.setSku("SKU123");
        product.setPrice(10.0);
        product.setQuantityAvailable(5);
        // build DataPayload for product
        DataPayload productPayload = new DataPayload();
        productPayload.setData(objectMapper.valueToTree(product));
        productPayload.setMeta(objectMapper.createObjectNode().put("entityId", technicalId));

        when(entityService.getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        when(entityService.updateItem(eq(UUID.fromString(technicalId)), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Processor under test
        DecrementStockProcessor processor = new DecrementStockProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Order entity that will pass isValid()
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNumber("ORD-1000");
        order.setStatus("NEW");
        order.setCreatedAt("2025-01-01T00:00:00Z");

        Order.GuestContact guest = new Order.GuestContact();
        Order.Address addr = new Order.Address();
        addr.setLine1("123 Test St");
        addr.setCountry("US");
        addr.setPostcode("12345");
        guest.setAddress(addr);
        guest.setEmail("test@example.com");
        guest.setName("Tester");
        order.setGuestContact(guest);

        Order.Totals totals = new Order.Totals();
        totals.setGrand(20.0);
        order.setTotals(totals);

        Order.Line line = new Order.Line();
        line.setSku("SKU123");
        line.setQty(2);
        line.setUnitPrice(10.0);
        order.setLines(java.util.List.of(line));

        // Build request payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DecrementStockProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(order));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Verify updateItem was called and that product quantity was decremented by order line qty (5 -> 3)
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), productCaptor.capture());

        Product updatedProduct = productCaptor.getValue();
        assertNotNull(updatedProduct);
        assertEquals("SKU123", updatedProduct.getSku());
        assertEquals(3, updatedProduct.getQuantityAvailable());
    }
}