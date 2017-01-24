package com.microsoft.azure.practices.nvadaemon.config;

import static org.mockito.Mockito.*;

import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.practices.nvadaemon.AzureClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NvaConfigurationTest {
    @Test
    void test_null_probe_network_interface() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration(null, null, null));
    }

    @Test
    void test_empty_probe_network_interface() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("", null, null));
    }

    @Test
    void test_null_probe_port() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaConfiguration("probe-network-interface", null, null));
    }

    @Test
    void test_zero_probe_port() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("probe-network-interface", 0, null));
    }

    @Test
    void test_negative_probe_port() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("probe-network-interface", -1, null));
    }

    @Test
    void test_null_network_interfaces() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaConfiguration("probe-network-interface", 1234, null));
    }

    @Test
    void test_empty_network_interfaces() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("probe-network-interface", 1234, new ArrayList<>()));
    }

    @Test
    void test_duplicate_network_interface_ids() {
        List<NamedResourceId> networkInterfaces = new ArrayList<>();
        networkInterfaces.add(new NamedResourceId("nic1", "nic1-id"));
        networkInterfaces.add(new NamedResourceId("nic2", "nic1-id"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("probe-network-interface", 1234, networkInterfaces));
    }

    @Test
    void test_duplicate_network_interface_names() {
        List<NamedResourceId> networkInterfaces = new ArrayList<>();
        networkInterfaces.add(new NamedResourceId("nic1", "nic1-id"));
        networkInterfaces.add(new NamedResourceId("nic1", "nic2-id"));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NvaConfiguration("probe-network-interface", 1234, networkInterfaces));
    }

    @Test
    void test_validate_null_azure_client() {
        NvaConfiguration nvaConfiguration = new NvaConfiguration("probe-network-interface",
            1234, Arrays.asList(new NamedResourceId("nic1", "nic1-id")));
        Assertions.assertThrows(NullPointerException.class,
            () -> nvaConfiguration.validate(null));
    }
    @Test
    void test_validate_invalid_network_interfaces() {
        AzureClient azureClient = mock(AzureClient.class);
        List<NamedResourceId> networkInterfaces = new ArrayList<>();
        networkInterfaces.add(new NamedResourceId("nic1", "nic1-id"));
        NvaConfiguration nvaConfiguration = new NvaConfiguration("probe-network-interface",
            1234, networkInterfaces);
        when(azureClient.checkExistenceById(anyString()))
            .thenReturn(false);
        Assertions.assertThrows(ConfigurationException.class,
            () -> nvaConfiguration.validate(azureClient));
    }

    @Test
    void test_validate_invalid_probe_network_interfaces() {
        AzureClient azureClient = mock(AzureClient.class);
        List<NamedResourceId> networkInterfaces = new ArrayList<>();
        networkInterfaces.add(new NamedResourceId("nic1", "nic1-id"));
        NvaConfiguration nvaConfiguration = new NvaConfiguration("probe-network-interface",
            1234, networkInterfaces);
        when(azureClient.checkExistenceById(anyString()))
            .thenReturn(true);
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(null);
        Assertions.assertThrows(ConfigurationException.class,
            () -> nvaConfiguration.validate(azureClient));
    }

    @Test
    void test_validate_valid_probe_network_interfaces() throws ConfigurationException {
        Integer probePort = 1234;
        String probeNetworkInterfacePrimaryPrivateIp = "127.0.0.1";
        List<NamedResourceId> networkInterfaces = new ArrayList<>();
        networkInterfaces.add(new NamedResourceId("nic1", "nic1-id"));
        NvaConfiguration nvaConfiguration = new NvaConfiguration("probe-network-interface",
            probePort, networkInterfaces);

        AzureClient azureClient = mock(AzureClient.class);
        when(azureClient.checkExistenceById(anyString()))
            .thenReturn(true);
        NetworkInterface probeNetworkInterface = mock(NetworkInterface.class);
        when(probeNetworkInterface.primaryPrivateIp())
            .thenReturn(probeNetworkInterfacePrimaryPrivateIp);
        when(azureClient.getNetworkInterfaceById(anyString()))
            .thenReturn(probeNetworkInterface);

        nvaConfiguration.validate(azureClient);

        Assertions.assertEquals(networkInterfaces, nvaConfiguration.getNetworkInterfaces());
        InetSocketAddress probeSocketAddress =
            (InetSocketAddress)nvaConfiguration.getProbeSocketAddress();
        Assertions.assertNotNull(probeSocketAddress);
        Assertions.assertEquals(probePort.intValue(), probeSocketAddress.getPort());
        Assertions.assertEquals(probeNetworkInterfacePrimaryPrivateIp,
            probeSocketAddress.getAddress().getHostAddress());
    }
}
