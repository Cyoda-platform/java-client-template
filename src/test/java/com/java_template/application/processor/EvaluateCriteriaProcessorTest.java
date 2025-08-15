package com.java_template.application.processor;

import com.java_template.application.criterion.IsGloomyCriterion;
import com.java_template.application.criterion.IsHappyCriterion;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluateCriteriaProcessorTest {

    // Minimal ProcessorSerializer for SerializerFactory
    static class TestProcessorSerializer implements ProcessorSerializer {
        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> T extractEntity(org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest request, Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode extractPayload(org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> com.fasterxml.jackson.databind.JsonNode entityToJsonNode(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getType() {
            return "jackson";
        }

        @Override
        public ResponseBuilder.ProcessorResponseBuilder responseBuilder(org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest request) {
            throw new UnsupportedOperationException();
        }

        // The default withRequest is provided by the interface; no need to implement additional methods
    }

    // Minimal CriterionSerializer for SerializerFactory
    static class TestCriterionSerializer implements CriterionSerializer {
        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> T extractEntity(org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest request, Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode extractPayload(org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends com.java_template.common.workflow.CyodaEntity> com.fasterxml.jackson.databind.JsonNode entityToJsonNode(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getType() {
            return "jackson";
        }

        @Override
        public ResponseBuilder.CriterionResponseBuilder responseBuilder(org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void testEvaluateCriteriaSetsIsHappyAndEvaluatedState() throws Exception {
        // Arrange - create minimal SerializerFactory with our test serializers
        SerializerFactory factory = new SerializerFactory(List.of(new TestProcessorSerializer()), List.of(new TestCriterionSerializer()));

        IsHappyCriterion happy = new IsHappyCriterion(factory);
        IsGloomyCriterion gloomy = new IsGloomyCriterion(factory);

        EvaluateCriteriaProcessor processor = new EvaluateCriteriaProcessor(factory, happy, gloomy);

        Mail mail = new Mail();
        mail.setTechnicalId("test-1");
        mail.setMailList(List.of("alice@happy.example.com", "bob@example.com"));
        mail.setIsHappy(Boolean.FALSE); // client-provided value should be ignored

        // Call private method processEntityLogic via reflection
        Method m = EvaluateCriteriaProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        // The ProcessorEntityExecutionContext can be constructed with a null request for test purposes
        @SuppressWarnings("unchecked")
        ProcessorEntityExecutionContext<Mail> ctx = new ProcessorEntityExecutionContext<>(null, mail);

        Mail result = (Mail) m.invoke(processor, ctx);

        // Assert
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsHappy()), "Mail should be classified as happy");
        assertEquals("EVALUATED", result.getState(), "State should be EVALUATED");
        assertNotNull(result.getDeliveryStatus(), "deliveryStatus should be initialized");
        assertEquals("PENDING", result.getDeliveryStatus().get("status"));
        assertEquals(0, ((Number) result.getDeliveryStatus().get("attempts")).intValue());
    }
}
