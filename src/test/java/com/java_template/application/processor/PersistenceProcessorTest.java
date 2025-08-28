package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import static org.mockito.Mockito.*;

public class PersistenceProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService (only allowed mock)
        EntityService entityService = mock(EntityService.class);

        // Prepare an existing laureate payload to be returned by the EntityService for deduplication merge
        Laureate existing = new Laureate();
        existing.setId(1);
        existing.setNormalizedCountryCode("US");
        existing.setDerived_ageAtAward(50);
        existing.setAffiliation_name("Uni");
        existing.setAffiliation_city("City");
        existing.setAffiliation_country("Country");

        DataPayload existingPayload = new DataPayload();
        existingPayload.setData(objectMapper.valueToTree(existing));

        when(entityService.getItemsByCondition(
                anyString(), anyInt() /* version */,
                any(), anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(List.of(existingPayload)));

        // Instantiate processor under test
        PersistenceProcessor processor = new PersistenceProcessor(serializerFactory, entityService, objectMapper);

        // Build incoming Laureate JSON that is valid but missing some derived/normalized/affiliation fields
        Laureate incoming = new Laureate();
        incoming.setId(1);
        incoming.setFirstname("John");
        incoming.setSurname("Doe");
        incoming.setCategory("Physics");
        incoming.setYear("2000");
        // leave born, borncountry, borncountrycode, normalizedCountryCode, affiliation_* null so merge can apply
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(objectMapper.valueToTree(incoming));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistenceProcessor");
        request.setPayload(requestPayload);

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

        // Inspect resulting payload data for merged/enriched values (sunny-day expectations)
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // normalizedCountryCode should be merged from existing
        assertEquals("US", out.get("normalizedCountryCode").asText());

        // derived_ageAtAward should be merged from existing (incoming had no born, so couldn't compute)
        assertEquals(50, out.get("derived_ageAtAward").asInt());

        // affiliation fields should be merged from existing
        assertEquals("Uni", out.get("affiliation_name").asText());
        assertEquals("City", out.get("affiliation_city").asText());
        assertEquals("Country", out.get("affiliation_country").asText());
    }
}