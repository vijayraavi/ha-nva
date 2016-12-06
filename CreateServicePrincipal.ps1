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
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$true)]
  $ResourceGroup,
  [Parameter(ParameterSetName="CreateServicePrincipal", Mandatory=$false)]
  $RoleDefinitionFile = "customAzureRole.json",
  [Parameter(ParameterSetName="GenerateKeyStore", Mandatory=$true)]
  [ValidateScript({
        if (Test-Path $_) {
            $true
        }
        else {
            Throw [System.Management.Automation.ValidationMetadataException] "The path '${_}' is not valid."
        }
    })]
  $Certificate,
  [Parameter(Mandatory=$true)]
  $CertificatePassword,
  [Parameter(Mandatory=$true)]
  [ValidateScript({
    if (Join-Path -Path $_ -ChildPath "bin\keytool.exe" | Test-Path) {
        $true
    }
    else {
        Throw [System.Management.Automation.ValidationMetadataException] "'${_}' does not contain keytool.exe"
    }
  })]
  $PathToJdk,
  [Parameter(Mandatory=$true)]
  [ValidateScript({
        if (Test-Path $_) {
            Throw [System.Management.Automation.ValidationMetadataException] "Output keystore file '${_}' already exists"
        }

        $true
    })]
  $OutputKeystoreFile,
  [Parameter(Mandatory=$true)]
  [ValidateNotNullOrEmpty()]
  $KeystorePassword
)

$ErrorActionPreference = "Stop"

#$keytoolPath = [System.IO.Path]::Combine($PathToJdk, "bin\keytool.exe")
#if (-not [System.IO.File]::Exists($keytoolPath))
#{
#  throw "Could not find Java keytool"
#}

$keytoolPath = Join-Path -Path $_ -ChildPath "bin\keytool.exe"

# We need to see if we have a full path or just a filename
if ($OutputKeystoreFile -eq ([System.IO.Path]::GetFilename($OutputKeystoreFile)))
{
    # this is just a filename, so append the script directory
    $OutputKeystoreFile = Join-Path -Path $PSScriptRoot -ChildPath $OutputKeystoreFile
  
#  $outputkeystorefile = [system.io.path]::combine($psscriptroot, $outputkeystorefile)
}

#if ([System.IO.File]::Exists($OutputKeystoreFile))
#{
#  throw "Output keystore file already exists:  $OutputKeystoreFile"
#}

switch ($PSCmdlet.ParameterSetName)
{
    "CreateServicePrincipal" { $certificateFilename = [System.IO.Path]::Combine($PSScriptRoot, "$DisplayName.pfx") }
    "GenerateKeyStore" {
    # We are just building the keystore file, so we need to make sure the certificate file exists
    $resolvedPath = Resolve-Path $Certificate
    switch ($resolvedPath.Provider.Name) {
        "Certificate" {
            $certificate = Get-Item $Certificate
            $certificateFilename = Join-Path -Path $PSScriptRoot -ChildPath ($cert.GetNameInfo([System.Security.Cryptography.X509Certificates.X509NameType]::SimpleName, $false) + ".pfx")
            # Export for later
            $password = ConvertTo-SecureString -String $CertificatePassword -AsPlainText -Force
            Export-PfxCertificate -Cert $certificate -FilePath $certificateFilename -Password $password | Out-Null
        }
        "FileSystem" {
            # If we need this, we can import here.
            #$certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2
            #$certificate.Import((Get-Item $resolvedPath).FullName, $CertificatePassword, [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::UserKeySet)
            $certificateFilename = (Get-Item $resolvedPath).FullName
        }
        default { Write-Error "Invalid provider: $($resolvedPath.Provider.Name)" }
    }
        #$certificateFilename = $CertificateFile
        #if ($certificateFilename -eq ([System.IO.Path]::GetFileName($certificateFilename)))
        #{
        #    $certificateFilename = [System.IO.Path]::Combine($PSScriptRoot, $certificateFilename)
        #}
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

    Login-AzureRmAccount -SubscriptionId $SubscriptionId

    # Create the role definition, adding the resource group as a scope
    $role = [Microsoft.Azure.Commands.Resources.Models.Authorization.PSRoleDefinition](Get-Content -Path $RoleDefinitionFile -Raw | ConvertFrom-Json)
    $role.Id = $null
    $role.AssignableScopes = @("/subscriptions/$SubscriptionId")

    $password = ConvertTo-SecureString -String $CertificatePassword -AsPlainText -Force
    $certificate = New-SelfSignedCertificate -CertStoreLocation "cert:\CurrentUser\My" `
        -Subject "CN=$Subject" -KeySpec KeyExchange -KeyExportPolicy Exportable
    Export-PfxCertificate -Cert $certificate -FilePath $certificateFilename -Password $password | Out-Null

    $keyValue = [System.Convert]::ToBase64String($certificate.GetRawCertData())
    
    # See if the role definition exists in our tenant before we try to create it.
    $roleDefinition = Get-AzureRmRoleDefinition -Name $role.Name
    if ($roleDefinition -eq $null) {
        #$roleDefinition = New-AzureRmRoleDefinition -Role $role
        New-AzureRmRoleDefinition -Role $role | Tee-Object -Variable roleDefinition
    }

    #$application = New-AzureRmADApplication -DisplayName $DisplayName -HomePage $HomePageUri.AbsoluteUri `
    New-AzureRmADApplication -DisplayName $DisplayName -HomePage $HomePageUri.AbsoluteUri `
        -IdentifierUris $identifierUri.AbsoluteUri -CertValue $keyValue -EndDate $certificate.NotAfter `
        -StartDate $certificate.NotBefore | Tee-Object -Variable application

    #$servicePrincipal = New-AzureRmADServicePrincipal -ApplicationId $application.ApplicationId
    New-AzureRmADServicePrincipal -ApplicationId $application.ApplicationId | Tee-Object -Variable servicePrincipal
    #$roleDefinition = New-AzureRmRoleDefinition -InputFile $RoleDefinitionFile
    #$roleAssignment = New-AzureRmRoleAssignment -RoleDefinitionName $roleDefinition.Name `
    New-AzureRmRoleAssignment -RoleDefinitionName $roleDefinition.Name -ServicePrincipalName $application.ApplicationId.Guid `
        -ResourceGroupName $ResourceGroup | Tee-Object -Variable roleAssignment
}

# Build the Java keystore file

$keytoolArguments = "-importkeystore -srckeystore `"$certificateFilename`" -srcstoretype pkcs12 -srcstorepass $CertificatePassword -destkeystore `"$OutputKeystoreFile`" -deststoretype JKS -deststorepass $KeystorePassword"
$output = ""

# We need to do all of this work because of the way PowerShell interacts with keytool.
$oldEAP = $ErrorActionPreference
try {

    $ErrorActionPreference = "Continue"
    Invoke-Expression -ErrorAction Ignore "& `"$keytoolPath`" $keytoolArguments 2>`&1" | % -ErrorAction Ignore { 
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
}
finally {
  $ErrorActionPreference = "Stop"
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