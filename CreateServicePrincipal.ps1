param(
  [Parameter(Mandatory=$true)]
  $Subject,
  [Parameter(Mandatory=$true)]
  $CertificatePassword,
  [Parameter(Mandatory=$true)]
  [System.Uri]$HomePageUri,
  [Parameter(Mandatory=$true)]
  [System.Uri]$IdentifierUri,
  [Parameter(Mandatory=$true)]
  $DisplayName,
  [Parameter(Mandatory=$true)]
  $SubscriptionId,
  [Parameter(Mandatory=$false)]
  $RoleDefinitionFile,
  [Parameter(Mandatory=$true)]
  $PathToJdk,
  [Parameter(Mandatory=$true)]
  $OutputKeystoreFile,
  [Parameter(Mandatory=$true)]
  $KeystorePassword
)

$keytoolPath = [System.IO.Path]::Combine($PathToJdk, "bin\keytool.exe")
if (-not [System.IO.File]::Exists($keytoolPath))
{
  throw "Could not find Java keytool"
}

# We need to see if we have a full path or just a filename
if ($OutputKeystoreFile -eq ([System.IO.Path]::GetFileName($OutputKeystoreFile)))
{
  # This is just a filename, so append the script directory
  $OutputKeystoreFile = [System.IO.Path]::Combine($PSScriptRoot, $OutputKeystoreFile)
}

if ([System.IO.File]::Exists($OutputKeystoreFile))
{
  throw "Output keystore file already exists:  $OutputKeystoreFile"
}

$certificateFilename = [System.IO.Path]::Combine($PSScriptRoot, "$DisplayName.pfx")
if ([System.IO.File]::Exists($certificateFilename))
{
  throw "Output certificate file already exists: $certificateFilename"
}

if ([System.String]::IsNullOrWhiteSpace($RoleDefinitionFile))
{
  $RoleDefinitionFile = [System.IO.Path]::Combine($PSScriptRoot, "customAzureRole.json")
}

$password = ConvertTo-SecureString -String $CertificatePassword -AsPlainText -Force
$certificate = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=$Subject" -KeySpec KeyExchange
Export-PfxCertificate -Cert $certificate -FilePath $certificateFilename -Password $password

$keytoolCommand = "-importkeystore -srckeystore `"$certificateFilename`" -srcstoretype pkcs12 -srcstorepass $CertificatePassword -destkeystore `"$OutputKeystoreFile`" -deststoretype JKS -deststorepass $KeystorePassword"
#$keytoolCommand = "`"$keytoolPath`" -importkeystore"
$output = Invoke-Expression "& `"$keytoolPath`" $keytoolCommand 2>`$null"
if ($LASTEXITCODE -ne 0)
{
  throw "Error invoking keytool: $output"
}

$keyValue = [System.Convert]::ToBase64String($certificate.GetRawCertData())
Login-AzureRmAccount -SubscriptionId $SubscriptionId
$application = New-AzureRmADApplication -DisplayName $DisplayName -HomePage $HomePageUri.AbsoluteUri `
  -IdentifierUris $identifierUri.AbsoluteUri -CertValue $keyValue -EndDate $cert.NotAfter -StartDate $cert.NotBefore
$servicePrincipal = New-AzureRmADServicePrincipal -ApplicationId $application.ApplicationId
$roleDefinition = New-AzureRmRoleDefinition -InputFile $RoleDefinitionFile
$roleAssignment = New-AzureRmRoleAssignment -RoleDefinitionName $roleDefinition.Name `
  -ServicePrincipalName $application.ApplicationId.Guid

# Build the Java keystore file
