package com.microsoft.azure.practices.nvadaemon;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.microsoft.azure.practices.nvadaemon.NvaDaemonConfig.ConfigException;

import java.lang.reflect.Field;

public class NvaDaemonConfigTest {
    private static String testOutputDirectory;

    @BeforeAll
    static void getProperties()
    {
        testOutputDirectory = System.getProperty("testOutputDirectory");
        if ((testOutputDirectory == null) || (testOutputDirectory == "")) {
            throw new IllegalArgumentException("testOutputDirectory was not specified");
        }
    }
    @Test
    void test_no_command_line_args() {
        Assertions.assertThrows(ConfigException.class,
            ()-> NvaDaemonConfig.parseArguments(new String[] { }));
    }

    @Test
    void test_no_config_file_specified() {
        Assertions.assertThrows(ConfigException.class,
            () -> NvaDaemonConfig.parseArguments(new String[] { "-c" }));
    }

    @Test
    void test_non_existent_config_file_specified() {
        Assertions.assertThrows(ConfigException.class,
            () -> NvaDaemonConfig.parseArguments(
                new String[] { "-c", testOutputDirectory + "/does-not-exist.cfg" }
            ));
    }

    @Test
    void test_invalid_properties_file() {
        Assertions.assertThrows(ConfigException.class,
            () -> NvaDaemonConfig.parseArguments(
                new String[] { "-c", testOutputDirectory + "/invalid-properties.cfg"}
            ));
    }

    @Test
    void test_missing_required_settings() {
        Assertions.assertThrows(ConfigException.class,
            () -> NvaDaemonConfig.parseArguments(
                new String[] { "-c", testOutputDirectory + "/missing-required-settings.cfg"}
            ));
    }

    @Test
    void test_invalid_setting_with_default_value() throws ConfigException, NoSuchFieldException,
    IllegalAccessException {
        NvaDaemonConfig config = NvaDaemonConfig.parseArguments(
            new String[]{"-c", testOutputDirectory + "/invalid-retry-setting.cfg"}
        );

        // We will just pull the value out of the private static field. :)
        Field defaultValueField = NvaDaemonConfig.class.getDeclaredField(
            "DEFAULT_NUMBER_OF_RETRIES_SETTING");
        defaultValueField.setAccessible(true);
        int defaultValue = (int)defaultValueField.get(null);
        Assertions.assertEquals(defaultValue, config.getNumberOfRetries());
    }
}
