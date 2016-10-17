package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.azure.RestClient;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.batch.BatchAccounts;
import com.microsoft.azure.management.compute.AvailabilitySets;
import com.microsoft.azure.management.compute.VirtualMachineImages;
import com.microsoft.azure.management.compute.VirtualMachineScaleSets;
import com.microsoft.azure.management.compute.VirtualMachines;
import com.microsoft.azure.management.keyvault.Vaults;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.management.redis.RedisCaches;
import com.microsoft.azure.management.resources.*;
import com.microsoft.azure.management.resources.fluentcore.arm.collection.SupportsGettingById;
import com.microsoft.azure.management.resources.fluentcore.arm.models.GroupableResource;
import com.microsoft.azure.management.storage.StorageAccounts;
import com.microsoft.azure.management.storage.Usages;
import com.microsoft.azure.management.trafficmanager.TrafficManagerProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public interface AzureClient extends AutoCloseable {
    String subscriptionId();

    ResourceGroups resourceGroups();

    Deployments deployments();

    GenericResources genericResources();

    Features features();

    Providers providers();

    PolicyDefinitions policyDefinitions();

    PolicyAssignments policyAssignments();

    StorageAccounts storageAccounts();

    Usages storageUsages();

    AvailabilitySets availabilitySets();

    Networks networks();

    RouteTables routeTables();

    LoadBalancers loadBalancers();

    NetworkSecurityGroups networkSecurityGroups();

    VirtualMachines virtualMachines();

    VirtualMachineScaleSets virtualMachineScaleSets();

    VirtualMachineImages virtualMachineImages();

    PublicIpAddresses publicIpAddresses();

    NetworkInterfaces networkInterfaces();

    Vaults vaults();

    BatchAccounts batchAccounts();

    TrafficManagerProfiles trafficManagerProfiles();

    RedisCaches redisCaches();

    boolean checkExistenceById(String id);

    <T extends GroupableResource> T getById(String id, SupportsGettingById<T> resources);

    NetworkInterface getNetworkInterfaceById(String id);

    PublicIpAddress getPublicIpAddressById(String id);

    RouteTable getRouteTableById(String id);

    static AzureClient create(AzureTokenCredentials tokenCredentials,
                                     String subscriptionId) {
        Preconditions.checkNotNull(tokenCredentials, "tokenCredentials cannot be null");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(subscriptionId),
            "subscriptionId cannot be null or empty");
        RestClient restClient = tokenCredentials
            .getEnvironment()
            .newRestClientBuilder()
            .withCredentials(tokenCredentials)
            .build();
        Azure azure = Azure.authenticate(restClient, tokenCredentials.getDomain())
            .withSubscription(subscriptionId);
        return new AzureClientImpl(azure, restClient);
    }

    final class AzureClientImpl implements AzureClient {
        private static final Logger log = LoggerFactory.getLogger(AzureClient.class);
        private static final int DEFAULT_SHUTDOWN_TIMEOUT_MS = 5000;
        private final Azure azure;
        private final RestClient restClient;
        private final int shutdownTimeoutMs;

        private AzureClientImpl(Azure azure, RestClient restClient) {
            this(azure, restClient, DEFAULT_SHUTDOWN_TIMEOUT_MS);
        }
        private AzureClientImpl(Azure azure, RestClient restClient, int shutdownTimeoutMs) {
            this.azure = Preconditions.checkNotNull(azure, "azure cannot be null");
            this.restClient = Preconditions.checkNotNull(restClient, "restClient cannot be null");
            this.shutdownTimeoutMs = shutdownTimeoutMs;
        }

        public NetworkInterface getNetworkInterfaceById(String id) {
            return getById(id, this.networkInterfaces());
        }

        public PublicIpAddress getPublicIpAddressById(String id) {
            return getById(id, this.publicIpAddresses());
        }

        public RouteTable getRouteTableById(String id) {
            return getById(id, this.routeTables());
        }

        public boolean checkExistenceById(String id) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "id cannot be null or empty");
            return this.azure.genericResources().getById(id) != null;
        }
        public <T extends GroupableResource> T getById(String id, SupportsGettingById<T> resources) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "id cannot be null or empty");
            Preconditions.checkNotNull(resources, "resources cannot be null");
            log.debug("Getting resource: " + id);

            T resource = resources.getById(id);
            if (resource == null) {
                throw new IllegalArgumentException("Error getting resource: " + id);
            }

            log.debug("Got resource: " + id);
            return resource;
        }

        @Override
        public void close() throws Exception {
            if (restClient != null) {
                restClient.httpClient().dispatcher().executorService().shutdown();
                try {
                    // This is needed to work around an okio threading issue.  By shutting it
                    // down manually here, it only takes one minute for okio to shutdown the idle connection thread.
                    if (!restClient
                        .httpClient()
                        .dispatcher()
                        .executorService()
                        .awaitTermination(this.shutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
                        // We have been waiting five seconds and the executorService hasn't shutdown, so
                        // try to force it.
                        restClient.httpClient().dispatcher().executorService().shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.debug("AzureProbeMonitor.close() interrupted");
                    Thread.currentThread().interrupt();
                }

                restClient.httpClient().connectionPool().evictAll();
            }
        }

        public String subscriptionId() { return this.azure.subscriptionId(); }

        public ResourceGroups resourceGroups() {
            return this.azure.resourceGroups();
        }

        public Deployments deployments() {
            return this.azure.deployments();
        }

        public GenericResources genericResources() {
            return this.azure.genericResources();
        }

        public Features features() {
            return this.azure.features();
        }

        public Providers providers() {
            return this.azure.providers();
        }

        public PolicyDefinitions policyDefinitions() {
            return this.azure.policyDefinitions();
        }

        public PolicyAssignments policyAssignments() {
            return this.azure.policyAssignments();
        }

        public StorageAccounts storageAccounts() {
            return this.azure.storageAccounts();
        }

        public Usages storageUsages() { return this.azure.storageUsages(); }

        public AvailabilitySets availabilitySets() {
            return this.azure.availabilitySets();
        }

        public Networks networks() {
            return this.azure.networks();
        }

        public RouteTables routeTables() {
            return this.azure.routeTables();
        }

        public LoadBalancers loadBalancers() {
            return this.azure.loadBalancers();
        }

        public NetworkSecurityGroups networkSecurityGroups() {
            return this.azure.networkSecurityGroups();
        }

        public VirtualMachines virtualMachines() {
            return this.azure.virtualMachines();
        }

        public VirtualMachineScaleSets virtualMachineScaleSets() {
            return this.azure.virtualMachineScaleSets();
        }

        public VirtualMachineImages virtualMachineImages() {
            return this.azure.virtualMachineImages();
        }

        public PublicIpAddresses publicIpAddresses() {
            return this.azure.publicIpAddresses();
        }

        public NetworkInterfaces networkInterfaces() {
            return this.azure.networkInterfaces();
        }

        public Vaults vaults() {
            return this.azure.vaults();
        }

        public BatchAccounts batchAccounts() {
            return this.azure.batchAccounts();
        }

        public TrafficManagerProfiles trafficManagerProfiles() {
            return this.azure.trafficManagerProfiles();
        }

        public RedisCaches redisCaches() {
            return this.azure.redisCaches();
        }
    }
}