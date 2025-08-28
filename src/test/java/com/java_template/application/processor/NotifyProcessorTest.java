package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifyProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson setup
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Owner with a contactEmail so NotifyProcessor will count it as notified
        Owner owner = new Owner();
        owner.setId("owner-1");
        owner.setName("Jane Doe");
        owner.setContactEmail("jane.doe@example.com");
        JsonNode ownerJson = objectMapper.valueToTree(owner);
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(ownerJson);

        when(entityService.getItems(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(ownerPayload)));

        // Instantiate processor (no Spring)
        NotifyProcessor processor = new NotifyProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid PetImportJob payload that passes isValid()
        PetImportJob job = new PetImportJob();
        job.setRequestId("req-1");
        job.setRequestedAt("2020-01-01T00:00:00Z");
        job.setSourceUrl("http://example.com/import.csv");
        job.setStatus("COMPLETED");
        job.setImportedCount(10);
        job.setErrors(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifyProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic sunny-day assertions
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result data must be present");

        // The processor appends a notifications summary into the job.errors field.
        assertTrue(resultData.has("errors"), "Result should contain 'errors' field");
        String errorsField = resultData.get("errors").asText();
        assertTrue(errorsField.contains("notificationsSent=1"), "errors should contain notificationsSent=1 indicating one owner notified");
    }
}