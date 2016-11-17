param(
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  $Subject,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  [System.Uri]$HomePageUri,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  [System.Uri]$IdentifierUri,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  $DisplayName,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  $SubscriptionId,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$false)]
  $RoleDefinitionFile = "customAzureRole.json",
  [Parameter(Mandatory=$true)]
  $CertificatePassword,
  [Parameter(Mandatory=$true)]
  $PathToJdk,
  [Parameter(Mandatory=$true)]
  $OutputKeystoreFile,
  [Parameter(Mandatory=$true)]
  $KeystorePassword,
  [Parameter(ParameterSetName="GenerateKeyStore", Mandatory=$true)]
  $CertificateFile
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

switch ($PSCmdlet.ParameterSetName)
{
    "CreateServicePrincipal" { $certificateFilename = [System.IO.Path]::Combine($PSScriptRoot, "$DisplayName.pfx") }
    "GenerateKeyStore" {
        $certificateFilename = $CertificateFile
        if ($certificateFilename -eq ([System.IO.Path]::GetFileName($certificateFilename)))
        {
            $certificateFilename = [System.IO.Path]::Combine($PSScriptRoot, $certificateFilename)
        }
    }
}

if ($PSCmdlet.ParameterSetName -eq "CreateServicePrincipal")
{
    if ([System.IO.File]::Exists($certificateFilename))
    {
      throw "Output certificate file already exists: $certificateFilename"
    }

    if ($RoleDefinitionFile -eq ([System.IO.Path]::GetFileName($RoleDefinitionFile)))
    {
        $RoleDefinitionFile = [System.IO.Path]::Combine($PSScriptRoot, $RoleDefinitionFile)
    }

    if (-not [System.IO.File]::Exists($RoleDefinitionFile))
    {
      throw "Role definition file does not exist: $RoleDefinitionFile"
    }

    $password = ConvertTo-SecureString -String $CertificatePassword -AsPlainText -Force
    $certificate = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" -Subject "CN=$Subject" -KeySpec KeyExchange
    Export-PfxCertificate -Cert $certificate -FilePath $certificateFilename -Password $password | Out-Null

    $keyValue = [System.Convert]::ToBase64String($certificate.GetRawCertData())
    Login-AzureRmAccount -SubscriptionId $SubscriptionId
    $application = New-AzureRmADApplication -DisplayName $DisplayName -HomePage $HomePageUri.AbsoluteUri `
        -IdentifierUris $identifierUri.AbsoluteUri -CertValue $keyValue -EndDate $cert.NotAfter -StartDate $cert.NotBefore
    $servicePrincipal = New-AzureRmADServicePrincipal -ApplicationId $application.ApplicationId
    $roleDefinition = New-AzureRmRoleDefinition -InputFile $RoleDefinitionFile
    $roleAssignment = New-AzureRmRoleAssignment -RoleDefinitionName $roleDefinition.Name `
        -ServicePrincipalName $application.ApplicationId.Guid
}
elseif ($PSCmdlet.ParameterSetName -eq "GenerateKeyStore")
{
    # We are just building the keystore file, so we need to make sure the certificate file exists
    if (-not [System.IO.File]::Exists($certificateFilename))
    {
      throw "Input certificate file does not exist: $certificateFilename"
    }
}

# Build the Java keystore file
$keytoolArguments = "-importkeystore -srckeystore `"$certificateFilename`" -srcstoretype pkcs12 -srcstorepass $CertificatePassword -destkeystore `"$OutputKeystoreFile`" -deststoretype JKS -deststorepass $KeystorePassword"
$output = ""

# We need to do all of this work because of the way PowerShell interacts with keytool.
Invoke-Expression "& `"$keytoolPath`" $keytoolArguments 2>`&1" | % { 
  if ($_ -is [System.Management.Automation.ErrorRecord]) {
    $record = $_ -as [System.Management.Automation.ErrorRecord]
    if ($record.FullyQualifiedErrorId -eq "NativeCommandError")
    {
      $output += $record.Exception.Message + [System.Environment]::NewLine + [System.Environment]::NewLine
    }
    else
    {
      $output += $record.Exception.Message
    }
  }
}

if ($LASTEXITCODE -eq 0)
{
  Write-Host $output
}
else
{
  # We aren't going to use throw here, but simply write out the error and exit.
  Write-Error "Error invoking keytool"
  Write-Error $output
  Exit(1)
}