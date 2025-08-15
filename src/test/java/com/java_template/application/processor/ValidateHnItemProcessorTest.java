package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateHnItemProcessorTest {

    @Test
    public void testValidEntityPassesValidation() throws Exception {
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(new JacksonProcessorSerializer(new ObjectMapper())),
            List.of(new JacksonCriterionSerializer(new ObjectMapper()))
        );
        ValidateHnItemProcessor processor = new ValidateHnItemProcessor(serializerFactory);

        HackerNewsItem valid = new HackerNewsItem();
        valid.setId("1");
        valid.setType("story");

        Method m = ValidateHnItemProcessor.class.getDeclaredMethod("isValidEntity", HackerNewsItem.class);
        m.setAccessible(true);
        boolean ok = (boolean) m.invoke(processor, valid);

        assertTrue(ok, "Valid entity should pass validation");
    }
}
