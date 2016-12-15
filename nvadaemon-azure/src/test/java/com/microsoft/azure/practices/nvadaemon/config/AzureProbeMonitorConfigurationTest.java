package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.practices.nvadaemon.AzureClient;
import com.microsoft.azure.practices.nvadaemon.AzureProbeMonitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzureProbeMonitorConfigurationTest {
    public static final AzureConfiguration azureConfiguration = new AzureConfiguration(
        "subscription-id", new AzureConfiguration.ServicePrincipal("tenant-id", "client-id",
        "client-secret", null));

    public static final List<NvaConfiguration> nvaConfigurations = Arrays.asList(
        new NvaConfiguration("nva-1-probe-network-interface", 1234,
            Arrays.asList(
                new NamedResourceId("nic1", "nva1-network-interface-id1"),
                new NamedResourceId("nic2", "nva1-network-interface-id2"))
        ),
        new NvaConfiguration("nva-2-probe-network-interface", 1234,
            Arrays.asList(
                new NamedResourceId("nic1", "nva2-network-interface-id1"),
                new NamedResourceId("nic2", "nva2-network-interface-id2"))
        )
    );

    public static final List<String> routeTables =
        Arrays.asList("route-table-id1", "route-table-id2", "route-table-id3");

    public static final List<NamedResourceId> publicIpAddresses =
        Arrays.asList(new NamedResourceId("nic1", "public-ip-address-id1"));

    @Test
    void test_null_azure_configuration() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new AzureProbeMonitorConfiguration(null, null, null, null, null, null, null));
    }

    @Test
    void test_null_nva_configurations() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                null, null, null, null, null, null));
    }

    @Test
    void test_empty_nva_configurations() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                new ArrayList<>(), null, null, null, null, null));
    }

    @Test
    void test_network_interface_name_mismatch() {
        List<NvaConfiguration> invalidNvaConfigurations = Arrays.asList(
            new NvaConfiguration("nva-1-probe-network-interface", 1234,
                Arrays.asList(
                    new NamedResourceId("nic1", "nva1-network-interface-id1"),
                    new NamedResourceId("nic2", "nva1-network-interface-id2"))
            ),
            new NvaConfiguration("nva-2-probe-network-interface", 1234,
                Arrays.asList(
                    new NamedResourceId("nic1", "nva2-network-interface-id1"),
                    new NamedResourceId("nic3", "nva2-network-interface-id2"))
            )
        );

        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                invalidNvaConfigurations, null, null, null, null, null));
    }

    @Test
    void test_null_route_tables_and_null_public_ip_addresses_configurations() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, null, null, null, null, null));
    }

    @Test
    void test_empty_route_tables_and_null_public_ip_addresses_configurations() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, new ArrayList<>(), null, null, null, null));
    }

    @Test
    void test_null_route_tables_and_empty_public_ip_addresses_configurations() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, null, new ArrayList<>(), null, null, null));
    }

    @Test
    void test_empty_route_tables_and_empty_public_ip_addresses_configurations() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, new ArrayList<>(), new ArrayList<>(), null, null, null));
    }

    @Test
    void test_duplicate_route_ids() {
        List<String> invalidRouteTableIds =
            Arrays.asList("route-table-id1", "route-table-id2", "route-table-id2");
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, invalidRouteTableIds, null, null, null, null));
    }

    @Test
    void test_duplicate_public_ip_address_names() {
        List<NamedResourceId> invalidPublicIpAddresses =
            Arrays.asList(new NamedResourceId("nic1", "public-ip-address-id1"),
                new NamedResourceId("nic1", "public-ip-address-id2"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, null, invalidPublicIpAddresses, null, null, null));
    }

    @Test
    void test_duplicate_public_ip_address_ids() {
        List<NamedResourceId> invalidPublicIpAddresses =
            Arrays.asList(new NamedResourceId("nic1", "public-ip-address-id1"),
                new NamedResourceId("nic2", "public-ip-address-id1"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, null, invalidPublicIpAddresses, null, null, null));
    }

    @Test
    void test_null_defaults() {
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);
        Assertions.assertEquals(AzureProbeMonitorConfiguration.DEFAULT_NUMBER_OF_FAILURES_THRESHOLD,
            azureProbeMonitorConfiguration.getNumberOfFailuresThreshold());
        Assertions.assertEquals(AzureProbeMonitorConfiguration.DEFAULT_PROBE_CONNECT_TIMEOUT,
            azureProbeMonitorConfiguration.getProbeConnectTimeout());
        Assertions.assertEquals(AzureProbeMonitorConfiguration.DEFAULT_PROBE_POLLING_INTERVAL,
            azureProbeMonitorConfiguration.getProbePollingInterval());
    }

    @Test
    void test_valid_parameters() {
        Integer numberOfFailuresThreshold = 5;
        Integer probeConnectTimeout = 5000;
        Integer probePollingInterval = 5000;
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                numberOfFailuresThreshold, probeConnectTimeout, probePollingInterval);
        Assertions.assertEquals(azureConfiguration,
            azureProbeMonitorConfiguration.getAzureConfiguration());
        Assertions.assertEquals(nvaConfigurations,
            azureProbeMonitorConfiguration.getNvaConfigurations());
        Assertions.assertEquals(routeTables,
            azureProbeMonitorConfiguration.getRouteTables());
        Assertions.assertEquals(publicIpAddresses,
            azureProbeMonitorConfiguration.getPublicIpAddresses());
        Assertions.assertEquals(numberOfFailuresThreshold.intValue(),
            azureProbeMonitorConfiguration.getNumberOfFailuresThreshold());
        Assertions.assertEquals(probeConnectTimeout.intValue(),
            azureProbeMonitorConfiguration.getProbeConnectTimeout());
        Assertions.assertEquals(probePollingInterval.intValue(),
            azureProbeMonitorConfiguration.getProbePollingInterval());
    }

    @Test
    void test_null_monitor_configuration() {
        Assertions.assertThrows(NullPointerException.class,
            () -> AzureProbeMonitorConfiguration.create(null));
    }

    @Test
    void test_create_invalid_settings() throws ConfigurationException {
        ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);
        Map<String, Object> settings = mapper.convertValue(azureProbeMonitorConfiguration,
            new TypeReference<Map<String, Object>>(){});
        settings.remove("azure");
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration(
            "com.company.Monitor", settings);
        Assertions.assertThrows(ConfigurationException.class,
            () -> AzureProbeMonitorConfiguration.create(monitorConfiguration));
    }

    @Test
    void test_create_settings() throws ConfigurationException {
        ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);
        Map<String, Object> settings = mapper.convertValue(azureProbeMonitorConfiguration,
            new TypeReference<Map<String, Object>>(){});
        MonitorConfiguration monitorConfiguration = new MonitorConfiguration(
            "com.company.Monitor", settings);
        AzureProbeMonitorConfiguration result =
            AzureProbeMonitorConfiguration.create(monitorConfiguration);
        Assertions.assertNotNull(result);
    }

    @Test
    void test_validate_null_azure_client() {
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);
        Assertions.assertThrows(NullPointerException.class,
            () -> azureProbeMonitorConfiguration.validate(null));
    }
    @Test
    void test_validate_duplicate_probe() throws ConfigurationException {
        String probeNetworkInterfacePrimaryPrivateIp = "127.0.0.1";
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);

        AzureClient azureClient = mock(AzureClient.class);
        when(azureClient.checkExistenceById(anyString()))
            .thenReturn(true);
        NetworkInterface probeNetworkInterface = mock(NetworkInterface.class);
        when(probeNetworkInterface.primaryPrivateIp())
            .thenReturn(probeNetworkInterfacePrimaryPrivateIp);
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(probeNetworkInterface);

        Assertions.assertThrows(ConfigurationException.class,
            () -> azureProbeMonitorConfiguration.validate(azureClient));
    }

    @Test
    void test_validate_invalid_public_ip_address() throws ConfigurationException {
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);

        AzureClient azureClient = mock(AzureClient.class);
        when(azureClient.checkExistenceById(contains("network-interface")))
            .thenReturn(true);
        NetworkInterface networkInterface = mock(NetworkInterface.class);
        when(networkInterface.primaryPrivateIp())
            .thenReturn("127.0.0.1")
            .thenReturn("127.0.0.2");
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(networkInterface);

        Assertions.assertThrows(ConfigurationException.class,
            () -> azureProbeMonitorConfiguration.validate(azureClient));
    }

    @Test
    void test_validate_invalid_route_table() throws ConfigurationException {
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);

        AzureClient azureClient = mock(AzureClient.class);
        when(azureClient.checkExistenceById(contains("network-interface")))
            .thenReturn(true);
        when(azureClient.checkExistenceById(contains("public-ip-address")))
            .thenReturn(true);
        NetworkInterface networkInterface = mock(NetworkInterface.class);
        when(networkInterface.primaryPrivateIp())
            .thenReturn("127.0.0.1")
            .thenReturn("127.0.0.2");
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(networkInterface);

        Assertions.assertThrows(ConfigurationException.class,
            () -> azureProbeMonitorConfiguration.validate(azureClient));
    }

    @Test
    void test_validate() throws ConfigurationException {
        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
            new AzureProbeMonitorConfiguration(azureConfiguration,
                nvaConfigurations, routeTables, publicIpAddresses,
                null, null, null);

        AzureClient azureClient = mock(AzureClient.class);
        when(azureClient.checkExistenceById(anyString()))
            .thenReturn(true);
        NetworkInterface networkInterface = mock(NetworkInterface.class);
        when(networkInterface.primaryPrivateIp())
            .thenReturn("127.0.0.1")
            .thenReturn("127.0.0.2");
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(networkInterface);

        azureProbeMonitorConfiguration.validate(azureClient);
    }
}
