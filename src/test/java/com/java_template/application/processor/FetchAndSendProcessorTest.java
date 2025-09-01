package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FetchAndSendProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
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

        // Prepare CatFact id that addItem will return
        UUID createdCatFactId = UUID.randomUUID();
        when(entityService.addItem(eq(CatFact.ENTITY_NAME), eq(CatFact.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(createdCatFactId));

        // Prepare subscriber list returned by getItems
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail("user@example.com");
        subscriber.setName("User");
        subscriber.setStatus("ACTIVE");
        subscriber.setInteractionsCount(0);
        subscriber.setSubscribedAt(OffsetDateTime.now());

        JsonNode subscriberJson = objectMapper.valueToTree(subscriber);
        DataPayload subscriberPayload = new DataPayload();
        // set data via reflection because DataPayload has getters only (but typical implementations have setters).
        // However, the example used payload.setData(entityJson) earlier, assume setter exists at runtime.
        subscriberPayload.setData(subscriberJson);
        subscriberPayload.setMeta(objectMapper.createObjectNode().put("entityId", UUID.randomUUID().toString()));

        when(entityService.getItems(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), isNull(), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(subscriberPayload)));

        // Stub updateItem calls to succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor (real), passing mock EntityService
        FetchAndSendProcessor processor = new FetchAndSendProcessor(serializerFactory, entityService, objectMapper);

        // Replace internal httpClient with a test HttpClient that returns a known CatFact response
        HttpClient testHttpClient = new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
                // Build a minimal HttpResponse<String> with a valid JSON body
                @SuppressWarnings("unchecked")
                HttpResponse<T> resp = (HttpResponse<T>) new HttpResponse<String>() {
                    @Override public int statusCode() { return 200; }
                    @Override public String body() { return "{\"fact\":\"A delightful cat fact.\"}"; }
                    @Override public HttpRequest request() { return request; }
                    @Override public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
                    @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a,b)->true); }
                    @Override public URI uri() { return request.uri(); }
                    @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
                    @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
                    @Override public java.util.concurrent.CompletableFuture<java.net.http.HttpHeaders> trailers() { return java.util.concurrent.CompletableFuture.completedFuture(java.net.http.HttpHeaders.of(java.util.Map.of(), (a,b)->true)); }
                };
                return resp;
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                // Not used in this processor; return a completed future for safety
                try {
                    return CompletableFuture.completedFuture(send(request, responseBodyHandler));
                } catch (Exception e) {
                    CompletableFuture<HttpResponse<T>> f = new CompletableFuture<>();
                    f.completeExceptionally(e);
                    return f;
                }
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                // Ignore push promises for this test; delegate to the two-arg sendAsync
                return sendAsync(request, responseBodyHandler);
            }
        };

        // Set private final field httpClient via reflection
        Field httpClientField = FetchAndSendProcessor.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(processor, testHttpClient);

        // Prepare a valid WeeklySendJob payload (must satisfy isValid)
        WeeklySendJob job = new WeeklySendJob();
        job.setCatFactTechnicalId("initial-id");
        job.setCreatedAt(Instant.now().toString());
        job.setRunAt(Instant.now().toString());
        job.setScheduledFor(Instant.now().toString());
        job.setStatus("PENDING");
        job.setErrorMessage(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FetchAndSendProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode returned = response.getPayload().getData();
        assertNotNull(returned);
        // Processor should set status to DISPATCHED on sunny path
        assertEquals("DISPATCHED", returned.get("status").asText());
        // Processor should set catFactTechnicalId to the UUID returned by entityService.addItem
        assertEquals(createdCatFactId.toString(), returned.get("catFactTechnicalId").asText());

        // Verify entityService interactions occurred (optional minimal verification)
        verify(entityService, atLeastOnce()).addItem(eq(CatFact.ENTITY_NAME), eq(CatFact.ENTITY_VERSION), any());
        verify(entityService, atLeastOnce()).getItems(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), isNull(), isNull(), isNull());
    }
}