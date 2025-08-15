package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class ParseAndPersistGamesProcessorTest {

    @Test
    public void testParseEmptyPayload() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);
        EntityService entityService = Mockito.mock(EntityService.class);

        ParseAndPersistGamesProcessor processor = new ParseAndPersistGamesProcessor(sf, jsonUtils, entityService);

        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");
        job.setResponsePayload("");

        java.lang.reflect.Method m = ParseAndPersistGamesProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertNotNull(out);
        assertEquals(0, out.getFetchedCount());
    }
}
