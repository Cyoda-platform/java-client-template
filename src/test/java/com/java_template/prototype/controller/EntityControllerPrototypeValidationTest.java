package com.java_template.prototype.controller;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.prototype.EntityControllerPrototype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the validation logic embedded in EntityControllerPrototype.
 * These tests use reflection to access private validation methods.
 */
@ExtendWith(MockitoExtension.class)
class EntityControllerPrototypeValidationTest {

    private EntityControllerPrototype controller;

    @BeforeEach
    void setUp() {
        controller = new EntityControllerPrototype();
    }

    // ================= JOB VALIDATION TESTS =================

    @Test
    void simulateValidationProcessorJob_WithValidJob_ShouldNotThrow() throws Exception {
        Job job = new Job();
        job.setExternalId("valid-external-id");
        job.setState("SCHEDULED");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorJob", Job.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> {
            try {
                method.invoke(controller, job);
            } catch (Exception e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void simulateValidationProcessorJob_WithNullExternalId_ShouldThrow() throws Exception {
        Job job = new Job();
        job.setExternalId(null);
        job.setState("SCHEDULED");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorJob", Job.class);
        method.setAccessible(true);

        assertThrows(IllegalArgumentException.class, () -> {
            try {
                method.invoke(controller, job);
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void simulateValidationProcessorJob_WithBlankExternalId_ShouldThrow() throws Exception {
        Job job = new Job();
        job.setExternalId("");
        job.setState("SCHEDULED");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorJob", Job.class);
        method.setAccessible(true);

        assertThrows(IllegalArgumentException.class, () -> {
            try {
                method.invoke(controller, job);
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void simulateValidationProcessorJob_WithWrongState_ShouldThrow() throws Exception {
        Job job = new Job();
        job.setExternalId("valid-external-id");
        job.setState("INGESTING");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorJob", Job.class);
        method.setAccessible(true);

        assertThrows(IllegalStateException.class, () -> {
            try {
                method.invoke(controller, job);
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalStateException) {
                    throw (IllegalStateException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    // ================= LAUREATE VALIDATION TESTS =================

    @Test
    void simulateValidationProcessorLaureate_WithValidLaureate_ShouldReturnTrue() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setFirstname("John");
        laureate.setSurname("Doe");
        laureate.setGender("male");
        laureate.setBorn("1950-01-01");
        laureate.setYear("2020");
        laureate.setCategory("Physics");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, laureate);
        assertTrue(result);
    }

    @Test
    void simulateValidationProcessorLaureate_WithMissingFirstname_ShouldReturnFalse() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setFirstname(null);
        laureate.setSurname("Doe");
        laureate.setGender("male");
        laureate.setBorn("1950-01-01");
        laureate.setYear("2020");
        laureate.setCategory("Physics");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, laureate);
        assertFalse(result);
    }

    @Test
    void simulateValidationProcessorLaureate_WithBlankSurname_ShouldReturnFalse() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setFirstname("John");
        laureate.setSurname("");
        laureate.setGender("male");
        laureate.setBorn("1950-01-01");
        laureate.setYear("2020");
        laureate.setCategory("Physics");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, laureate);
        assertFalse(result);
    }

    @Test
    void simulateValidationProcessorLaureate_WithMissingRequiredFields_ShouldReturnFalse() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setFirstname("John");
        laureate.setSurname("Doe");
        // Missing gender, born, year, category

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, laureate);
        assertFalse(result);
    }

    // ================= SUBSCRIBER VALIDATION TESTS =================

    @Test
    void simulateValidationProcessorSubscriber_WithValidSubscriber_ShouldReturnTrue() throws Exception {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("https://example.com/webhook");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorSubscriber", Subscriber.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, subscriber);
        assertTrue(result);
    }

    @Test
    void simulateValidationProcessorSubscriber_WithValidEmailOnly_ShouldReturnTrue() throws Exception {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl(null);

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorSubscriber", Subscriber.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, subscriber);
        assertTrue(result);
    }

    @Test
    void simulateValidationProcessorSubscriber_WithInvalidEmail_ShouldReturnFalse() throws Exception {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("invalid-email");
        subscriber.setWebhookUrl("https://example.com/webhook");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorSubscriber", Subscriber.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, subscriber);
        assertFalse(result);
    }

    @Test
    void simulateValidationProcessorSubscriber_WithInvalidWebhookUrl_ShouldReturnFalse() throws Exception {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail("test@example.com");
        subscriber.setWebhookUrl("invalid-url");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorSubscriber", Subscriber.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, subscriber);
        assertFalse(result);
    }

    @Test
    void simulateValidationProcessorSubscriber_WithMissingEmail_ShouldReturnFalse() throws Exception {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactEmail(null);
        subscriber.setWebhookUrl("https://example.com/webhook");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateValidationProcessorSubscriber", Subscriber.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(controller, subscriber);
        assertFalse(result);
    }

    // ================= ENRICHMENT TESTS =================

    @Test
    void simulateEnrichmentProcessorLaureate_WithValidDates_ShouldCalculateAge() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setId(1L);
        laureate.setBorn("1950-01-01");
        laureate.setDied(null);
        laureate.setBorncountrycode("us");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateEnrichmentProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        method.invoke(controller, laureate);

        assertNotNull(laureate.getCalculatedAge());
        assertTrue(laureate.getCalculatedAge() > 0);
        assertEquals("US", laureate.getBorncountrycode());
    }

    @Test
    void simulateEnrichmentProcessorLaureate_WithInvalidDate_ShouldHandleGracefully() throws Exception {
        Laureate laureate = new Laureate();
        laureate.setId(1L);
        laureate.setBorn("invalid-date");
        laureate.setBorncountrycode("jp");

        Method method = EntityControllerPrototype.class.getDeclaredMethod("simulateEnrichmentProcessorLaureate", Laureate.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> {
            try {
                method.invoke(controller, laureate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertNull(laureate.getCalculatedAge());
        assertEquals("JP", laureate.getBorncountrycode());
    }
}
