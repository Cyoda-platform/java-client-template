package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UnderperformingDetectorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare Product that will be returned by EntityService.getItem(...)
        UUID productUuid = UUID.randomUUID();
        Product product = new Product();
        product.setProductId(productUuid.toString());
        product.setName("Test Product");
        product.setPrice(100.0);
        product.setMetadata(null);

        DataPayload productPayload = new DataPayload();
        productPayload.setData(objectMapper.valueToTree(product));

        // Stub getItem to return the product payload
        when(entityService.getItem(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(productPayload));

        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(productUuid));

        // Create processor with real serializerFactory and mocked entityService
        UnderperformingDetector processor = new UnderperformingDetector(serializerFactory, entityService, objectMapper);

        // Build a valid SalesRecord that will pass isValid()
        SalesRecord salesRecord = new SalesRecord();
        salesRecord.setRecordId("rec-1");
        salesRecord.setDateSold("2025-08-25T10:00:00Z");
        salesRecord.setProductId(productUuid.toString());
        salesRecord.setQuantity(1); // low volume => underperforming
        salesRecord.setRevenue(10.0);
        salesRecord.setRawSource("{\"source\":\"test\"}");

        // Request payload
        JsonNode salesNode = objectMapper.valueToTree(salesRecord);
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("UnderperformingDetector");
        DataPayload payload = new DataPayload();
        payload.setData(salesNode);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "processing should succeed");

        // Assert returned payload contains the SalesRecord with expected id
        assertNotNull(response.getPayload(), "response payload should not be null");
        assertNotNull(response.getPayload().getData(), "response payload data should not be null");
        JsonNode returnedSales = response.getPayload().getData();
        assertEquals(salesRecord.getRecordId(), returnedSales.get("recordId").asText());

        // Verify that updateItem was invoked to persist product metadata update
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(entityService, atLeastOnce()).updateItem(uuidCaptor.capture(), productCaptor.capture());

        // Inspect captured product metadata to ensure performance tag was applied
        Product updatedProduct = productCaptor.getValue();
        assertNotNull(updatedProduct.getMetadata(), "updated product metadata should not be null");
        JsonNode metadataNode = objectMapper.readTree(updatedProduct.getMetadata());
        assertEquals("UNDERPERFORMING", metadataNode.get("performance").asText());
        assertEquals("UnderperformingDetector", metadataNode.get("lastTaggedBy").asText());
        assertTrue(metadataNode.has("lastTaggedAt"));
    }
}