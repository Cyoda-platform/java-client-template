package com.java_template.application.processor;

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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EnrichProductProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup ObjectMapper (real) and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a SalesRecord that will be returned by entityService to allow price estimation
        SalesRecord sr = new SalesRecord();
        sr.setRecordId("sr-1");
        sr.setDateSold("2025-08-01T00:00:00Z");
        sr.setProductId("p1");
        sr.setQuantity(2);
        sr.setRevenue(30.0);
        sr.setRawSource("{}");
        JsonNode srNode = objectMapper.valueToTree(sr);
        DataPayload srPayload = new DataPayload();
        srPayload.setData(srNode);

        when(entityService.getItemsByCondition(
                eq(SalesRecord.ENTITY_NAME),
                eq(SalesRecord.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(srPayload)));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        EnrichProductProcessor processor = new EnrichProductProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Product payload that exercises trim, metadata category extraction and price estimation
        Product product = new Product();
        product.setProductId("p1");
        product.setName("  Test Product  ");
        product.setCategory(null);
        product.setMetadata("{\"category\":\"Electronics\"}");
        product.setPrice(null); // force estimation path

        JsonNode productNode = objectMapper.valueToTree(product);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("p1");
        request.setProcessorName("EnrichProductProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(productNode);
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

        // Inspect returned payload data for expected sunny-day state changes
        assertNotNull(response.getPayload());
        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned);

        // Name should be trimmed
        assertEquals("Test Product", returned.get("name").asText());

        // Category should be extracted from metadata
        assertEquals("Electronics", returned.get("category").asText());

        // Price should be estimated from SalesRecord (30 / 2 = 15.00)
        assertTrue(returned.has("price"));
        double returnedPrice = returned.get("price").asDouble();
        assertEquals(15.00, returnedPrice, 0.0001);

        // Verify entityService was used for price estimation
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(SalesRecord.ENTITY_NAME), eq(SalesRecord.ENTITY_VERSION), any(), eq(true));
    }
}