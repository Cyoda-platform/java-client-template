package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SendEmailProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real Jackson serializer and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Subscriber that will be returned by EntityService
        Subscriber sub = new Subscriber();
        sub.setEmail("jane.doe@example.com");
        sub.setName("Jane Doe");
        sub.setStatus("ACTIVE");
        sub.setInteractionsCount(0);
        sub.setSubscribedAt(OffsetDateTime.now());

        DataPayload subPayload = new DataPayload();
        subPayload.setData(objectMapper.valueToTree(sub));

        when(entityService.getItemsByCondition(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(subPayload)));

        // Instantiate processor under test
        SendEmailProcessor processor = new SendEmailProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid CatFact payload that passes isValid()
        CatFact fact = new CatFact();
        fact.setTechnicalId("tech-1");
        fact.setText("Cats sleep 16 hours a day.");
        fact.setSource("catfacts.example");
        fact.setFetchedAt("2025-09-07T09:00:01Z");
        fact.setValidationStatus("VALID");
        fact.setSendCount(0); // initial
        fact.setEngagementScore(1.0);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("SendEmailProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(fact));
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Verify sendCount was incremented (was 0 -> should become 1 because subscribers present)
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        CatFact resultFact = objectMapper.treeToValue(response.getPayload().getData(), CatFact.class);
        assertNotNull(resultFact);
        assertEquals(1, resultFact.getSendCount().intValue());
    }
}