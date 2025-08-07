package com.java_template.prototype.controller;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.prototype.EntityControllerPrototype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntityControllerPrototype that test the controller logic directly
 * without Spring's web layer to avoid serialization issues.
 */
class EntityControllerPrototypeUnitTest {

    private EntityControllerPrototype controller;

    @BeforeEach
    void setUp() {
        controller = new EntityControllerPrototype();
    }

    // ================= JOB ENDPOINT TESTS =================

    @Test
    void createJob_WithValidExternalId_ShouldReturnCreatedWithTechnicalId() {
        Map<String, String> request = Map.of("externalId", "test-job-001");

        ResponseEntity<Map<String, Long>> response = controller.createJob(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("technicalId"));
        assertTrue(response.getBody().get("technicalId") > 0);
    }

    @Test
    void createJob_WithMissingExternalId_ShouldReturnBadRequest() {
        Map<String, String> request = new HashMap<>();

        ResponseEntity<Map<String, Long>> response = controller.createJob(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createJob_WithBlankExternalId_ShouldReturnBadRequest() {
        Map<String, String> request = Map.of("externalId", "");

        ResponseEntity<Map<String, Long>> response = controller.createJob(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createJob_WithNullExternalId_ShouldReturnBadRequest() {
        Map<String, String> request = new HashMap<>();
        request.put("externalId", null);

        ResponseEntity<Map<String, Long>> response = controller.createJob(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getJob_WithValidId_ShouldReturnJobDetails() throws InterruptedException {
        // First create a job
        Map<String, String> createRequest = Map.of("externalId", "test-job-002");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(createRequest);
        String technicalId = createResponse.getBody().get("technicalId").toString();

        // Give a moment for the job to be stored
        Thread.sleep(10);

        // Then retrieve the job
        ResponseEntity<Job> getResponse = controller.getJob(technicalId);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        Job job = getResponse.getBody();
        assertEquals(Long.parseLong(technicalId), job.getId());
        assertEquals("test-job-002", job.getExternalId());
        // State could be SCHEDULED or INGESTING due to async processing
        assertTrue(job.getState().equals("SCHEDULED") || job.getState().equals("INGESTING"));
        assertNotNull(job.getCreatedAt());
    }

    @Test
    void getJob_WithInvalidId_ShouldReturnNotFound() {
        ResponseEntity<Job> response = controller.getJob("999999");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ================= SUBSCRIBER ENDPOINT TESTS =================

    @Test
    void createSubscriber_WithValidData_ShouldReturnCreatedWithTechnicalId() {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("technicalId"));
        assertTrue(response.getBody().get("technicalId") > 0);
    }

    @Test
    void createSubscriber_WithMissingContactEmail_ShouldReturnBadRequest() {
        Subscriber subscriber = new Subscriber();
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createSubscriber_WithBlankContactEmail_ShouldReturnBadRequest() {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("");
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createSubscriber_WithMissingActiveFlag_ShouldReturnBadRequest() {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");
        // active is null

        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createSubscriber_WithOnlyEmailAndActive_ShouldReturnCreated() {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setActive(true);
        // webhookUrl is null - should be allowed

        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("technicalId") > 0);
    }

    @Test
    void getSubscriber_WithValidId_ShouldReturnSubscriberDetails() throws InterruptedException {
        // First create a subscriber
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        ResponseEntity<Map<String, Long>> createResponse = controller.createSubscriber(subscriber);
        String technicalId = createResponse.getBody().get("technicalId").toString();

        // Give a moment for the subscriber to be stored
        Thread.sleep(10);

        // Then retrieve the subscriber
        ResponseEntity<Subscriber> getResponse = controller.getSubscriber(technicalId);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        Subscriber retrievedSubscriber = getResponse.getBody();
        assertEquals(Long.parseLong(technicalId), retrievedSubscriber.getId());
        assertEquals("test@example.com", retrievedSubscriber.getContactEmail());
        assertEquals("https://example.com/webhook", retrievedSubscriber.getWebhookUrl());
        assertTrue(retrievedSubscriber.getActive());
    }

    @Test
    void getSubscriber_WithInvalidId_ShouldReturnNotFound() {
        ResponseEntity<Subscriber> response = controller.getSubscriber("999999");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ================= LAUREATE ENDPOINT TESTS =================

    @Test
    void getLaureate_WithInvalidId_ShouldReturnNotFound() {
        ResponseEntity<Laureate> response = controller.getLaureate("999999");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ================= INTEGRATION TESTS =================

    @Test
    void multipleJobsCreation_ShouldHaveUniqueIds() {
        Map<String, String> request1 = Map.of("externalId", "job-001");
        Map<String, String> request2 = Map.of("externalId", "job-002");

        ResponseEntity<Map<String, Long>> response1 = controller.createJob(request1);
        ResponseEntity<Map<String, Long>> response2 = controller.createJob(request2);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());

        Long technicalId1 = response1.getBody().get("technicalId");
        Long technicalId2 = response2.getBody().get("technicalId");

        assertNotEquals(technicalId1, technicalId2);
    }

    @Test
    void multipleSubscribersCreation_ShouldHaveUniqueIds() {
        Subscriber subscriber1 = new Subscriber();
        subscriber1.setContactEmail("test1@example.com");
        subscriber1.setActive(true);

        Subscriber subscriber2 = new Subscriber();
        subscriber2.setContactEmail("test2@example.com");
        subscriber2.setActive(false);

        ResponseEntity<Map<String, Long>> response1 = controller.createSubscriber(subscriber1);
        ResponseEntity<Map<String, Long>> response2 = controller.createSubscriber(subscriber2);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());

        Long technicalId1 = response1.getBody().get("technicalId");
        Long technicalId2 = response2.getBody().get("technicalId");

        assertNotEquals(technicalId1, technicalId2);
    }

    @Test
    void jobWorkflow_ShouldStartWithScheduledState() throws InterruptedException {
        // Create a job
        Map<String, String> createRequest = Map.of("externalId", "integration-test-job");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(createRequest);
        String technicalId = createResponse.getBody().get("technicalId").toString();

        // Give a moment for the job to be stored
        Thread.sleep(10);

        // Verify job was created successfully and has progressed through workflow
        ResponseEntity<Job> jobResponse = controller.getJob(technicalId);
        assertEquals(HttpStatus.OK, jobResponse.getStatusCode());
        Job job = jobResponse.getBody();
        assertNotNull(job);
        // State could be SCHEDULED or INGESTING due to async processing
        assertTrue(job.getState().equals("SCHEDULED") || job.getState().equals("INGESTING"));
        assertNotNull(job.getCreatedAt());
        // completedAt should be null since job is still processing
        assertNull(job.getCompletedAt());
    }
}
