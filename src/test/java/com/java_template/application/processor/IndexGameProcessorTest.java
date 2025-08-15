package com.java_template.application.processor;

import com.java_template.application.entity.game.version_1.Game;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class IndexGameProcessorTest {

    @Test
    public void testIndexingLogsAndReturnsEntity() throws Exception {
        SerializerFactory sf = org.mockito.Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = org.mockito.Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        org.mockito.Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        IndexGameProcessor processor = new IndexGameProcessor(sf);
        Game g = new Game();
        g.setGameId("game-1");
        g.setDate("2025-03-25");

        Method m = IndexGameProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Game out = (Game) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, g));

        assertNotNull(out);
        assertEquals("game-1", out.getGameId());
    }
}
