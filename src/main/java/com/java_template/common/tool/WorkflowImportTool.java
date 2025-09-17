package com.java_template.common.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.auth.Authentication;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.util.JsonUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * ABOUTME: Command-line tool for importing workflow definitions into Cyoda platform
 * with Spring context initialization and dependency injection.
 */
public class WorkflowImportTool {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(Authentication.class, HttpUtils.class, JsonUtils.class);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh();

        Authentication auth = context.getBean(Authentication.class);
        HttpUtils httpUtils = context.getBean(HttpUtils.class);
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

        CyodaInit init = new CyodaInit(httpUtils, auth, objectMapper);
        init.initCyoda();

        context.close();
    }
}

