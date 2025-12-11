package com.java_template.common.tool;

import com.beust.jcommander.Parameter;

/**
 * ABOUTME: Configuration class for CyodaInit tool containing command line options
 * and settings for workflow import and entity model management.
 */
public class CyodaInitConfig {

    @Parameter(
            names = {"--recreate-models"},
            description = "Delete and recreate existing entity models"
    )
    private boolean recreateModels = false;

    @Parameter(
            names = {"--help", "-h"},
            description = "Display help information",
            help = true
    )
    private boolean help = false;

    /**
     * Creates a default configuration with all options set to false
     */
    public CyodaInitConfig() {
    }

    /**
     * Creates a configuration with specified values
     */
    public CyodaInitConfig(boolean recreateModels, boolean help) {
        this.recreateModels = recreateModels;
        this.help = help;
    }

    /**
     * Creates a configuration with specified recreateModels flag
     */
    public static CyodaInitConfig withRecreateModels(boolean recreateModels) {
        return new CyodaInitConfig(recreateModels, false);
    }

    public boolean recreateModels() {
        return recreateModels;
    }

    public boolean help() {
        return help;
    }

    public void setRecreateModels(boolean recreateModels) {
        this.recreateModels = recreateModels;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }
}

