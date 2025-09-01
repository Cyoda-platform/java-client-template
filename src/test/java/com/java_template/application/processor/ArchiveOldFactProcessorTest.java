package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ArchiveOldFactProcessorTest {

    @Test
    void sunnyDay_archive_old_fact_test() {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        ArchiveOldFactProcessor processor = new ArchiveOldFactProcessor(serializerFactory);

        // Build a CatFact that is older than retention (set fetchedAt to 40 days ago)
        CatFact catFact = new CatFact();
        catFact.setTechnicalId(UUID.randomUUID().toString());
        catFact.setText("Cats sleep a lot");
        catFact.setSource("catfact.ninja");
        catFact.setFetchedAt(OffsetDateTime.now().minusDays(40).toString());
        catFact.setSendCount(0);
        catFact.setEngagementScore(1.0);
        catFact.setValidationStatus("VALID"); // not archived yet

        JsonNode entityJson = objectMapper.valueToTree(catFact);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ArchiveOldFactProcessor");
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

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should be present");

        // validationStatus should have been set to ARCHIVED for old CatFact
        JsonNode validationStatusNode = responseData.get("validationStatus");
        assertNotNull(validationStatusNode, "validationStatus should be present in response data");
        assertEquals("ARCHIVED", validationStatusNode.asText(), "CatFact should be archived in sunny path");
    }
}