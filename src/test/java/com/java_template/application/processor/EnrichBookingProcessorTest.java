package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EnrichBookingProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Create processor (does not require EntityService)
        EnrichBookingProcessor processor = new EnrichBookingProcessor(serializerFactory);

        // Create a minimal valid Booking that will exercise enrichment logic:
        // - firstname/lastname will be trimmed and capitalized
        // - totalprice will be rounded to 2 decimal places
        // - persistedAt is null and should be set by the processor
        // - source contains surrounding whitespace and should be trimmed
        Booking booking = new Booking();
        booking.setBookingId(123);
        booking.setFirstname("  john ");
        booking.setLastname("doe smith  ");
        booking.setCheckin("2025-08-01");
        booking.setCheckout("2025-08-05");
        booking.setDepositpaid(true);
        booking.setTotalprice(12.3456);
        booking.setSource("  customSource  ");
        booking.setPersistedAt(null); // expect processor to set this

        JsonNode entityJson = objectMapper.valueToTree(booking);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnrichBookingProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);

        // firstname should be capitalized and trimmed -> "John"
        assertEquals("John", resultData.get("firstname").asText());

        // lastname should be capitalized each word and trimmed -> "Doe Smith"
        assertEquals("Doe Smith", resultData.get("lastname").asText());

        // source should be trimmed -> "customSource"
        assertEquals("customSource", resultData.get("source").asText());

        // totalprice rounded to two decimals -> 12.35
        assertEquals(12.35, resultData.get("totalprice").asDouble(), 0.0001);

        // persistedAt should have been set (not null/blank)
        assertTrue(resultData.hasNonNull("persistedAt"));
        assertFalse(resultData.get("persistedAt").asText().isBlank());
    }
}