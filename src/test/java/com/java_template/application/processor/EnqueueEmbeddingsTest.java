package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.postversion.version_1.PostVersion;
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

public class EnqueueEmbeddingsTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        EnqueueEmbeddings processor = new EnqueueEmbeddings(serializerFactory);

        // Build a minimal valid PostVersion JSON payload.
        // Ensure required fields version_id, post_id, created_at are present.
        // Provide empty chunks_meta to avoid HTTP calls in the processor logic.
        JsonNode postVersionJson = objectMapper.createObjectNode()
                .put("version_id", "v1")
                .put("post_id", "p1")
                .put("created_at", "2025-01-01T00:00:00Z")
                .set("chunks_meta", objectMapper.createArrayNode()); // empty -> processor will skip embedding calls

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnqueueEmbeddings");
        DataPayload payload = new DataPayload();
        payload.setData(postVersionJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic sunny-day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Inspect returned payload data for expected stable fields
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response payload data should not be null");
        assertEquals("v1", responseData.get("version_id").asText(), "version_id should be preserved");
        assertEquals("p1", responseData.get("post_id").asText(), "post_id should be preserved");

        // Since chunks_meta was empty, processor should not have enqueued embeddings and embeddings_ref should be absent or null
        assertTrue(!responseData.has("embeddings_ref") || responseData.get("embeddings_ref").isNull(),
                "embeddings_ref should be absent or null for empty chunks_meta");
    }
}