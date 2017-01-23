package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.aad.adal4j.AuthenticationResult;

public class AzureConfiguration {
    private String subscriptionId;
    private ServicePrincipal servicePrincipal;

    @JsonCreator
    public AzureConfiguration(@JsonProperty("subscriptionId")String subscriptionId,
                              @JsonProperty("servicePrincipal")ServicePrincipal servicePrincipal) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(subscriptionId),
            "subscriptionId cannot be null or empty");
        this.servicePrincipal = Preconditions.checkNotNull(servicePrincipal,
            "servicePrincipal cannot be null");
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() { return this.subscriptionId; }

    public ServicePrincipal getServicePrincipal() { return this.servicePrincipal; }

    public static class ServicePrincipal {
        private String tenantId;
        private String clientId;
        private String clientSecret;
        private ClientCertificate clientCertificate;

        @JsonIgnore
        private AuthenticationMode authenticationMode;

        @JsonCreator
        public ServicePrincipal(@JsonProperty("tenantId")String tenantId,
                                @JsonProperty("clientId")String clientId,
                                @JsonProperty("clientSecret")String clientSecret,
                                @JsonProperty("clientCertificate")ClientCertificate clientCertificate) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(tenantId),
                "tenantId cannot be null or empty");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(clientId),
                "clientId cannot be null or empty");

            if ((Strings.isNullOrEmpty(clientSecret) &&
                (clientCertificate == null))) {
                throw new IllegalArgumentException("No authentication mode specified");
            } else if ((!Strings.isNullOrEmpty(clientSecret) &&
                (clientCertificate != null))) {
                throw new IllegalArgumentException("Ambiguous authentication mode specified");
            } else if (!Strings.isNullOrEmpty(clientSecret)) {
                this.clientSecret = clientSecret;
                this.authenticationMode = AuthenticationMode.PASSWORD;
            } else {
                this.clientCertificate = clientCertificate;
                this.authenticationMode = AuthenticationMode.CERTIFICATE;
            }

            this.clientId = clientId;
            this.tenantId = tenantId;

        }

        public String getClientId() { return this.clientId; }

        public String getTenantId() { return this.tenantId; }

        public String getClientSecret() { return this.clientSecret; }

        public ClientCertificate getClientCertificate() { return this.clientCertificate; }

        public AuthenticationMode getAuthenticationMode() { return this.authenticationMode; }

        public static class ClientCertificate {
            private String keyStorePath;
            private String keyStorePassword;
            private String certificatePassword;

            @JsonCreator
            public ClientCertificate(@JsonProperty("keyStorePath")String keyStorePath,
                                     @JsonProperty("keyStorePassword")String keyStorePassword,
                                     @JsonProperty("certificatePassword")String certificatePassword) {
                Preconditions.checkArgument(!Strings.isNullOrEmpty(keyStorePath),
                    "keyStorePath cannot be null or empty");
                Preconditions.checkArgument(!Strings.isNullOrEmpty(keyStorePassword),
                    "keyStorePassword cannot be null or empty");
                Preconditions.checkArgument(!Strings.isNullOrEmpty(certificatePassword),
                    "certificatePassword cannot be null or empty");
                this.keyStorePath = keyStorePath;
                this.keyStorePassword = keyStorePassword;
                this.certificatePassword = certificatePassword;
            }

            public String getKeyStorePath() { return this.keyStorePath; }

            public String getKeyStorePassword() { return this.keyStorePassword; }

            public String getCertificatePassword() { return this.certificatePassword; }
        }

        public enum AuthenticationMode {
            PASSWORD,
            CERTIFICATE
        }
    }
}
