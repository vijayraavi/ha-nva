package com.microsoft.azure.practices.nvadaemon.credentials;

import com.microsoft.aad.adal4j.AsymmetricKeyCredential;

import java.security.GeneralSecurityException;

public interface AsymmetricKeyCredentialFactory {
    AsymmetricKeyCredential create(String resource) throws GeneralSecurityException;
}
