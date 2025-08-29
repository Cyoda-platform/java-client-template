package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

public class NormalizeAndChunkTest {

    @Test
    void sunnyDay_normalize_and_chunk() {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        NormalizeAndChunk processor = new NormalizeAndChunk(serializerFactory);

        // Create a valid PostVersion entity that passes isValid()
        PostVersion pv = new PostVersion();
        pv.setVersion_id("v1");
        pv.setPost_id("p1");
        pv.setCreated_at("2025-01-01T00:00:00Z");
        // Provide HTML rich content to exercise normalization and chunking
        pv.setContent_rich("Hello <b>World</b>&nbsp;!");

        JsonNode entityJson = objectMapper.valueToTree(pv);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NormalizeAndChunk");
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
        assertTrue(response.getSuccess(), "Response should indicate success");

        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response payload data should not be null");

        // normalized_text expected after naive HTML removal and whitespace collapse
        assertEquals("Hello World !", responseData.get("normalized_text").asText());

        // chunks_meta must be an array with at least one chunk and text matching normalized_text
        JsonNode chunksMeta = responseData.get("chunks_meta");
        assertTrue(chunksMeta.isArray(), "chunks_meta should be an array");
        assertTrue(chunksMeta.size() >= 1, "At least one chunk should be present");
        JsonNode firstChunk = chunksMeta.get(0);
        assertNotNull(firstChunk.get("text"), "Chunk should contain text field");
        assertEquals("Hello World !", firstChunk.get("text").asText());
    }
}