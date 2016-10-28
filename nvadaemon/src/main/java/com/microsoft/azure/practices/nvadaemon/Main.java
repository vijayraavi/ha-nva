package com.microsoft.azure.practices.nvadaemon;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private void runDaemon(final String[] args) throws Exception {
        DaemonContext context = new DaemonContext() {
            public DaemonController getController() {
                return null;
            }

            public String[] getArguments() {
                return args;
            }
        };

        NvaDaemon daemon = new NvaDaemon();
        try {
            daemon.init(context);
            daemon.start();
            // Works
            //long milliseconds = 7 * 60 * 1000;
            // Works
            //long milliseconds = ((6 * 60) + 30) * 1000;
            // Works
            //long milliseconds = ((6 * 60) + 15) * 1000;
            // Works
            //long milliseconds = ((6 * 60) + 7) * 1000;
            // Works
            //long milliseconds = ((6 * 60) + 3) * 1000;
            // Works
            //long milliseconds = ((6 * 60) + 1) * 1000;
            // Doesn't work
//            long milliseconds = 6 * 60 * 1000;
//            System.out.println("Sleeping for " + milliseconds + " seconds");
//            Thread.sleep(milliseconds);
            System.out.println("Press enter to quit...");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (Exception e) {
            log.error("Unexpected exception, exiting abnormally", e);
            throw e;
        } finally {
            daemon.stop();
            daemon.destroy();
        }
    }

    public static void main(final String[] args) throws Exception {
        Main main = new Main();
        main.runDaemon(args);
    }
}
