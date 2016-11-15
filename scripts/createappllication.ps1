$pwd = ConvertTo-SecureString -String "p@ssw0rd" -AsPlainText -Force
$cert = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=fortinva1" -KeySpec KeyExchange
Export-PfxCertificate -Cert $cert -FilePath "e:\certificates\fortinva1.pfx" -Password $pwd
$cert = Get-PfxCertificate -FilePath "e:\certificates\fortinva1.pfx"


echo $cert
#Add-AzureRmAccount


$keyValue = [System.Convert]::ToBase64String($cert.GetRawCertData())
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2
$cert.Import("e:\certificates\fortinva1.pfx", "p@ssw0rd", [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::DefaultKeySet)
$keyValue = [System.Convert]::ToBase64String($cert.GetRawCertData())
$homePage = "https://pnp1.azure.com"
$identifierUri = $homePage + "/ha-nva"
Login-AzureRmAccount -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 

#Select-AzureRmSubscription -SubscriptionName "Azure Integration Pack - Perf Testing Subscription"


$app = New-AzureRmADApplication  -DisplayName "nvaapplication1000" -HomePage "https://nvad.com" -IdentifierUris "https://nvad.org" -CertValue $keyValue -EndDate $cert.NotAfter -StartDate $cert.NotBefore      
echo $app


New-AzureRmADServicePrincipal -ApplicationId $app.ApplicationId

#New-AzureRmRoleAssignment -RoleDefinitionName Reader -ServicePrincipalName 10686b6e-b797-4f4f-9e5d-56b6aaa3b377