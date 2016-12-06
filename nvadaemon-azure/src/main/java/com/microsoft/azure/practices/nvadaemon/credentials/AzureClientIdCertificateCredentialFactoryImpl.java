package com.microsoft.azure.practices.nvadaemon.credentials;

import com.google.common.base.Preconditions;
import com.microsoft.aad.adal4j.AsymmetricKeyCredential;
import com.microsoft.azure.practices.nvadaemon.config.AzureProbeMonitorConfiguration;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class AzureClientIdCertificateCredentialFactoryImpl implements AsymmetricKeyCredentialFactory {

    private AzureProbeMonitorConfiguration configuration;

    public AzureClientIdCertificateCredentialFactoryImpl(AzureProbeMonitorConfiguration configuration) {
        this.configuration = Preconditions.checkNotNull(configuration,
            "configuration cannot be null or empty");
    }

    @Override
    public AsymmetricKeyCredential create(String resource) throws GeneralSecurityException {
        // We don't care about the resource in our scenario, so we will ignore it.
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream inputStream = new FileInputStream(
            this.configuration.getKeyStorePath())) {
            keyStore.load(inputStream, this.configuration.getKeyStorePassword().toCharArray());
            // Assuming there is only one entry here.
            String alias = keyStore.aliases().nextElement();
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias,
                new KeyStore.PasswordProtection(this.configuration.getCertificatePassword().toCharArray()));
            AsymmetricKeyCredential credential = AsymmetricKeyCredential.create(
                this.configuration.getClientId(), privateKeyEntry.getPrivateKey(),
                (X509Certificate) privateKeyEntry.getCertificate());
            return credential;
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not load KeyStore file", e);
        }
    }
}
