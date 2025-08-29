package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class IndexBookingProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - setup real Jackson serializers and serializer factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        IndexBookingProcessor processor = new IndexBookingProcessor(serializerFactory);

        // Create a valid Booking entity JSON that will pass validate() before processing
        Booking booking = new Booking();
        booking.setBookingId(1);
        booking.setFirstname("  John  "); // will be trimmed by processor
        booking.setLastname("Doe");
        booking.setCheckin("2020-01-01");
        booking.setCheckout("2020-01-02");
        booking.setSource("TestSource"); // must be present for isValid() to pass
        booking.setDepositpaid(Boolean.TRUE);
        booking.setTotalprice(123.456); // will be rounded to 2 decimals by processor

        JsonNode entityJson = objectMapper.valueToTree(booking);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("IndexBookingProcessor");
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

        JsonNode data = response.getPayload().getData();
        assertNotNull(data);

        // firstname should be trimmed by processor logic
        assertEquals("John", data.get("firstname").asText());

        // persistedAt should be set by processor (was null/absent initially)
        assertTrue(data.hasNonNull("persistedAt"));
        assertFalse(data.get("persistedAt").asText().isBlank());

        // totalprice should be rounded to 2 decimals (123.456 -> 123.46)
        assertEquals(123.46, data.get("totalprice").asDouble(), 0.0001);
    }
}