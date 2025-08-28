package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IndexingProcessorTest {

    @Test
    void sunnyDay_indexingProcessor_sets_validated_and_normalizes() {
        // Arrange - ObjectMapper and serializers (real Jackson serializers, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        IndexingProcessor processor = new IndexingProcessor(serializerFactory);

        // Build a Laureate that is valid and will trigger indexing logic:
        // - validated == null to force setting to VALIDATED
        // - born and year provided to compute ageAtAward
        // - bornCountry of length 2 to compute normalizedCountryCode
        Laureate laureate = new Laureate();
        laureate.setId("laureate-1");
        laureate.setFirstname(" John ");
        laureate.setSurname(" Doe ");
        laureate.setCategory("physics");
        laureate.setYear("2000");
        laureate.setBorn("1980-05-01");
        laureate.setBornCountry("se");
        laureate.setValidated(null);
        laureate.setAgeAtAward(null);

        JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("IndexingProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result payload data should not be null");

        // validated should be set to VALIDATED
        assertEquals("VALIDATED", resultData.get("validated").asText());

        // normalizedCountryCode should be uppercase SE (derived from bornCountry of length 2)
        assertEquals("SE", resultData.get("normalizedCountryCode").asText());

        // ageAtAward should be computed as 20 (2000 - 1980)
        assertEquals(20, resultData.get("ageAtAward").asInt());
    }
}