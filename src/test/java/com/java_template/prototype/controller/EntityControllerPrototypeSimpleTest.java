package com.java_template.prototype.controller;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.prototype.EntityControllerPrototype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test class for EntityControllerPrototype with one test per endpoint.
 * Tests only sunny day scenarios (happy path).
 */
class EntityControllerPrototypeSimpleTest {

    private EntityControllerPrototype controller;

    @BeforeEach
    void setUp() {
        controller = new EntityControllerPrototype();
    }

    @Test
    void createJob_ShouldReturnCreatedWithTechnicalId() {
        // Given
        Map<String, String> request = Map.of("externalId", "test-job-001");

        // When
        ResponseEntity<Map<String, Long>> response = controller.createJob(request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("technicalId"));
        assertTrue(response.getBody().get("technicalId") > 0);
    }

    @Test
    void getJob_ShouldReturnJobDetails() throws InterruptedException {
        // Given - create a job first
        Map<String, String> createRequest = Map.of("externalId", "test-job-002");
        ResponseEntity<Map<String, Long>> createResponse = controller.createJob(createRequest);
        String technicalId = createResponse.getBody().get("technicalId").toString();
        
        // Wait a moment for the job to be stored
        Thread.sleep(10);

        // When
        ResponseEntity<Job> response = controller.getJob(technicalId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Job job = response.getBody();
        assertEquals(Long.parseLong(technicalId), job.getId());
        assertEquals("test-job-002", job.getExternalId());
        assertNotNull(job.getState());
        assertNotNull(job.getCreatedAt());
    }

    @Test
    void createSubscriber_ShouldReturnCreatedWithTechnicalId() {
        // Given
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        // When
        ResponseEntity<Map<String, Long>> response = controller.createSubscriber(subscriber);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("technicalId"));
        assertTrue(response.getBody().get("technicalId") > 0);
    }

    @Test
    void getSubscriber_ShouldReturnSubscriberDetails() throws InterruptedException {
        // Given - create a subscriber first
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");
        subscriber.setActive(true);

        ResponseEntity<Map<String, Long>> createResponse = controller.createSubscriber(subscriber);
        String technicalId = createResponse.getBody().get("technicalId").toString();
        
        // Wait a moment for the subscriber to be stored
        Thread.sleep(10);

        // When
        ResponseEntity<Subscriber> response = controller.getSubscriber(technicalId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Subscriber retrievedSubscriber = response.getBody();
        assertEquals(Long.parseLong(technicalId), retrievedSubscriber.getId());
        assertEquals("test@example.com", retrievedSubscriber.getContactEmail());
        assertEquals("https://example.com/webhook", retrievedSubscriber.getWebhookUrl());
        assertTrue(retrievedSubscriber.getActive());
    }

    @Test
    void getLaureate_ShouldReturnNotFoundForNonExistentId() {
        // Given
        String nonExistentId = "999999";

        // When
        ResponseEntity<Laureate> response = controller.getLaureate(nonExistentId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
