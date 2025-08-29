package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateReportRequestProcessorTest {

    @Test
    void sunnyDay_process_sets_fetching_status_and_clears_completedAt() {
        // Arrange - real Jackson serializers + factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService dependency for this processor)
        ValidateReportRequestProcessor processor = new ValidateReportRequestProcessor(serializerFactory);

        // Build a valid ReportJob entity (passes isValid)
        ReportJob reportJob = new ReportJob();
        reportJob.setName("Monthly Report");
        reportJob.setRequestedAt("2025-01-01T00:00:00Z");
        reportJob.setRequestedBy("tester");
        reportJob.setStatus("PENDING"); // initial status that should move to FETCHING

        JsonNode entityJson = objectMapper.valueToTree(reportJob);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateReportRequestProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");
        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode data = response.getPayload().getData();
        assertNotNull(data, "Response payload data should be present");

        // Expect status updated to FETCHING and completedAt cleared (null)
        assertEquals("FETCHING", data.get("status").asText(), "Status should be FETCHING after validation");
        assertTrue(data.get("completedAt") == null || data.get("completedAt").isNull(), "completedAt should be null/cleared");
    }
}