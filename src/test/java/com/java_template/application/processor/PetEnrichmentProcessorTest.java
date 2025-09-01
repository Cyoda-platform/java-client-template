package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PetEnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService may be mocked per requirements
        EntityService entityService = mock(EntityService.class);

        // Construct processor (real)
        PetEnrichmentProcessor processor = new PetEnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Pet entity that will pass isValid() and exercise normalization/cleaning logic
        Pet inputPet = new Pet();
        inputPet.setId("pet-1");
        inputPet.setName(" Fido ");
        inputPet.setSpecies(" Cat ");
        inputPet.setStatus("available");
        inputPet.setBreed("golden retriever");
        inputPet.setPhotoUrls(List.of(" http://example.com/photo1.jpg ", " "));
        inputPet.setVaccinations(List.of(" rabies ", "", null));
        // age, sourceUrl left null to avoid fetchPhotosFromSource being called

        JsonNode entityJson = objectMapper.valueToTree(inputPet);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PetEnrichmentProcessor");
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

        // Assert basic response
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should be successful");

        // Extract resulting entity from payload and verify normalization/cleaning occurred
        JsonNode resultNode = response.getPayload().getData();
        assertNotNull(resultNode, "response payload data should not be null");

        Pet resultPet = objectMapper.treeToValue(resultNode, Pet.class);
        assertNotNull(resultPet);

        // Species should be normalized to lower-case trimmed
        assertEquals("cat", resultPet.getSpecies());

        // Breed should be title-cased
        assertEquals("Golden Retriever", resultPet.getBreed());

        // Name should be trimmed
        assertEquals("Fido", resultPet.getName());

        // Vaccinations should have blanks/nulls removed and trimmed
        assertNotNull(resultPet.getVaccinations());
        assertEquals(1, resultPet.getVaccinations().size());
        assertEquals("rabies", resultPet.getVaccinations().get(0));

        // Photo URLs should be cleaned and trimmed
        assertNotNull(resultPet.getPhotoUrls());
        assertEquals(1, resultPet.getPhotoUrls().size());
        assertEquals("http://example.com/photo1.jpg", resultPet.getPhotoUrls().get(0));
    }
}