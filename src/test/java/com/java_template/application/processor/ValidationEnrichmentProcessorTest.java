package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.JacksonCriterionSerializer;
import com.java_template.common.serializer.JacksonProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationEnrichmentProcessorTest {

    @Test
    public void testProcess_sunnyDay_enrichesAndValidatesEntity() {
        // Arrange
        ObjectMapper om = new ObjectMapper();
        JacksonProcessorSerializer ps = new JacksonProcessorSerializer(om);
        JacksonCriterionSerializer cs = new JacksonCriterionSerializer(om);
        SerializerFactory sf = new SerializerFactory(java.util.List.of(ps), java.util.List.of(cs));

        ValidationEnrichmentProcessor underTest = new ValidationEnrichmentProcessor(sf);

        ObjectNode data = om.createObjectNode();
        // Populate fields expected by Laureate.isValid() and by enrichment logic
        data.put("id", "laureate-1");
        data.put("firstname", "  John  ");
        data.put("surname", "  Doe  ");
        // born used to compute age
        data.put("born", "1970-01-01");
        // no died -> age computed to now
        data.putNull("died");
        // country code to be normalized to uppercase
        data.put("bornCountryCode", "us");
        // gender to be normalized to lowercase and trimmed
        data.put("gender", " Male ");
        // year in 4-digit format
        data.put("year", "2000");
        // affiliation fields to be trimmed
        data.put("affiliationName", " University ");
        data.put("affiliationCity", " City ");
        data.put("affiliationCountry", " Country ");
        // bornCountry to help fallback normalization (not needed since bornCountryCode provided)
        data.put("bornCountry", "United States");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest req = new EntityProcessorCalculationRequest();
        req.setId("req-1");
        req.setRequestId("r-1");
        req.setEntityId("laureate-1");
        req.setProcessorName("ValidationEnrichmentProcessor");
        req.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public Object getCloudEvent() { return null; }

            @Override
            public EntityProcessorCalculationRequest getEvent() { return req; }
        };

        // Act
        EntityProcessorCalculationResponse resp = underTest.process(ctx);

        // Assert
        assertNotNull(resp, "response should not be null");
        assertTrue(resp.getSuccess(), "response should be successful");

        JsonNode out = (JsonNode) resp.getPayload().getData();
        assertNotNull(out, "response payload data should not be null");

        // gender should be normalized to lowercase and trimmed
        assertEquals("male", out.get("gender").asText());

        // country code normalized to uppercase
        assertEquals("US", out.get("normalizedCountryCode").asText());

        // age should be an integer and positive (computed from born to now)
        assertTrue(out.has("age") && out.get("age").isInt(), "age should be present as an integer");
        int age = out.get("age").asInt();
        int expectedMinAge = 1;
        int expectedMaxAge = LocalDate.now().getYear() - 1900 + 200; // generous upper bound
        assertTrue(age >= expectedMinAge && age < expectedMaxAge, "age should be a reasonable positive value");

        // trimmed fields should have no surrounding spaces
        assertEquals("John", out.get("firstname").asText());
        assertEquals("Doe", out.get("surname").asText());
        assertEquals("University", out.get("affiliationName").asText());
    }
}