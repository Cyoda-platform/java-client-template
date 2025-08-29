package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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

public class ValidateSourceProcessorTest {

    @Test
    void sunnyDay_validateSourceProcessor_marksFailedWhenSourceUnavailable() {
        // Setup ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService needed)
        ValidateSourceProcessor processor = new ValidateSourceProcessor(serializerFactory);

        // Build a valid IngestionJob entity JSON that passes isValid()
        IngestionJob job = new IngestionJob();
        job.setJobId("job-123");
        // Use a URL that is syntactically valid but will likely be unreachable in unit test environment
        job.setSourceUrl("http://nonexistent.invalid.example");
        job.setStatus("PENDING"); // initial status must be non-blank to satisfy isValid()
        // Provide a valid cron expression (5 parts) so scheduleValid == true
        job.setScheduleCron("0 0 * * *");

        JsonNode entityJson = objectMapper.valueToTree(job);

        // Build request with DataPayload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateSourceProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
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
        assertTrue(response.getSuccess(), "Processor response should indicate success");

        // Inspect payload: processor should set status to FAILED when source is unavailable
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should not be null");
        assertEquals("FAILED", responseData.get("status").asText(), "IngestionJob should be marked FAILED when source unavailable");
    }
}