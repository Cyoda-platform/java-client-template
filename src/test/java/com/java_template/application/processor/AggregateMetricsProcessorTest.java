package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AggregateMetricsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only the EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Booking that will be returned by entityService.getItems(...)
        Booking booking = new Booking();
        booking.setBookingId(1);
        booking.setFirstname("John");
        booking.setLastname("Doe");
        booking.setCheckin("2023-01-01");
        booking.setCheckout("2023-01-02");
        booking.setDepositpaid(true);
        booking.setTotalprice(100.0);
        booking.setSource("TEST");
        booking.setPersistedAt("2023-01-01T00:00:00Z");
        booking.setAdditionalneeds("None");

        DataPayload bookingPayload = new DataPayload();
        bookingPayload.setData(objectMapper.valueToTree(booking));

        when(entityService.getItems(
                eq(Booking.ENTITY_NAME),
                eq(Booking.ENTITY_VERSION),
                isNull(), isNull(), isNull()
        )).thenReturn(CompletableFuture.completedFuture(List.of(bookingPayload)));

        // Stub addItem for Report persistence
        when(entityService.addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Construct processor instance (use real serializerFactory and objectMapper, mocked entityService)
        AggregateMetricsProcessor processor = new AggregateMetricsProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid ReportJob request payload (no filters to keep validation simple)
        ReportJob job = new ReportJob();
        job.setName("Monthly Report");
        job.setRequestedAt("2023-08-01T00:00:00Z");
        job.setRequestedBy("tester");
        job.setStatus("PENDING"); // initial status; processor should update it

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AggregateMetricsProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(job));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload data for expected state change (status should be GENERATING because one booking was found)
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        String resultingStatus = response.getPayload().getData().get("status").asText();
        assertEquals("GENERATING", resultingStatus);

        // Verify that EntityService methods were used as expected
        verify(entityService, atLeastOnce()).getItems(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), isNull(), isNull(), isNull());
        verify(entityService, atLeastOnce()).addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any());
    }
}