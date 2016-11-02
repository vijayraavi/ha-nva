package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.practices.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AzureProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(AzureProbeMonitor.class);
    private static final String CLIENT_ID_SETTING="azure.clientId";
    private static final String TENANT_ID_SETTING="azure.tenantId";
    private static final String CLIENT_SECRET_SETTING="azure.clientSecret";
    private static final String PROBE_IP_ADDRESS="probe.ipAddress";
    private static final String PROBE_PORT="probe.port";

    private static final String AZURE_RESOURCE_GROUP="probe.resourceGroup";
    private static final String NVA_PUBLIC_IP_ADDRESS="probe.publicIpAddress";
    private static final String NVA_ROUTE_TABLE="probe.routeTable";
    private static final String NVA_ROUTE_TABLE_ROUTE="probe.routeTableRoute";
    //private static final ArrayList<String> networkInterfaces = new ArrayList<>();
    private final ArrayList<NvaNetworkConfig> networkConfigurations = new ArrayList<>();

    private int failures;
    private Map<String, String> config;
    private SocketChannel channel;
    private Azure azure;
    private int currentNetworkInterfaceIndex = 0;

    private int currentNvaIndex = 0;
    private PublicIpAddress publicIpAddress = null;
    private RouteTable routeTable = null;
    private OperatingMode operatingMode;
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

    private static final class NvaNetworkConfig {
        private String publicIpNetworkInterface;
        private String routeNetworkInterface;

        private NvaNetworkConfig(String publicIpNetworkInterface, String routeNetworkInterface) {
            //Preconditions.checkNotNull(publicIpNetworkInterface, "publicIpNetworkInterface cannot be null");
            //Preconditions.checkNotNull(routeNetworkInterface, "routeNetworkInterface cannot be null");
            // This will only be invalid if both are null
            if ((publicIpNetworkInterface == null) && (routeNetworkInterface == null)) {
                throw new IllegalArgumentException(
                    "publicIpNetworkInterface and routeNetworkInterface cannot both be null");
            }
            this.publicIpNetworkInterface = publicIpNetworkInterface;
            this.routeNetworkInterface = routeNetworkInterface;
        }

        private String getPublicIpNetworkInterface() { return this.publicIpNetworkInterface; }

        private String getRouteNetworkInterface() { return this.routeNetworkInterface; }

        private static NvaNetworkConfig create(OperatingMode operatingMode,
            String publicIpNetworkInterface, String routeNetworkInterface) {
            if (((operatingMode == OperatingMode.PIP_AND_ROUTE) &&
                ((publicIpNetworkInterface == null) || (routeNetworkInterface == null))) ||
                ((operatingMode == OperatingMode.ONLY_PIP) && (publicIpNetworkInterface == null)) ||
                ((operatingMode == OperatingMode.ONLY_ROUTE) && (routeNetworkInterface == null))){
                throw new IllegalArgumentException("Invalid configuration entry.  OperatingMode: " + operatingMode +
                " publicIpNetworkInterface: " + publicIpNetworkInterface + " routeNetworkInterface: " +
                routeNetworkInterface);
            }

            return new NvaNetworkConfig(publicIpNetworkInterface, routeNetworkInterface);
        }
    }

    private enum OperatingMode {
        PIP_AND_ROUTE,
        ONLY_PIP,
        ONLY_ROUTE
    }

    private int indexOfPublicIpNetworkInterface(String name) {
        Preconditions.checkNotNull(name, "name cannot be null");
        for (int i = 0; i < networkConfigurations.size(); i++) {
            if (name.equals(networkConfigurations.get(i).getPublicIpNetworkInterface())) {
                return i;
            }
        }

        return -1;
    }

    private int indexOfRouteNetworkInterface(String privateIpAddress) {
        Preconditions.checkNotNull(privateIpAddress, "privateIpAddress cannot be null");
        String resourceGroupName = this.config.get(AZURE_RESOURCE_GROUP);
        for (int i = 0; i < networkConfigurations.size(); i++) {
//            if (name.equals(networkConfigurations.get(i).getRouteNetworkInterface())) {
//                return i;
//            }
            NetworkInterface routeNetworkInterface = azure.networkInterfaces()
                .getByGroup(resourceGroupName,
                    networkConfigurations.get(i).getRouteNetworkInterface());
            if (privateIpAddress.equals(routeNetworkInterface.primaryPrivateIp())) {
                return i;
            }
        }

        return -1;
    }

    private OperatingMode deduceOperatingMode(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix cannot be null");
        // We essentially have three modes of operation.  Rather than making the user specify
        // the mode, we can extrapolate from the configuration entries.  We will drive this
        // based on the first NVA entry for the probe.  We will validate the following entries
        // with respect to the first one.
        // Mode #1 - publicIpAddressNetworkInterface and routeNetworkInterface are specified.
        // Mode #2 - Only publicIpAddressNetworkInterface is specified
        // Mode #3 - Only routeNetworkInterface is specified.
        String publicIpNetworkInterface = this.config.get(prefix + ".publicIpNetworkInterface");
        String routeNetworkInterface = this.config.get(prefix + ".routeNetworkInterface");
        if ((publicIpNetworkInterface != null) && (routeNetworkInterface != null)) {
            return OperatingMode.PIP_AND_ROUTE;
        } else if ((publicIpNetworkInterface != null) && (routeNetworkInterface != null)) {
            return OperatingMode.ONLY_PIP;
        } else if ((publicIpNetworkInterface == null) && (routeNetworkInterface != null)) {
            return OperatingMode.ONLY_ROUTE;
        } else {
            throw new IllegalArgumentException("Invalid NVA configuration entry.");
        }
    }

    private void readConfiguration() {

        ArrayList<String> prefixes = new ArrayList<>();
        for (String key : this.config.keySet()) {
            if (key.startsWith("probe.nva")) {
                String prefix = key.substring(0, key.lastIndexOf("."));
                if (!prefixes.contains(prefix)) {
                    prefixes.add(prefix);
                }
            }
        }

        if (prefixes.size() == 0) {
            throw new IllegalArgumentException("No NVA configuration entries found");
        }

        this.operatingMode = deduceOperatingMode(prefixes.get(0));
        for (String prefix : prefixes) {
            this.networkConfigurations.add(NvaNetworkConfig.create(this.operatingMode,
                this.config.get(prefix + ".publicIpNetworkInterface"),
                this.config.get(prefix + ".routeNetworkInterface")
            ));
        }

//        for (Map.Entry<String, String> entry : this.config.entrySet()) {
//            if (entry.getKey().startsWith("probe.nva")) {
//                networkInterfaces.add(entry.getValue());
//            }
//        }
    }
    private void initializeAzure() throws Exception {
        AzureTokenCredentials credentials = new ApplicationTokenCredentials(
            config.get(CLIENT_ID_SETTING),
            config.get(TENANT_ID_SETTING),
            config.get(CLIENT_SECRET_SETTING),
            AzureEnvironment.AZURE
        );
        try {
            CertificateCredentials certificateCredentials = new CertificateCredentials(
                config.get(CLIENT_ID_SETTING), config.get(TENANT_ID_SETTING),
                AzureEnvironment.AZURE, this.config
            );
            azure = Azure.configure()
                //.authenticate(credentials)
                .authenticate(certificateCredentials)
                .withDefaultSubscription();
        } catch (CloudException | IOException e) {
            log.error("Exception creating Azure client", e);
            throw e;
        }
    }

    private void getCurrentNva() {
        // We need to find out the current setup of the NVAs
        int pipNetworkInterfaceIndex = -1;
        int routeNetworkInterfaceIndex = -1;
        String resourceGroupName = this.config.get(AZURE_RESOURCE_GROUP);
        String pipName = this.config.get(NVA_PUBLIC_IP_ADDRESS);
        String routeTableName = this.config.get(NVA_ROUTE_TABLE);
        String routeName = this.config.get(NVA_ROUTE_TABLE_ROUTE);
        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) {
            PublicIpAddress publicIpAddress =
                azure.publicIpAddresses().getByGroup(resourceGroupName, pipName);
            if (publicIpAddress == null) {
                throw new IllegalArgumentException("Invalid PublicIpAddress name: " + pipName);
            }

            if (!publicIpAddress.hasAssignedNetworkInterface()) {
                throw new IllegalArgumentException(
                    "PublicIpAddress " + pipName +" is not assigned to a NetworkInterface");
            }

            NicIpConfiguration nicIpConfiguration =
                publicIpAddress.getAssignedNetworkInterfaceIpConfiguration();

            NetworkInterface networkInterface = nicIpConfiguration.parent();
            log.debug("NetworkInterface: " + networkInterface.name() + " PublicIpAddress: " + pipName);
            //currentNetworkInterfaceIndex = networkInterfaces.indexOf(networkInterface.name());
            pipNetworkInterfaceIndex = indexOfPublicIpNetworkInterface(networkInterface.name());
            if (pipNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException("NetworkInterface " + networkInterface.name() +
                    " was not found in the list of valid network interfaces");
            }

            this.publicIpAddress = publicIpAddress;
            // If everything goes okay, this will be valid
            this.currentNvaIndex = pipNetworkInterfaceIndex;
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) {
            RouteTable routeTable = azure.routeTables().getByGroup(resourceGroupName,
                routeTableName);
            if (routeTable == null) {
                throw new IllegalArgumentException("Invalid RouteTable name: " + routeTableName);
            }

            Route route = routeTable.routes().get(routeName);
            if (route == null) {
                throw new IllegalArgumentException("Invalid Route name: " + routeName);
            }

            routeNetworkInterfaceIndex = indexOfRouteNetworkInterface(route.nextHopIpAddress());
            if (routeNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException("NetworkInterface for route " + route.name() +
                    " was not found in the list of valid network interfaces");
            }

            this.routeTable = routeTable;
            // If everything goes okay, this will be valid
            this.currentNvaIndex = routeNetworkInterfaceIndex;
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) &&
            (pipNetworkInterfaceIndex != routeNetworkInterfaceIndex)) {
            // The indexes need to match here.  Otherwise, the pip and route are pointing at different NVAs
            // and the currentNvaIndex is wrong
            throw new IllegalArgumentException("Current PublicIpAddress and Route point to different NVAs");
        }
    }

    private void getCurrent() {
        String resourceGroupName = this.config.get(AZURE_RESOURCE_GROUP);
        String pipName = this.config.get(NVA_PUBLIC_IP_ADDRESS);
        String routeTableName = this.config.get(NVA_ROUTE_TABLE);
        String routeName = this.config.get(NVA_ROUTE_TABLE_ROUTE);
        PublicIpAddress publicIpAddress =
            azure.publicIpAddresses().getByGroup(resourceGroupName, pipName);
        if (publicIpAddress == null) {
            throw new IllegalArgumentException("Invalid PublicIpAddress name: " + pipName);
        }

        if (!publicIpAddress.hasAssignedNetworkInterface()) {
            throw new IllegalArgumentException(
                "PublicIpAddress " + pipName +" is not assigned to a NetworkInterface");
        }

        NicIpConfiguration nicIpConfiguration =
            publicIpAddress.getAssignedNetworkInterfaceIpConfiguration();

        NetworkInterface networkInterface = nicIpConfiguration.parent();
        log.debug("NetworkInterface: " + networkInterface.name() + " PublicIpAddress: " + pipName);
        //currentNetworkInterfaceIndex = networkInterfaces.indexOf(networkInterface.name());
        currentNetworkInterfaceIndex = indexOfPublicIpNetworkInterface(networkInterface.name());
        if (currentNetworkInterfaceIndex == -1) {
            throw new IllegalArgumentException("NetworkInterface " + networkInterface.name() +
                " was not found in the list of valid network interfaces");
        }

        RouteTable routeTable = azure.routeTables().getByGroup(resourceGroupName,
            routeTableName);
        if (routeTable == null) {
            throw new IllegalArgumentException("Invalid RouteTable name: " + routeTableName);
        }

        Route route = routeTable.routes().get(routeName);
        if (route == null) {
            throw new IllegalArgumentException("Invalid Route name: " + routeName);
        }

        // Save the public ip address
        this.publicIpAddress = publicIpAddress;
        // Save the route table
        this.routeTable = routeTable;
    }

    private NetworkInterface getNetworkInterfaceIn(int index) throws
        IndexOutOfBoundsException, IllegalArgumentException {
        //if ((index < 0) || (index >= networkInterfaces.size())) {
        if ((index < 0) || (index >= networkConfigurations.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " is invalid for network interfaces");
        }
        NetworkInterface networkInterface = this.azure.networkInterfaces().getByGroup(
            //this.config.get(AZURE_RESOURCE_GROUP), networkInterfaces.get(index));
            this.config.get(AZURE_RESOURCE_GROUP), networkConfigurations.get(index).getPublicIpNetworkInterface());
        if (networkInterface == null) {
            throw new IllegalArgumentException("Error getting NetworkInterface " + index);
        }

        return networkInterface;
    }

    private NetworkInterface getNetworkInterfaceOut(int index) throws
        IndexOutOfBoundsException, IllegalArgumentException {
        //if ((index < 0) || (index >= networkInterfaces.size())) {
        if ((index < 0) || (index >= networkConfigurations.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " is invalid for network interfaces");
        }
        NetworkInterface networkInterface = this.azure.networkInterfaces().getByGroup(
            //this.config.get(AZURE_RESOURCE_GROUP), networkInterfaces.get(index));
            this.config.get(AZURE_RESOURCE_GROUP), networkConfigurations.get(index).getRouteNetworkInterface());
        if (networkInterface == null) {
            throw new IllegalArgumentException("Error getting NetworkInterface " + index);
        }

        return networkInterface;
    }

    private void migrateAzureResources() {
        int nextNvaIndex = (this.currentNvaIndex + 1) % this.networkConfigurations.size();
        NvaNetworkConfig currentNvaNetworkConfig =
            this.networkConfigurations.get(this.currentNvaIndex);
        NvaNetworkConfig nextNvaNetworkConfig = this.networkConfigurations.get(nextNvaIndex);
        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) {
            migratePublicIpAddress(currentNvaNetworkConfig, nextNvaNetworkConfig);
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) {
            migrateRoute(currentNvaNetworkConfig, nextNvaNetworkConfig);
        }

        this.currentNvaIndex = nextNvaIndex;
    }

    private void migrateRoute(NvaNetworkConfig fromNvaNetworkConfig,
                              NvaNetworkConfig toNvaNetworkConfig) {
        Preconditions.checkNotNull(fromNvaNetworkConfig, "fromNvaNetworkConfig cannot be null");
        Preconditions.checkNotNull(toNvaNetworkConfig, "toNvaNetworkConfig cannot be null");
        NetworkInterface fromNetworkInterface = null;
        NetworkInterface toNetworkInterface = null;

        log.debug("Getting network interface " + fromNvaNetworkConfig.getRouteNetworkInterface());
        fromNetworkInterface = this.azure.networkInterfaces().getByGroup(
            this.config.get(AZURE_RESOURCE_GROUP), fromNvaNetworkConfig.getRouteNetworkInterface());
        log.debug("Got network interface " + fromNvaNetworkConfig.getRouteNetworkInterface());

        if (fromNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting from network interface: " +
                fromNvaNetworkConfig.getRouteNetworkInterface());
        }

        log.debug("Getting network interface " + toNvaNetworkConfig.getRouteNetworkInterface());
        toNetworkInterface = this.azure.networkInterfaces().getByGroup(
            this.config.get(AZURE_RESOURCE_GROUP), toNvaNetworkConfig.getRouteNetworkInterface());
        log.debug("Got network interface " + toNvaNetworkConfig.getRouteNetworkInterface());
        if (toNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting to network interface: " +
                toNvaNetworkConfig.getRouteNetworkInterface());
        }

        // Update the route table
        log.debug("Updating route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + toNetworkInterface.primaryPrivateIp());
        routeTable.update()
            .updateRoute(this.config.get(NVA_ROUTE_TABLE_ROUTE))
            .withNextHopToVirtualAppliance(toNetworkInterface.primaryPrivateIp())
            .parent()
            .apply();
        log.debug("Updated route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + toNetworkInterface.primaryPrivateIp());
        //this.publicIpAddress = this.publicIpAddress.refresh();
    }

    private void migratePublicIpAddress(NvaNetworkConfig fromNvaNetworkConfig,
                                        NvaNetworkConfig toNvaNetworkConfig) {
        Preconditions.checkNotNull(fromNvaNetworkConfig, "fromNvaNetworkConfig cannot be null");
        Preconditions.checkNotNull(toNvaNetworkConfig, "toNvaNetworkConfig cannot be null");
        NetworkInterface fromNetworkInterface = null;
        NetworkInterface toNetworkInterface = null;

        log.debug("Getting network interface " + fromNvaNetworkConfig.getPublicIpNetworkInterface());
        fromNetworkInterface = this.azure.networkInterfaces().getByGroup(
            this.config.get(AZURE_RESOURCE_GROUP), fromNvaNetworkConfig.getPublicIpNetworkInterface());
        log.debug("Got network interface " + fromNvaNetworkConfig.getPublicIpNetworkInterface());

        if (fromNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting from network interface: " +
                fromNvaNetworkConfig.getPublicIpNetworkInterface());
        }

        log.debug("Getting network interface " + toNvaNetworkConfig.getPublicIpNetworkInterface());
        toNetworkInterface = this.azure.networkInterfaces().getByGroup(
            this.config.get(AZURE_RESOURCE_GROUP), toNvaNetworkConfig.getPublicIpNetworkInterface());
        log.debug("Got network interface " + toNvaNetworkConfig.getPublicIpNetworkInterface());
        if (toNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting to network interface: " +
                toNvaNetworkConfig.getPublicIpNetworkInterface());
        }

        log.debug("Removing public ip address from network interface " + fromNetworkInterface.name());
        // Swap the pip
        fromNetworkInterface.update()
            .withoutPrimaryPublicIpAddress()
            .apply();
        log.debug("Public ip address removed from network interface " + fromNetworkInterface.name());

        log.debug("Adding public ip address to network interface " + toNetworkInterface.name());
        toNetworkInterface.update()
            .withExistingPrimaryPublicIpAddress(this.publicIpAddress)
            .apply();
        log.debug("Added public ip address to network interface " + toNetworkInterface.name());
    }

    private void migratePublicIpAddress2() {
        //int nextNetworkInterfaceIndex = (this.currentNetworkInterfaceIndex + 1) % networkInterfaces.size();
        int nextNetworkInterfaceIndex = (this.currentNetworkInterfaceIndex + 1) % networkConfigurations.size();
        NetworkInterface currentNetworkInterface = null;
        NetworkInterface nextNetworkInterface = null;
        NetworkInterface nextNetworkInterfaceOut = null;

        try {
            log.debug("Getting current network interface");
            currentNetworkInterface = getNetworkInterfaceIn(this.currentNetworkInterfaceIndex);
            log.debug("Got current network interface");
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Error getting current network interface: " +
                this.currentNetworkInterfaceIndex);
        }

        try {
            log.debug("Getting next network interface");
            nextNetworkInterface = getNetworkInterfaceIn(nextNetworkInterfaceIndex);
            log.debug("Got next network interface");
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Error getting next network interface: " +
                nextNetworkInterfaceIndex);
        }

        try {
            log.debug("Getting next network interface out");
            nextNetworkInterfaceOut = getNetworkInterfaceOut(nextNetworkInterfaceIndex);
            log.debug("Got next network interface out");
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Error getting next network interface out: " +
                nextNetworkInterfaceIndex);
        }

        log.debug("Removing public ip address from current network interface");
        // Swap the pip
        currentNetworkInterface.update()
            .withoutPrimaryPublicIpAddress()
            .apply();
        log.debug("Public ip address removed from current network interface");

        // Not sure we even need this, but let's keep things up to date.
        //this.publicIpAddress = this.publicIpAddress.refresh();
        log.debug("Adding public ip address to next network interface");
        nextNetworkInterface.update()
            .withExistingPrimaryPublicIpAddress(this.publicIpAddress)
            .apply();
        log.debug("Added public ip address to next network interface");

        // Update the route table
        log.debug("Updating route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + nextNetworkInterface.primaryPrivateIp());
        routeTable.update()
            .updateRoute(this.config.get(NVA_ROUTE_TABLE_ROUTE))
            .withNextHopToVirtualAppliance(nextNetworkInterface.primaryPrivateIp())
            .parent()
            .apply();
        log.debug("Updated route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + nextNetworkInterface.primaryPrivateIp());
        //this.publicIpAddress = this.publicIpAddress.refresh();
        this.currentNetworkInterfaceIndex = nextNetworkInterfaceIndex;
    }

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

        initializeAzure();
        readConfiguration();
        //getCurrent();
        getCurrentNva();
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

        return failures < 3;
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        migrateAzureResources();
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
