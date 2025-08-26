package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.JacksonCriterionSerializer;
import com.java_template.common.serializer.JacksonProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PersistLaureateProcessorTest {

    @Test
    public void testProcessSunnyDay_NewLaureateIsPersistedAndCreatedAtSet() throws Exception {
        // Arrange
        ObjectMapper om = new ObjectMapper();
        JacksonProcessorSerializer ps = new JacksonProcessorSerializer(om);
        JacksonCriterionSerializer cs = new JacksonCriterionSerializer(om);
        SerializerFactory sf = new SerializerFactory(List.of(ps), List.of(cs));

        // Only EntityService is mocked per requirements
        EntityService es = mock(EntityService.class);

        // Stub getItemsByCondition to return empty array -> indicates no existing record
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> emptyArrayFuture =
            CompletableFuture.completedFuture(om.createArrayNode());
        when(es.getItemsByCondition(anyString(), anyString(), any(), anyBoolean())).thenReturn(emptyArrayFuture);

        // Stub addItem to return a UUID
        CompletableFuture<UUID> addedFuture = CompletableFuture.completedFuture(UUID.randomUUID());
        when(es.addItem(anyString(), anyString(), any())).thenReturn(addedFuture);

        PersistLaureateProcessor underTest = new PersistLaureateProcessor(sf, es, om);

        // Build payload JSON sufficient to pass Laureate.isValid() and to exercise sunny path
        ObjectNode data = om.createObjectNode();
        data.put("id", 123); // business id compared in processor
        data.put("firstname", "Marie");
        data.put("surname", "Curie");
        data.put("year", "1903");
        data.put("category", "Physics");
        data.put("motivation", "Research on radiation");
        data.put("affiliationName", "Sorbonne");
        data.put("affiliationCity", "Paris");
        data.put("affiliationCountry", "France");
        data.put("born", "1867-11-07");
        data.put("died", "1934-07-04");
        data.put("bornCountry", "Poland");
        data.put("bornCountryCode", "PL");
        data.put("gender", "female");
        data.put("age", 66);

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest req = new EntityProcessorCalculationRequest();
        req.setId(UUID.randomUUID().toString());
        req.setRequestId(UUID.randomUUID().toString());
        req.setEntityId("laureate-entity");
        req.setProcessorName(PersistLaureateProcessor.class.getSimpleName());
        req.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public Object getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return req;
            }
        };

        // Act
        EntityProcessorCalculationResponse resp = underTest.process(ctx);

        // Assert
        assertNotNull(resp, "Response should not be null");
        assertTrue(resp.getSuccess(), "Response should indicate success");

        // Inspect payload data for createdAt set by processor in new-record path
        JsonNode out = resp.getPayload().getData();
        assertNotNull(out, "Response payload data should not be null");
        assertTrue(out.has("createdAt"), "CreatedAt should be set on new laureate");
        String createdAt = out.get("createdAt").asText(null);
        assertNotNull(createdAt);
        assertFalse(createdAt.isBlank(), "createdAt should be a non-blank ISO timestamp");
    }
}