package com.microsoft.azure.practices.nvadaemon.config;

import com.microsoft.azure.practices.nvadaemon.AzureClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class NamedResourceIdTest {
    @Test
    void test_null_name() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NamedResourceId(null, null));
    }

    @Test
    void test_empty_name() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NamedResourceId("", null));
    }

    @Test
    void test_null_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NamedResourceId("name", null));
    }

    @Test
    void test_empty_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new NamedResourceId("name", ""));
    }

    @Test
    void test_name_and_id_and_validate() {
        String name = "name";
        String id = "id";
        NamedResourceId namedResourceId = new NamedResourceId(name, id);

        AzureClient azureClient = mock(AzureClient.class);

        namedResourceId.validate(azureClient);
        Assertions.assertEquals(name, namedResourceId.getName());
        Assertions.assertEquals(id, namedResourceId.getId());
    }
}
