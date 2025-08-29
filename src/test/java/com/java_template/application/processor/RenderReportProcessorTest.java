package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.service.EntityService;
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

public class RenderReportProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers & factory
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore unknown props as required
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare two valid Booking entries that will be returned by entityService.getItems(...)
        Booking b1 = new Booking();
        b1.setBookingId(1);
        b1.setFirstname("John");
        b1.setLastname("Doe");
        b1.setCheckin("2023-01-01");
        b1.setCheckout("2023-01-03");
        b1.setDepositpaid(true);
        b1.setTotalprice(100.0);
        b1.setAdditionalneeds("None");
        b1.setPersistedAt("2023-01-01T00:00:00Z");
        b1.setSource("TEST");

        Booking b2 = new Booking();
        b2.setBookingId(2);
        b2.setFirstname("Jane");
        b2.setLastname("Smith");
        b2.setCheckin("2023-01-02");
        b2.setCheckout("2023-01-04");
        b2.setDepositpaid(false);
        b2.setTotalprice(200.0);
        b2.setAdditionalneeds("Breakfast");
        b2.setPersistedAt("2023-01-02T00:00:00Z");
        b2.setSource("TEST");

        DataPayload payload1 = new DataPayload();
        payload1.setData(objectMapper.valueToTree(b1));
        DataPayload payload2 = new DataPayload();
        payload2.setData(objectMapper.valueToTree(b2));

        when(entityService.getItems(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(payload1, payload2)));

        // Stub addItem for Report persistence
        when(entityService.addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        RenderReportProcessor processor = new RenderReportProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid ReportJob entity JSON so that ReportJob.isValid() passes
        ReportJob job = new ReportJob();
        job.setName("Monthly Revenue");
        job.setRequestedAt("2023-08-01T00:00:00Z");
        job.setRequestedBy("tester@example.com");
        job.setStatus("PENDING");
        job.setIncludeCharts(true); // exercise visualization branch

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RenderReportProcessor");
        DataPayload reqPayload = new DataPayload();
        reqPayload.setData(jobJson);
        request.setPayload(reqPayload);

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

        // Inspect returned payload - status should have been updated to COMPLETED and completedAt set
        assertNotNull(response.getPayload());
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);
        assertEquals("COMPLETED", resultData.get("status").asText());
        assertTrue(resultData.hasNonNull("completedAt"));
        assertFalse(resultData.get("completedAt").asText().isBlank());

        // Verify that entityService was used to fetch bookings and persist report
        verify(entityService, atLeastOnce()).getItems(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(), any(), any());
        verify(entityService, atLeastOnce()).addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any());
    }
}