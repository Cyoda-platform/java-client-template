package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.booking.version_1.Booking;
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
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FetchBookingsProcessorTest {

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
        when(entityService.addItem(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(Booking.class)))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor (will create its own HttpClient)
        FetchBookingsProcessor processor = new FetchBookingsProcessor(serializerFactory, entityService, objectMapper);

        // Replace private final httpClient with a fake implementation that returns controlled responses
        HttpClient fakeClient = new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
                String uri = request.uri().toString();
                if (uri.endsWith("/booking")) {
                    // Respond with a list containing one booking id
                    String body = "[{\"bookingid\":1}]";
                    return new SimpleHttpResponse<>(200, (T) body, request);
                } else if (uri.endsWith("/booking/1")) {
                    // Respond with booking details required by processor and Booking.isValid()
                    String body = "{"
                            + "\"firstname\":\"John\","
                            + "\"lastname\":\"Doe\","
                            + "\"bookingdates\":{\"checkin\":\"2020-01-01\",\"checkout\":\"2020-01-02\"},"
                            + "\"depositpaid\":true,"
                            + "\"totalprice\":100,"
                            + "\"additionalneeds\":\"Breakfast\""
                            + "}";
                    return new SimpleHttpResponse<>(200, (T) body, request);
                } else {
                    return new SimpleHttpResponse<>(404, (T) "", request);
                }
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                // Not used by processor; provide a simple completed future
                try {
                    HttpResponse<T> resp = send(request, responseBodyHandler);
                    return CompletableFuture.completedFuture(resp);
                } catch (Exception e) {
                    CompletableFuture<HttpResponse<T>> f = new CompletableFuture<>();
                    f.completeExceptionally(e);
                    return f;
                }
            }
        };

        // inject fake client via reflection
        Field httpClientField = FetchBookingsProcessor.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(processor, fakeClient);

        // Build a valid ReportJob entity payload that passes isValid()
        ReportJob job = new ReportJob();
        job.setName("Test Job");
        job.setRequestedAt(Instant.now().toString());
        job.setRequestedBy("tester");
        job.setStatus("PENDING");

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FetchBookingsProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Assert that the returned payload contains the updated status "FILTERING"
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        JsonNode returned = response.getPayload().getData();
        assertEquals("FILTERING", returned.get("status").asText());

        // Verify that entityService.addItem was called (persistence attempted)
        verify(entityService, atLeastOnce()).addItem(eq(Booking.ENTITY_NAME), eq(Booking.ENTITY_VERSION), any(Booking.class));
    }

    // Minimal HttpResponse implementation to use in fake client
    private static class SimpleHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;
        private final HttpRequest request;

        SimpleHttpResponse(int statusCode, T body, HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() { return statusCode; }

        @Override
        public HttpRequest request() { return request; }

        @Override
        public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }

        @Override
        public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (s1, s2) -> true); }

        @Override
        public T body() { return body; }

        @Override
        public Optional<SSLSession> sslSession() { return Optional.empty(); }

        @Override
        public URI uri() { return request.uri(); }

        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }

        @Override
        public Optional<java.util.concurrent.CompletableFuture<HttpResponse<T>>> previousResponseAsync() { return Optional.empty(); }

        @Override
        public java.util.Map<String,Object> trailers() { return java.util.Map.of(); }
    }
}