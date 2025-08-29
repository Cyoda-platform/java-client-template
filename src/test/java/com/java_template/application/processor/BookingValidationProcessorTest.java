package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

import static org.junit.jupiter.api.Assertions.*;

public class BookingValidationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor with real serializerFactory (no EntityService required)
        BookingValidationProcessor processor = new BookingValidationProcessor(serializerFactory);

        // Prepare a valid Booking entity that will pass isValid()
        Booking inputBooking = new Booking();
        inputBooking.setBookingId(1);
        inputBooking.setFirstname("  john  ");
        inputBooking.setLastname("doe");
        inputBooking.setCheckin("2025-08-10 ");
        inputBooking.setCheckout(" 2025-08-12");
        inputBooking.setDepositpaid(true);
        inputBooking.setTotalprice(100.0);
        inputBooking.setSource(" web ");
        // persistedAt left null so processor should set it

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName(processor.getClass().getSimpleName());

        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(inputBooking));
        request.setPayload(payload);

        // Minimal context implementation
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "processing should be successful");

        // Extract resulting entity from response payload and verify transformations
        assertNotNull(response.getPayload(), "response payload should not be null");
        assertNotNull(response.getPayload().getData(), "response payload data should not be null");

        Booking result = objectMapper.treeToValue(response.getPayload().getData(), Booking.class);
        assertNotNull(result, "resulting Booking should be deserializable");

        // Name fields should be trimmed and capitalized
        assertEquals("John", result.getFirstname(), "firstname should be capitalized and trimmed");
        assertEquals("Doe", result.getLastname(), "lastname should be capitalized and trimmed");

        // Source trimmed
        assertEquals("web", result.getSource(), "source should be trimmed");

        // Dates normalized (trimmed and kept order)
        assertEquals("2025-08-10", result.getCheckin(), "checkin should be normalized to ISO date");
        assertEquals("2025-08-12", result.getCheckout(), "checkout should be normalized to ISO date");

        // persistedAt should be set by processor when missing
        assertNotNull(result.getPersistedAt());
        assertFalse(result.getPersistedAt().isBlank(), "persistedAt should be set and non-blank");

        // Numeric/boolean fields preserved
        assertEquals(1, result.getBookingId().intValue());
        assertTrue(result.getDepositpaid());
        assertEquals(100.0, result.getTotalprice());
    }
}