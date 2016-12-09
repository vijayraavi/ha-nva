#
# Deploy_ReferenceArchitecture.ps1
#
param(
  [Parameter(Mandatory=$true)]
  $SubscriptionId,
  [Parameter(Mandatory=$false)]
  $Location = "West US 2",
  [Parameter(Mandatory=$false)]
  $ResourceGroupName = "ha-nva-rg"
)

$ErrorActionPreference = "Stop"

$buildingBlocksRootUriString = $env:TEMPLATE_ROOT_URI
if ($buildingBlocksRootUriString -eq $null) {
  $buildingBlocksRootUriString = "https://raw.githubusercontent.com/mspnp/template-building-blocks/master/"
}

if (![System.Uri]::IsWellFormedUriString($buildingBlocksRootUriString, [System.UriKind]::Absolute)) {
  throw "Invalid value for TEMPLATE_ROOT_URI: $env:TEMPLATE_ROOT_URI"
}

Write-Host
Write-Host "Using $buildingBlocksRootUriString to locate templates"
Write-Host

$templateRootUri = New-Object System.Uri -ArgumentList @($buildingBlocksRootUriString)
$multiVMsTemplate = New-Object System.Uri -ArgumentList @($templateRootUri, "templates/buildingBlocks/multi-vm-n-nic-m-storage/azuredeploy.json")

$dockerParametersFile = [System.IO.Path]::Combine($PSScriptRoot, "parameters\ha-nva-docker.parameters.json")

# Login to Azure and select your subscription
Login-AzureRmAccount -SubscriptionId $SubscriptionId | Out-Null

# Create the resource group
$networkResourceGroup = New-AzureRmResourceGroup -Name $ResourceGroupName -Location $Location

Write-Host "Deploying docker vms..."
New-AzureRmResourceGroupDeployment -Name "ra-docker11-deployment" -ResourceGroupName $networkResourceGroup.ResourceGroupName `
	-TemplateUri $multiVMsTemplate.AbsoluteUri -TemplateParameterFile $dockerParametersFile