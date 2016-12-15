package com.microsoft.azure.practices.nvadaemon.credentials;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.aad.adal4j.AsymmetricKeyCredential;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.AzureProbeMonitorConfiguration;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class AzureClientIdCertificateCredentialFactoryImpl implements AsymmetricKeyCredentialFactory {

    private String clientId;
    private String keyStorePath;
    private String keyStorePassword;
    private String certificatePassword;

    public AzureClientIdCertificateCredentialFactoryImpl(String clientId, String keyStorePath,
                                                         String keyStorePassword,
                                                         String certificatePassword) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(clientId),
            "clientId cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(keyStorePath),
            "keyStorePath cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(keyStorePassword),
            "keyStorePassword cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(certificatePassword),
            "certificatePassword cannot be null or empty");
        this.clientId = clientId;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.certificatePassword = certificatePassword;
    }

    @Override
    public AsymmetricKeyCredential create(String resource) throws GeneralSecurityException {
        // We don't care about the resource in our scenario, so we will ignore it.
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        try (FileInputStream inputStream = new FileInputStream(
            this.keyStorePath)) {
            keyStore.load(inputStream, this.keyStorePassword.toCharArray());
            // Assuming there is only one entry here.
            String alias = keyStore.aliases().nextElement();
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias,
                new KeyStore.PasswordProtection(this.certificatePassword.toCharArray()));
            AsymmetricKeyCredential credential = AsymmetricKeyCredential.create(
                this.clientId, privateKeyEntry.getPrivateKey(),
                (X509Certificate)privateKeyEntry.getCertificate());
            return credential;
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not load KeyStore file", e);
        }
    }
}
