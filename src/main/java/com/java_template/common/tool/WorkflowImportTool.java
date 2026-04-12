package com.java_template.common.tool;

import com.beust.jcommander.JCommander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

/**
 * ABOUTME: Command-line tool for importing workflow definitions into Cyoda platform
 * as a Spring Boot application to get full property binding and bean injection.
 */
@SpringBootApplication(scanBasePackages = "com.java_template.common")
@Profile("WorkflowImportTool")
public class WorkflowImportTool implements CommandLineRunner {

    private final CyodaInit cyodaInit;

    private final Logger log = LoggerFactory.getLogger(WorkflowImportTool.class);

    public WorkflowImportTool(CyodaInit cyodaInit) {
        this.cyodaInit = cyodaInit;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WorkflowImportTool.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("WorkflowImportTool");
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Override
    public void run(String... args) {
        CyodaInitConfig initConfig = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(initConfig)
                .programName("WorkflowImportTool")
                .build();

        try {
            jCommander.parse(args);
        } catch (Exception e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            jCommander.usage();
            System.exit(1);
        }

        if (initConfig.help()) {
            jCommander.usage();
            return;
        }

        try {
            cyodaInit.initCyoda(initConfig);
        } catch (Exception e) {
            // No other way to get the error message, so we must do the antipattern of log and throw here
            log.error("Error importing workflows: {}", e.getMessage(), e);
            throw e;
        }
    }
}

