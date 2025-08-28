package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FetchPetsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
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
        // Stub addItems to return a single UUID (simulate persisted pets)
        when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), ArgumentMatchers.anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

        // Instantiate processor (real)
        FetchPetsProcessor processor = new FetchPetsProcessor(serializerFactory, entityService, objectMapper);

        // Replace private final httpClient with a fake HttpClient that returns predefined JSON
        HttpClient fakeClient = new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
                String body = "[{\"name\":\"Fido\"}]"; // minimal pet JSON; mapJsonNodeToPet will fill defaults
                @SuppressWarnings("unchecked")
                HttpResponse<T> resp = (HttpResponse<T>) new HttpResponse<String>() {
                    @Override public int statusCode() { return 200; }
                    @Override public String body() { return body; }
                    @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (s1,s2)->true); }
                    @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
                    @Override public HttpRequest request() { return request; }
                    @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                    @Override public URI uri() { return request.uri(); }
                    @Override public Version version() { return Version.HTTP_1_1; }
                };
                return resp;
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                try {
                    HttpResponse<T> resp = this.send(request, responseBodyHandler);
                    return CompletableFuture.completedFuture(resp);
                } catch (IOException | InterruptedException e) {
                    CompletableFuture<HttpResponse<T>> f = new CompletableFuture<>();
                    f.completeExceptionally(e);
                    return f;
                }
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return sendAsync(request, responseBodyHandler);
            }

            @Override
            public Optional<java.util.concurrent.Executor> executor() {
                return Optional.empty();
            }
        };

        // Use reflection to set private final httpClient field
        Field httpClientField = FetchPetsProcessor.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(processor, fakeClient);

        // Build a valid PetImportJob payload that will pass isValid()
        PetImportJob job = new PetImportJob();
        job.setJobId("job-1");
        job.setSourceUrl("http://example.invalid/pets"); // fake URL; our fake client ignores it
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setFetchedCount(0);
        job.setCreatedCount(0);

        // Build request and context
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FetchPetsProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(job));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic success and expected state changes
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload -> data node should reflect updated job (status COMPLETED and createdCount=1, fetchedCount=1)
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // As JSON node
        com.fasterxml.jackson.databind.JsonNode dataNode = response.getPayload().getData();
        assertEquals("COMPLETED", dataNode.get("status").asText());
        assertEquals(1, dataNode.get("createdCount").asInt());
        assertEquals(1, dataNode.get("fetchedCount").asInt());

        // Verify entityService.addItems was called at least once with Pet entity name
        verify(entityService, atLeastOnce()).addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), ArgumentMatchers.anyCollection());
    }
}