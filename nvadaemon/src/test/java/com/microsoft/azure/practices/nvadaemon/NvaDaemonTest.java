package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.practices.nvadaemon.config.ConfigurationException;
import com.microsoft.azure.practices.nvadaemon.config.NvaDaemonConfiguration;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.apache.commons.daemon.DaemonInitException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.Preconditions;

public class NvaDaemonTest {
    private static String testOutputDirectory;

    @BeforeAll
    static void getProperties()
    {
        testOutputDirectory = Preconditions.notBlank(
            System.getProperty("testOutputDirectory"),
            "testOutputDirectory cannot be null or empty");
    }

    @Test
    void testNullDaemonContext() {
        NvaDaemon nvaDaemon = new NvaDaemon();
        Assertions.assertThrows(NullPointerException.class,
            () -> nvaDaemon.init(null));
    }

    @Test
    void testInvalidArguments() {
        NvaDaemon nvaDaemon = new NvaDaemon();
        DaemonContext daemonContext = new DaemonContext() {
            @Override
            public DaemonController getController() {
                return null;
            }

            @Override
            public String[] getArguments() {
                return new String[0];
            }
        };
        Assertions.assertThrows(DaemonInitException.class,
            () -> nvaDaemon.init(daemonContext));
    }

    @Test
    void test_no_command_line_args() {
        NvaDaemon nvaDaemon = new NvaDaemon();
        DaemonContext daemonContext = new DaemonContext() {
            @Override
            public DaemonController getController() {
                return null;
            }

            @Override
            public String[] getArguments() {
                return new String[0];
            }
        };

        try {
            nvaDaemon.init(daemonContext);
        } catch (DaemonInitException e) {
            // The DaemonInitException is coded incorrectly, so we have to check for our
            // error message.
            Assertions.assertTrue(e.getMessageWithCause().contains(
                "Error parsing command line arguments"));
        }
    }

    @Test
    void test_no_config_file_specified() {
        NvaDaemon nvaDaemon = new NvaDaemon();
        DaemonContext daemonContext = new DaemonContext() {
            @Override
            public DaemonController getController() {
                return null;
            }

            @Override
            public String[] getArguments() {
                return new String[] { "-c" };
            }
        };

        try {
            nvaDaemon.init(daemonContext);
        } catch (DaemonInitException e) {
            // The DaemonInitException is coded incorrectly, so we have to check for our
            // error message.
            Assertions.assertTrue(e.getMessageWithCause().contains(
                "Error parsing command line arguments"));
        }
    }

    @Test
    void test_non_existent_config_file_specified() {
        NvaDaemon nvaDaemon = new NvaDaemon();
        DaemonContext daemonContext = new DaemonContext() {
            @Override
            public DaemonController getController() {
                return null;
            }

            @Override
            public String[] getArguments() {
                return new String[] { "-c", testOutputDirectory + "/does-not-exist.json" };
            }
        };

        try {
            nvaDaemon.init(daemonContext);
        } catch (DaemonInitException e) {
            // The DaemonInitException is coded incorrectly, so we have to check for our
            // error message.
            Assertions.assertTrue(e.getMessageWithCause().contains(
                "Error reading configuration file"));
        }
    }

    @Test
    void test_valid_config_file_specified() throws DaemonInitException {
        NvaDaemon nvaDaemon = new NvaDaemon();
        DaemonContext daemonContext = new DaemonContext() {
            @Override
            public DaemonController getController() {
                return null;
            }

            @Override
            public String[] getArguments() {
                return new String[] { "-c", testOutputDirectory + "/nvadaemon-sample.json" };
            }
        };

        nvaDaemon.init(daemonContext);
    }
}
