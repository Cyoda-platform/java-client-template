package com.java_template.common.tool;

import com.java_template.common.auth.Authentication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class WorkflowImportTool {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext("com.java_template");

        Authentication auth = context.getBean(Authentication.class);

        CyodaInit init = new CyodaInit(auth);
        init.initCyoda().join();  // wait for completion

        context.close();
    }
}

