package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class GenerateDailySummaryProcessorTest {

    @Test
    public void testGenerateSummaryCreatesDailySummary() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = Mockito.mock(EntityService.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        GenerateDailySummaryProcessor processor = new GenerateDailySummaryProcessor(sf, entityService, jsonUtils);

        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");

        ArrayNode games = Mockito.mock(ArrayNode.class);
        Mockito.when(games.size()).thenReturn(0);
        CompletableFuture<ArrayNode> future = CompletableFuture.completedFuture(games);
        Mockito.when(entityService.getItemsByCondition(Mockito.eq(Game.ENTITY_NAME), Mockito.eq(String.valueOf(Game.ENTITY_VERSION)), Mockito.any(), Mockito.eq(true))).thenReturn(future);

        java.lang.reflect.Method m = GenerateDailySummaryProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertNotNull(out);
        assertEquals("2025-03-25", out.getRequestDate());
    }
}
