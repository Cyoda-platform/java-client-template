package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SendMailProcessorTest {

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
    }

    @Test
    void testSendMailProcessorSendsAndMarksSent() throws Exception {
        SerializerFactory factory = new SerializerFactory(List.of(new TestProcessorSerializer()), List.of());
        SendMailProcessor processor = new SendMailProcessor(factory);

        Mail mail = new Mail();
        mail.setTechnicalId("mail-1");
        mail.setMailList(List.of("alice@example.com", "bob@example.com"));
        mail.setIsHappy(Boolean.TRUE);
        mail.setState("EVALUATED");

        // Call private processEntityLogic via reflection
        Method m = SendMailProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<Mail> ctx = new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, mail);

        Mail result = (Mail) m.invoke(processor, ctx);

        assertNotNull(result.getDeliveryStatus());
        assertEquals("SENT", result.getDeliveryStatus().get("status"));
        assertEquals("SENT", result.getState());
        Object perRecipient = result.getDeliveryStatus().get("perRecipient");
        assertTrue(perRecipient instanceof Map);
        Map<String, String> pr = (Map<String, String>) perRecipient;
        assertEquals("SENT", pr.get("alice@example.com"));
        assertEquals("SENT", pr.get("bob@example.com"));
    }

    @Test
    void testSendMailProcessorHandlesFailuresAndAttempts() throws Exception {
        SerializerFactory factory = new SerializerFactory(List.of(new TestProcessorSerializer()), List.of());
        SendMailProcessor processor = new SendMailProcessor(factory);

        Mail mail = new Mail();
        mail.setTechnicalId("mail-2");
        mail.setMailList(List.of("alice@example.com", "fail@example.com"));
        mail.setIsHappy(Boolean.FALSE);
        mail.setState("EVALUATED");

        // First attempt
        Method m = SendMailProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<Mail> ctx = new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, mail);

        Mail firstResult = (Mail) m.invoke(processor, ctx);
        assertNotNull(firstResult.getDeliveryStatus());
        assertEquals("FAILED", firstResult.getDeliveryStatus().get("status"));
        assertEquals(1, ((Number) firstResult.getDeliveryStatus().get("attempts")).intValue());
        Map<String, String> pr = (Map<String, String>) firstResult.getDeliveryStatus().get("perRecipient");
        assertEquals("SENT", pr.get("alice@example.com"));
        assertEquals("FAILED", pr.get("fail@example.com"));

        // Second attempt should increment attempts and keep already SENT recipient as SENT
        Mail secondResult = (Mail) m.invoke(processor, ctx);
        assertEquals(2, ((Number) secondResult.getDeliveryStatus().get("attempts")).intValue());
        Map<String, String> pr2 = (Map<String, String>) secondResult.getDeliveryStatus().get("perRecipient");
        assertEquals("SENT", pr2.get("alice@example.com"));
        assertEquals("FAILED", pr2.get("fail@example.com"));
    }
}
