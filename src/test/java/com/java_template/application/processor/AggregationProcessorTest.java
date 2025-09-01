package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.pet.version_1.Pet;
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
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AggregationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
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

        // Prepare some Pet items that match criteria
        Pet pet1 = new Pet();
        pet1.setId("pet-1");
        pet1.setName("Fido");
        pet1.setSpecies("Dog");
        pet1.setStatus("AVAILABLE");
        pet1.setAge(3);

        Pet pet2 = new Pet();
        pet2.setId("pet-2");
        pet2.setName("Spot");
        pet2.setSpecies("Dog");
        pet2.setStatus("AVAILABLE");
        pet2.setAge(4);

        DataPayload dp1 = new DataPayload();
        dp1.setData(objectMapper.valueToTree(pet1));
        DataPayload dp2 = new DataPayload();
        dp2.setData(objectMapper.valueToTree(pet2));

        List<DataPayload> returned = new ArrayList<>();
        returned.add(dp1);
        returned.add(dp2);

        when(entityService.getItemsByCondition(anyString(), any(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(returned));

        // Build a valid AdoptionJob that will pass validation
        AdoptionJob job = new AdoptionJob();
        job.setId("job-1");
        job.setOwnerId("owner-1");
        job.setCreatedAt("2020-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setCriteria("{\"species\":\"Dog\",\"ageMax\":5}");
        job.setResultCount(0);
        job.setResultsPreview(new ArrayList<>());

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AggregationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Instantiate processor with real serializers and mocked EntityService
        AggregationProcessor processor = new AggregationProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload data for expected sunny-day changes
        assertNotNull(response.getPayload());
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData);
        assertEquals("COMPLETED", outData.get("status").asText());
        assertEquals(2, outData.get("resultCount").asInt());

        JsonNode preview = outData.get("resultsPreview");
        assertTrue(preview.isArray());
        List<String> ids = new ArrayList<>();
        preview.forEach(n -> ids.add(n.asText()));
        assertTrue(ids.contains("pet-1"));
        assertTrue(ids.contains("pet-2"));

        // Verify the processor invoked the EntityService to search for Pets
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
    }
}