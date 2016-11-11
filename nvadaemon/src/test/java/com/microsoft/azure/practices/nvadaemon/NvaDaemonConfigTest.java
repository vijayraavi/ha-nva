package com.microsoft.azure.practices.nvadaemon;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Executable;
import org.junit.jupiter.api.Test;
import com.microsoft.azure.practices.nvadaemon.NvaDaemonConfig.ConfigException;

public class NvaDaemonConfigTest {
    @Test
    void test_invalid_command_line_args() {
        Assertions.assertThrows(ConfigException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                NvaDaemonConfig.parseArguments(new String[] { });
            }
        });
    }
}
