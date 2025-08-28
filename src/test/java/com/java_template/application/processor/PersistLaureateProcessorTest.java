package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

public class PersistLaureateProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        PersistLaureateProcessor processor = new PersistLaureateProcessor(serializerFactory);

        // Prepare a valid Laureate entity that will pass isValid() and exercise processor logic:
        // - firstname/surname/category contain extra spaces to verify trimming
        // - born and year provided so ageAtAward will be computed
        // - bornCountryCode provided so normalizedCountryCode will be derived
        Laureate input = new Laureate();
        input.setId(853);
        input.setFirstname("  Akira  ");
        input.setSurname("  Suzuki ");
        input.setCategory(" Chemistry ");
        input.setYear("1980");
        input.setBorn("1940-09-12");
        input.setBornCountryCode("jp");
        // ageAtAward left null to trigger computation
        input.setAgeAtAward(null);

        JsonNode entityJson = objectMapper.valueToTree(input);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistLaureateProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode outNode = response.getPayload().getData();
        assertNotNull(outNode);

        Laureate out = objectMapper.treeToValue(outNode, Laureate.class);
        // ageAtAward should be computed as 40 (1980 - 1940)
        assertNotNull(out.getAgeAtAward());
        assertEquals(40, out.getAgeAtAward().intValue());
        // normalizedCountryCode should be set to upper-case bornCountryCode
        assertEquals("JP", out.getNormalizedCountryCode());
        // firstname and surname should be trimmed
        assertEquals("Akira", out.getFirstname());
        assertEquals("Suzuki", out.getSurname());
        // lastUpdatedAt should be set by processor (non-null and non-blank)
        assertNotNull(out.getLastUpdatedAt());
        assertFalse(out.getLastUpdatedAt().isBlank());
    }
}