package com.java_template.application.processor;

import com.java_template.application.entity.contact.version_1.Contact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class ValidateContactCriterionTest {

    @Test
    public void testProcess_validatesContactAndPersists() throws Exception {
        SerializerFactory sf = mock(SerializerFactory.class);
        ProcessorSerializer ps = mock(ProcessorSerializer.class);
        when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = mock(EntityService.class);
        when(entityService.updateItem(anyString(), anyString(), any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        ValidateContactCriterion processor = new ValidateContactCriterion(sf, entityService);

        Contact contact = new Contact();
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setEmail("jane.doe@example.com");

        EntityProcessorCalculationRequest request = mock(EntityProcessorCalculationRequest.class);
        String technicalId = UUID.randomUUID().toString();
        when(request.getEntityId()).thenReturn(technicalId);

        ProcessorSerializer.ProcessorEntityExecutionContext<Contact> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(request, contact);

        Method m = ValidateContactCriterion.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Contact result = (Contact) m.invoke(processor, ctx);

        assertNotNull(result);
        assertEquals("VALIDATED", result.getTitle());
        verify(entityService).updateItem(eq(Contact.ENTITY_NAME), eq(String.valueOf(Contact.ENTITY_VERSION)), eq(UUID.fromString(technicalId)), any());
    }
}
