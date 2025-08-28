package com.java_template.application.processor;

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

public class EnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor with real serializerFactory (no mocks required)
        EnrichmentProcessor processor = new EnrichmentProcessor(serializerFactory);

        // Prepare a valid Laureate entity JSON that will pass isValid()
        Laureate laureate = new Laureate();
        laureate.setId("l1");
        laureate.setFirstname("Ada");
        laureate.setSurname("Lovelace");
        laureate.setCategory("computing");
        // Born and year set so ageAtAward can be computed: born 1970, award year 2000 -> age 30
        laureate.setBorn("1970-05-20");
        laureate.setYear("2000");
        // Use a country name present in the mapping to assert normalized code
        laureate.setBornCountry("Sweden");

        // Convert to JsonNode for DataPayload
        com.fasterxml.jackson.databind.JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId("ent-1");
        request.setProcessorName("EnrichmentProcessor");
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

        // Assert basic success
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should be successful");

        // Inspect returned payload data and validate the enrichment changes
        assertNotNull(response.getPayload(), "Response payload should not be null");
        com.fasterxml.jackson.databind.JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned payload data should not be null");

        // Convert returned JSON back to Laureate to check computed fields
        Laureate resultEntity = objectMapper.treeToValue(returnedData, Laureate.class);
        assertNotNull(resultEntity, "Deserialized result entity should not be null");

        // Age should be computed: 2000 - 1970 = 30
        assertEquals(Integer.valueOf(30), resultEntity.getAgeAtAward(), "Expected ageAtAward to be computed as 30");

        // bornCountry "Sweden" is mapped to "SE" in processor mapping
        assertEquals("SE", resultEntity.getNormalizedCountryCode(), "Expected normalized country code to be 'SE'");
    }
}