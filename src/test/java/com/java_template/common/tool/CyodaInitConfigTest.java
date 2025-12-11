package com.java_template.common.tool;

import com.beust.jcommander.JCommander;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for CyodaInitConfig to verify command line argument parsing
 * and configuration behavior.
 */
@DisplayName("CyodaInitConfig Tests")
class CyodaInitConfigTest {

    @Test
    @DisplayName("Default configuration should have all flags set to false")
    void testDefaultConfiguration() {
        CyodaInitConfig config = new CyodaInitConfig();

        assertFalse(config.recreateModels(), "recreateModels should be false by default");
        assertFalse(config.help(), "help should be false by default");
    }

    @Test
    @DisplayName("Should parse --recreate-models flag correctly")
    void testParseRecreateModelsFlag() {
        CyodaInitConfig config = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        jCommander.parse("--recreate-models");

        assertTrue(config.recreateModels(), "recreateModels should be true when flag is provided");
        assertFalse(config.help(), "help should remain false");
    }

    @Test
    @DisplayName("Should parse --help flag correctly")
    void testParseHelpFlag() {
        CyodaInitConfig config = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        jCommander.parse("--help");

        assertTrue(config.help(), "help should be true when --help flag is provided");
        assertFalse(config.recreateModels(), "recreateModels should remain false");
    }

    @Test
    @DisplayName("Should parse -h flag correctly")
    void testParseShortHelpFlag() {
        CyodaInitConfig config = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        jCommander.parse("-h");

        assertTrue(config.help(), "help should be true when -h flag is provided");
        assertFalse(config.recreateModels(), "recreateModels should remain false");
    }

    @Test
    @DisplayName("Should parse multiple flags correctly")
    void testParseMultipleFlags() {
        CyodaInitConfig config = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        jCommander.parse("--recreate-models", "--help");

        assertTrue(config.recreateModels(), "recreateModels should be true");
        assertTrue(config.help(), "help should be true");
    }

    @Test
    @DisplayName("Should handle no arguments")
    void testParseNoArguments() {
        CyodaInitConfig config = new CyodaInitConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        jCommander.parse();

        assertFalse(config.recreateModels(), "recreateModels should be false with no arguments");
        assertFalse(config.help(), "help should be false with no arguments");
    }

    @Test
    @DisplayName("withRecreateModels factory method should create correct config")
    void testWithRecreateModelsFactory() {
        CyodaInitConfig config = CyodaInitConfig.withRecreateModels(true);

        assertTrue(config.recreateModels(), "recreateModels should be true");
        assertFalse(config.help(), "help should be false");
    }

    @Test
    @DisplayName("withRecreateModels factory method with false should create correct config")
    void testWithRecreateModelsFactoryFalse() {
        CyodaInitConfig config = CyodaInitConfig.withRecreateModels(false);

        assertFalse(config.recreateModels(), "recreateModels should be false");
        assertFalse(config.help(), "help should be false");
    }

    @Test
    @DisplayName("Record constructor should work correctly")
    void testRecordConstructor() {
        CyodaInitConfig config = new CyodaInitConfig(true, true);

        assertTrue(config.recreateModels(), "recreateModels should be true");
        assertTrue(config.help(), "help should be true");
    }
}

