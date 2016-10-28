package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.UserTokenCredentials;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.practices.monitor.ScheduledMonitor;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import com.microsoft.rest.credentials.TokenCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ServiceUnavailableException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class AzureProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(AzureProbeMonitor.class);
    private static final String CLIENT_ID_SETTING="azure.clientId";
    private static final String TENANT_ID_SETTING="azure.tenantId";
    private static final String CLIENT_SECRET_SETTING="azure.clientSecret";
    private static final String PROBE_IP_ADDRESS="probe.ipAddress";
    private static final String PROBE_PORT="probe.port";

    private int failures;
    private Map<String, String> config;
    private SocketChannel channel;

//    private Azure azure;
//
//    private AuthenticationResult getAccessTokenFromUserCredentials() throws Exception {
//        AuthenticationContext context = null;
//        AuthenticationResult result = null;
//        ExecutorService service = null;
//        try {
//            service = Executors.newFixedThreadPool(1);
//            context = new AuthenticationContext(
//                "https://login.microsoftonline.com/" + config.get(TENANT_ID_SETTING),
//                false, service);
//            ClientCredential clientCredential = new ClientCredential(
//                config.get(CLIENT_ID_SETTING),
//                config.get(CLIENT_SECRET_SETTING));
//            Future<AuthenticationResult> future = context.acquireToken(
//                "https://management.azure.com/", clientCredential,
//                null);
//            result = future.get();
//        } finally {
//            service.shutdownNow();
//            if (!service.awaitTermination(3000, TimeUnit.MILLISECONDS)) {
//                service.shutdownNow();
//            }
//        }
//
//        if (result == null) {
//            throw new ServiceUnavailableException(
//                "authentication result was null");
//        }
//        return result;
//    }

    @Override
    public void init(Map<String, String> config) throws Exception {
        Preconditions.checkNotNull(config, "config cannot be null");
        this.config = config;
        failures = 0;
//        AuthenticationResult result = null;
//        try {
//            result = getAccessTokenFromUserCredentials();
//        } catch (Exception e) {
//            log.error("Error getting access token", e);
//        }

//        AzureTokenCredentials credentials = new ApplicationTokenCredentials(
//            config.get(CLIENT_ID_SETTING),
//            config.get(TENANT_ID_SETTING),
//            config.get(CLIENT_SECRET_SETTING),
//            AzureEnvironment.AZURE
//        );


//        // See if it's okhttp
//        // It's okhttp.  If we wait six minutes, shutting down the daemon will
//        // hang for at least 15 seconds (that's when exec:java terminates the main
//        // thread).  But if we wait six minutes and ONE second, it shuts down
//        // properly....awesome.
//        okhttp3.OkHttpClient c = new okhttp3.OkHttpClient.Builder().build();
//        okhttp3.Response response = c.newCall(new okhttp3.Request.Builder()
//            .get().url("http://www.bing.com").build()).execute();
//        response.close();

//        Field executor = okhttp3.ConnectionPool.class.getDeclaredField("executor");
//        executor.setAccessible(true);
//        Executor ex = (Executor)executor.get(null);
//        ExecutorService exs = (ExecutorService)ex;
//        exs.shutdownNow();
//        Field modifier = Field.class.getDeclaredField("modifier");
//        modifier.setAccessible(true);
        //modifier.setInt(executor, executor.getModifiers() & ~Modifier.FINAL);

        //response.body().string();
        //response.body().close();
//        c.dispatcher().executorService().shutdown();
//        c.connectionPool().evictAll();
        //c.dispatcher().executorService().awaitTermination(5000, TimeUnit.MILLISECONDS);
//        try {
//            azure = Azure.configure()
//                .authenticate(credentials)
//                //.authenticate(new TokenCredentials(null, result.getAccessToken()))
//                .withDefaultSubscription();
//        } catch (CloudException | IOException e) {
//            log.error("Exception creating Azure client", e);
//            throw e;
//        }
    }

    @Override
    public boolean probe() {

        try {
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(
                this.config.get(PROBE_IP_ADDRESS), new Integer(this.config.get(PROBE_PORT))));
            channel.close();
            // If this works, we want to reset any previous failures.
            failures = 0;
        } catch (IOException e) {
            log.info("probe() threw an exception", e);
            failures++;
        }

        return failures < 5;
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        failures = 0;
//        try {
//            // Do something with Azure
//            PagedList<ResourceGroup> resourceGroups = azure.resourceGroups().list();
//            resourceGroups.loadAll();
//            for (ResourceGroup resourceGroup : resourceGroups) {
//                log.debug("Resource group: " + resourceGroup);
//            }
//        } catch (CloudException | IOException e) {
//            log.error("Error executing Azure call", e);
//        }
//////        } catch (InterruptedException e) {
//////            log.error("Long running execute interrupted");
//////        }
    }

    @Override
    public int getTime() {
        return 3000;
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() throws IOException {
        //azure = null;
    }
}
