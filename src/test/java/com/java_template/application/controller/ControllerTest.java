package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.application.entity.job.version_1000.Job;
import com.java_template.application.entity.laureate.version_1000.Laureate;
import com.java_template.application.entity.subscriber.version_1000.Subscriber;
import com.java_template.prototype.config.MockAuthenticationConfig;
import com.java_template.prototype.config.MockGrpcConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Controller class in prototype mode.
 * Tests all CRUD operations for Job, Laureate, and Subscriber entities.
 */
@SpringBootTest(classes = ControllerTest.TestConfig.class)
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "logging.level.com.java_template=DEBUG",
    "prototype.enabled=true",
    "spring.profiles.active=prototype"
})
public class ControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Configuration
    @ComponentScan(
        basePackages = {
            "com.java_template.prototype",
            "com.java_template.application",
            "com.java_template.common"
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, 
            pattern = "com.java_template.common.grpc.*|com.java_template.common.auth.*|com.java_template.common.service.*")
    )
    @EnableAutoConfiguration(exclude = {OAuth2ClientAutoConfiguration.class})
    @Import({MockAuthenticationConfig.class, MockGrpcConfig.class})
    static class TestConfig {
    }

    /**
     * Checks if the prototype is enabled via system property.
     */
    static boolean isPrototypeEnabled() {
        return "true".equals(System.getProperty("prototype.enabled"));
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Configure ObjectMapper to handle Java 8 time types
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ========== JOB TESTS ==========

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateJob_Success() throws Exception {
        // Given
        Job job = createValidJob();
        String jobJson = objectMapper.writeValueAsString(job);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jobJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicalId").exists())
                .andExpect(jsonPath("$.technicalId").isString())
                .andReturn();

        // Verify the response contains a valid UUID
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("technicalId"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateJob_InvalidData() throws Exception {
        // Given - Job with missing required fields
        Job invalidJob = new Job();
        // Missing jobName, status, createdAt
        String jobJson = objectMapper.writeValueAsString(invalidJob);

        // When & Then
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jobJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetJob_Success() throws Exception {
        // Given - Create a job first
        Job job = createValidJob();
        String jobJson = objectMapper.writeValueAsString(job);

        MvcResult createResult = mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jobJson))
                .andExpect(status().isCreated())
                .andReturn();

        String technicalId = extractTechnicalId(createResult);

        // When & Then
        mockMvc.perform(get("/api/jobs/{technicalId}", technicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value(job.getJobName()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetJob_NotFound() throws Exception {
        // Given - Non-existent UUID
        String nonExistentId = "550e8400-e29b-41d4-a716-446655440000";

        // When & Then
        mockMvc.perform(get("/api/jobs/{technicalId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Job not found"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetJob_InvalidUUID() throws Exception {
        // Given - Invalid UUID format
        String invalidId = "invalid-uuid";

        // When & Then
        mockMvc.perform(get("/api/jobs/{technicalId}", invalidId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testUpdateJob_Success() throws Exception {
        // Given - Create a job first
        Job job = createValidJob();
        String jobJson = objectMapper.writeValueAsString(job);

        MvcResult createResult = mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jobJson))
                .andExpect(status().isCreated())
                .andReturn();

        String technicalId = extractTechnicalId(createResult);

        // Update the job
        job.setStatus("PROCESSING");
        job.setJobName("Updated Job Name");
        String updatedJobJson = objectMapper.writeValueAsString(job);

        // When & Then
        mockMvc.perform(put("/api/jobs/{technicalId}", technicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedJobJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicalId").value(technicalId));

        // Verify the update
        mockMvc.perform(get("/api/jobs/{technicalId}", technicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("Updated Job Name"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetAllJobs_Success() throws Exception {
        // Given - Create multiple jobs
        Job job1 = createValidJob();
        job1.setJobName("Job 1");
        Job job2 = createValidJob();
        job2.setJobName("Job 2");

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job2)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    // ========== LAUREATE TESTS ==========

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateLaureate_Success() throws Exception {
        // Given
        Laureate laureate = createValidLaureate();
        String laureateJson = objectMapper.writeValueAsString(laureate);

        // When & Then
        mockMvc.perform(post("/api/laureates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(laureateJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicalId").exists())
                .andExpect(jsonPath("$.technicalId").isString());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetLaureate_Success() throws Exception {
        // Given - Create a laureate first
        Laureate laureate = createValidLaureate();
        String laureateJson = objectMapper.writeValueAsString(laureate);

        MvcResult createResult = mockMvc.perform(post("/api/laureates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(laureateJson))
                .andExpect(status().isCreated())
                .andReturn();

        String technicalId = extractTechnicalId(createResult);

        // When & Then
        mockMvc.perform(get("/api/laureates/{technicalId}", technicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstname").value(laureate.getFirstname()))
                .andExpect(jsonPath("$.surname").value(laureate.getSurname()))
                .andExpect(jsonPath("$.laureateId").value(laureate.getLaureateId()));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetAllLaureates_Success() throws Exception {
        // Given - Create a laureate
        Laureate laureate = createValidLaureate();
        String laureateJson = objectMapper.writeValueAsString(laureate);

        mockMvc.perform(post("/api/laureates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(laureateJson))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/api/laureates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    // ========== SUBSCRIBER TESTS ==========

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateSubscriber_Success() throws Exception {
        // Given
        Subscriber subscriber = createValidSubscriber();
        String subscriberJson = objectMapper.writeValueAsString(subscriber);

        // When & Then
        mockMvc.perform(post("/api/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriberJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicalId").exists())
                .andExpect(jsonPath("$.technicalId").isString());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetSubscriber_Success() throws Exception {
        // Given - Create a subscriber first
        Subscriber subscriber = createValidSubscriber();
        String subscriberJson = objectMapper.writeValueAsString(subscriber);

        MvcResult createResult = mockMvc.perform(post("/api/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriberJson))
                .andExpect(status().isCreated())
                .andReturn();

        String technicalId = extractTechnicalId(createResult);

        // When & Then
        mockMvc.perform(get("/api/subscribers/{technicalId}", technicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriberId").value(subscriber.getSubscriberId()))
                .andExpect(jsonPath("$.contactAddress").value(subscriber.getContactAddress()))
                .andExpect(jsonPath("$.active").value(subscriber.getActive()));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testUpdateSubscriber_Success() throws Exception {
        // Given - Create a subscriber first
        Subscriber subscriber = createValidSubscriber();
        String subscriberJson = objectMapper.writeValueAsString(subscriber);

        MvcResult createResult = mockMvc.perform(post("/api/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriberJson))
                .andExpect(status().isCreated())
                .andReturn();

        String technicalId = extractTechnicalId(createResult);

        // Update the subscriber
        subscriber.setActive(false);
        subscriber.setContactAddress("updated@example.com");
        String updatedSubscriberJson = objectMapper.writeValueAsString(subscriber);

        // When & Then
        mockMvc.perform(put("/api/subscribers/{technicalId}", technicalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedSubscriberJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicalId").value(technicalId));

        // Verify the update
        mockMvc.perform(get("/api/subscribers/{technicalId}", technicalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactAddress").value("updated@example.com"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetAllSubscribers_Success() throws Exception {
        // Given - Create a subscriber
        Subscriber subscriber = createValidSubscriber();
        String subscriberJson = objectMapper.writeValueAsString(subscriber);

        mockMvc.perform(post("/api/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriberJson))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/api/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateLaureate_InvalidData() throws Exception {
        // Given - Laureate with missing required fields
        Laureate invalidLaureate = new Laureate();
        // Missing laureateId, firstname, surname, year, category
        String laureateJson = objectMapper.writeValueAsString(invalidLaureate);

        // When & Then
        mockMvc.perform(post("/api/laureates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(laureateJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testCreateSubscriber_InvalidData() throws Exception {
        // Given - Subscriber with missing required fields
        Subscriber invalidSubscriber = new Subscriber();
        // Missing subscriberId, contactType, contactAddress, active
        String subscriberJson = objectMapper.writeValueAsString(invalidSubscriber);

        // When & Then
        mockMvc.perform(post("/api/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriberJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetLaureate_NotFound() throws Exception {
        // Given - Non-existent UUID
        String nonExistentId = "550e8400-e29b-41d4-a716-446655440001";

        // When & Then
        mockMvc.perform(get("/api/laureates/{technicalId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Laureate not found"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetSubscriber_NotFound() throws Exception {
        // Given - Non-existent UUID
        String nonExistentId = "550e8400-e29b-41d4-a716-446655440002";

        // When & Then
        mockMvc.perform(get("/api/subscribers/{technicalId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Subscriber not found"));
    }

    // ========== HELPER METHODS ==========

    private Job createValidJob() {
        Job job = new Job();
        job.setJobName("Test Job");
        job.setStatus("SCHEDULED");
        job.setCreatedAt(OffsetDateTime.now());
        return job;
    }

    private Laureate createValidLaureate() {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(123);
        laureate.setFirstname("Albert");
        laureate.setSurname("Einstein");
        laureate.setBorn(LocalDate.of(1879, 3, 14));
        laureate.setBorncountry("Germany");
        laureate.setBorncountrycode("DE");
        laureate.setBorncity("Ulm");
        laureate.setGender("male");
        laureate.setYear("1921");
        laureate.setCategory("Physics");
        laureate.setMotivation("for his services to Theoretical Physics");
        return laureate;
    }

    private Subscriber createValidSubscriber() {
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("SUB001");
        subscriber.setContactType("email");
        subscriber.setContactAddress("test@example.com");
        subscriber.setActive(true);
        return subscriber;
    }

    private String extractTechnicalId(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("technicalId").asText();
    }
}
