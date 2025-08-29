package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FilterBookingsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);

        // Prepare a persisted Booking that should be returned by EntityService.getItems
        Booking persisted = new Booking();
        persisted.setBookingId(123);
        persisted.setFirstname("John");
        persisted.setLastname("Doe");
        persisted.setCheckin("2023-01-01");
        persisted.setCheckout("2023-01-03");
        persisted.setDepositpaid(true);
        persisted.setTotalprice(150.0);
        persisted.setSource("OTA");
        persisted.setPersistedAt("2023-01-01T10:00:00Z");

        ObjectNode bookingJson = objectMapper.valueToTree(persisted);

        DataPayload persistedPayload = new DataPayload();
        persistedPayload.setData(bookingJson);

        when(entityService.getItems(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(persistedPayload)));

        // Create processor with mocked EntityService
        FilterBookingsProcessor processor = new FilterBookingsProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid ReportJob (no filters => valid and will cause processor to include all bookings)
        ReportJob requestEntity = new ReportJob();
        requestEntity.setName("Monthly Report");
        requestEntity.setRequestedAt("2023-01-01T09:00:00Z");
        requestEntity.setRequestedBy("tester");
        requestEntity.setStatus("NEW");
        // leave filters null to simplify validation

        ObjectNode reportJobJson = objectMapper.valueToTree(requestEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName(FilterBookingsProcessor.class.getSimpleName());
        DataPayload payload = new DataPayload();
        payload.setData(reportJobJson);
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
        assertNotNull(response.getPayload().getData());
        // The processor sets status to "AGGREGATING" on happy path
        assertEquals("AGGREGATING", response.getPayload().getData().get("status").asText());

        // Verify we queried persisted bookings
        verify(entityService, atLeastOnce()).getItems(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(), any(), any());
    }
}