package com.microsoft.azure.practices.nvadaemon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.practices.nvadaemon.config.AzureProbeMonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.AzureProbeMonitorConfigurationTest;
import com.microsoft.azure.practices.nvadaemon.config.ConfigurationException;
import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class AzureProbeMonitorTest {
    @Test
    void testConstructorNullMonitorConfiguration() throws ConfigurationException {
        Assertions.assertThrows(NullPointerException.class,
            () -> new AzureProbeMonitor(null));
    }

//    @Test
//    void testConstructor() throws ConfigurationException {
//        ObjectMapper mapper = new ObjectMapper()
//            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
//        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
//        AzureProbeMonitorConfiguration azureProbeMonitorConfiguration =
//            new AzureProbeMonitorConfiguration(AzureProbeMonitorConfigurationTest.azureConfiguration,
//                AzureProbeMonitorConfigurationTest.nvaConfigurations,
//                AzureProbeMonitorConfigurationTest.routeTables,
//                AzureProbeMonitorConfigurationTest.publicIpAddresses,
//                null, null, null);
//        Map<String, Object> settings = mapper.convertValue(azureProbeMonitorConfiguration,
//            new TypeReference<Map<String, Object>>(){});
//        MonitorConfiguration monitorConfiguration = new MonitorConfiguration(
//            "com.company.Monitor", settings);
//        AzureProbeMonitor monitor = new AzureProbeMonitor(monitorConfiguration);
//    }
}
