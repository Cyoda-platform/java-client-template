package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.CriterionSerializer;
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

public class DedupUpsertProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirements
        EntityService entityService = mock(EntityService.class);
        // Sunny path: no existing items found
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        DedupUpsertProcessor processor = new DedupUpsertProcessor(serializerFactory, entityService, objectMapper);

        // Build minimal valid Laureate payload to satisfy isValid()
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        data.put("firstname", "John");
        data.put("surname", "Doe");
        data.put("category", "Physics");
        data.put("year", "2000");
        // Provide born and borncountrycode to trigger enrichment and normalization
        data.put("born", "1950-01-01");
        data.put("borncountrycode", "us");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DedupUpsertProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect resulting payload for enrichment and normalization
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        var out = response.getPayload().getData();
        // enrichedAgeAtAward = 2000 - 1950 = 50
        assertTrue(out.has("enrichedAgeAtAward"));
        assertEquals(50, out.get("enrichedAgeAtAward").asInt());

        // normalizedCountryCode should be upper-cased "US"
        assertTrue(out.has("normalizedCountryCode"));
        assertEquals("US", out.get("normalizedCountryCode").asText());

        // validationStatus should be set to OK in sunny path
        assertTrue(out.has("validationStatus"));
        assertEquals("OK", out.get("validationStatus").asText());

        // Verify EntityService was used to search for duplicates
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), any(), eq(true));
    }
}