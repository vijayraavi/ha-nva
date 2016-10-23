$cert = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=nva" -KeySpec KeyExchange

$cert = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=nva" -KeySpec KeyExchange

echo $cert
Add-AzureRmAccount



Select-AzureRmSubscription -SubscriptionName "Azure Integration Pack - Perf Testing Subscription"


$cert = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=nva" -KeySpec KeyExchange
$keyValue = [System.Convert]::ToBase64String($cert.GetRawCertData())


$app = New-AzureRmADApplication  -DisplayName "nvaapplication10" -HomePage "https://nva.com" -IdentifierUris "https://nva.org" -CertValue $keyValue -EndDate $cert.NotAfter -StartDate $cert.NotBefore      
echo $app


New-AzureRmADServicePrincipal -ApplicationId 10686b6e-b797-4f4f-9e5d-56b6aaa3b377


New-AzureRmRoleAssignment -RoleDefinitionName Contributor -ServicePrincipalName 10686b6e-b797-4f4f-9e5d-56b6aaa3b377



