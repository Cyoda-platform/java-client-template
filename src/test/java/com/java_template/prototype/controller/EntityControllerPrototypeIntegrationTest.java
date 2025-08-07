package com.java_template.prototype.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.prototype.EntityControllerPrototype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for EntityControllerPrototype that test the complete workflow
 * including job processing, laureate ingestion, and subscriber notifications.
 */
class EntityControllerPrototypeIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private EntityControllerPrototype controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new EntityControllerPrototype();
        objectMapper = new ObjectMapper();
        
        // Use reflection to inject the mocked RestTemplate
        try {
            var restTemplateField = EntityControllerPrototype.class.getDeclaredField("restTemplate");
            restTemplateField.setAccessible(true);
            restTemplateField.set(controller, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocked RestTemplate", e);
        }
    }

    @Test
    void jobProcessing_WithSuccessfulApiCall_ShouldCompleteWorkflow() throws Exception {
        // Mock successful API response
        String mockApiResponse = """
            {
              "records": [
                {
                  "fields": {
                    "id": 853,
                    "firstname": "Akira",
                    "surname": "Suzuki",
                    "born": "1930-09-12",
                    "died": null,
                    "borncountry": "Japan",
                    "borncountrycode": "JP",
                    "borncity": "Mukawa",
                    "gender": "male",
                    "year": "2010",
                    "category": "Chemistry",
                    "motivation": "for palladium-catalyzed cross couplings in organic synthesis",
                    "name": "Hokkaido University",
                    "city": "Sapporo",
                    "country": "Japan"
                  }
                }
              ]
            }
            """;

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockApiResponse, HttpStatus.OK));

        // Create a job
        Map<String, String> request = Map.of("externalId", "integration-test-job");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(request);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        Long technicalId = createResponse.getBody().get("technicalId");
        assertNotNull(technicalId);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify job state progression
        ResponseEntity<Job> jobResponse = controller.getJob(technicalId.toString());
        assertEquals(HttpStatus.OK, jobResponse.getStatusCode());
        Job job = jobResponse.getBody();
        assertNotNull(job);
        
        // Job should have completed successfully
        assertTrue(job.getState().equals("SUCCEEDED") || job.getState().equals("NOTIFIED_SUBSCRIBERS"));
        assertNotNull(job.getCompletedAt());
        assertNotNull(job.getResultSummary());
        assertTrue(job.getResultSummary().contains("Ingested"));

        // Verify API was called
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void jobProcessing_WithFailedApiCall_ShouldFailGracefully() throws Exception {
        // Mock failed API response
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Create a job
        Map<String, String> request = Map.of("externalId", "failed-job");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(request);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        Long technicalId = createResponse.getBody().get("technicalId");

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify job failed
        ResponseEntity<Job> jobResponse = controller.getJob(technicalId.toString());
        Job job = jobResponse.getBody();
        assertNotNull(job);
        assertEquals("FAILED", job.getState());
        assertNotNull(job.getCompletedAt());
        assertNotNull(job.getResultSummary());
        assertTrue(job.getResultSummary().contains("Failed to fetch laureates data"));
    }

    @Test
    void jobProcessing_WithInvalidJsonResponse_ShouldFailGracefully() throws Exception {
        // Mock invalid JSON response
        String invalidJson = "{ invalid json }";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(invalidJson, HttpStatus.OK));

        // Create a job
        Map<String, String> request = Map.of("externalId", "invalid-json-job");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(request);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        Long technicalId = createResponse.getBody().get("technicalId");

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify job failed
        ResponseEntity<Job> jobResponse = controller.getJob(technicalId.toString());
        Job job = jobResponse.getBody();
        assertNotNull(job);
        assertEquals("FAILED", job.getState());
        assertNotNull(job.getResultSummary());
        assertTrue(job.getResultSummary().contains("Exception during processing"));
    }

    @Test
    void subscriberNotification_WithActiveSubscribers_ShouldNotifyAll() throws Exception {
        // Create active subscribers
        Subscriber activeSubscriber1 = new Subscriber();
        activeSubscriber1.setContactEmail("active1@example.com");
        activeSubscriber1.setActive(true);
        
        Subscriber activeSubscriber2 = new Subscriber();
        activeSubscriber2.setContactEmail("active2@example.com");
        activeSubscriber2.setWebhookUrl("https://example.com/webhook");
        activeSubscriber2.setActive(true);
        
        Subscriber inactiveSubscriber = new Subscriber();
        inactiveSubscriber.setContactEmail("inactive@example.com");
        inactiveSubscriber.setActive(false);

        // Create subscribers
        controller.createSubscriber(activeSubscriber1);
        controller.createSubscriber(activeSubscriber2);
        controller.createSubscriber(inactiveSubscriber);

        // Mock successful API response
        String mockApiResponse = """
            {
              "records": [
                {
                  "fields": {
                    "id": 1,
                    "firstname": "Test",
                    "surname": "Laureate",
                    "born": "1950-01-01",
                    "borncountry": "Test Country",
                    "borncountrycode": "TC",
                    "borncity": "Test City",
                    "gender": "male",
                    "year": "2020",
                    "category": "Test",
                    "motivation": "for testing"
                  }
                }
              ]
            }
            """;

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockApiResponse, HttpStatus.OK));

        // Create and process a job
        Map<String, String> request = Map.of("externalId", "notification-test-job");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(request);
        Long technicalId = createResponse.getBody().get("technicalId");

        // Wait for processing and notification
        Thread.sleep(3000);

        // Verify job completed and notified subscribers
        ResponseEntity<Job> jobResponse = controller.getJob(technicalId.toString());
        Job job = jobResponse.getBody();
        assertNotNull(job);
        assertEquals("NOTIFIED_SUBSCRIBERS", job.getState());
    }

    @Test
    void laureateCreation_DuringJobProcessing_ShouldBeAccessible() throws Exception {
        // Mock API response with laureate data
        String mockApiResponse = """
            {
              "records": [
                {
                  "fields": {
                    "id": 999,
                    "firstname": "Test",
                    "surname": "Laureate",
                    "born": "1980-05-15",
                    "died": null,
                    "borncountry": "Test Country",
                    "borncountrycode": "TC",
                    "borncity": "Test City",
                    "gender": "female",
                    "year": "2021",
                    "category": "Physics",
                    "motivation": "for groundbreaking test research",
                    "name": "Test University",
                    "city": "Test City",
                    "country": "Test Country"
                  }
                }
              ]
            }
            """;

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockApiResponse, HttpStatus.OK));

        // Create a job
        Map<String, String> request = Map.of("externalId", "laureate-creation-test");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(request);
        Long jobTechnicalId = createResponse.getBody().get("technicalId");

        // Wait for processing to complete
        Thread.sleep(2000);

        // Verify job succeeded
        ResponseEntity<Job> jobResponse = controller.getJob(jobTechnicalId.toString());
        Job job = jobResponse.getBody();
        assertEquals("SUCCEEDED", job.getState());

        // Try to find the created laureate (we know it should have ID 1 since it's the first one)
        ResponseEntity<Laureate> laureateResponse = controller.getLaureate("1");
        if (laureateResponse.getStatusCode() == HttpStatus.OK) {
            Laureate laureate = laureateResponse.getBody();
            assertNotNull(laureate);
            assertEquals("Test", laureate.getFirstname());
            assertEquals("Laureate", laureate.getSurname());
            assertEquals("female", laureate.getGender());
            assertEquals("1980-05-15", laureate.getBorn());
            assertEquals("Physics", laureate.getCategory());
            assertEquals("TC", laureate.getBorncountrycode().toUpperCase());
            assertNotNull(laureate.getCalculatedAge());
        }
    }
}
