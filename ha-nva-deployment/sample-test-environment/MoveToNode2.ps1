$pip = Get-AzureRmPublicIpAddress -Name ha-nva-pip -ResourceGroupName ha-nva-rg

$nic1 = Get-AzureRmNetworkInterface -ResourceGroupName ha-nva-rg -Name ha-nva-vm1-nic1
$nic1.IpConfigurations[0].PublicIpAddress = $null
$nic1 | Set-AzureRmNetworkInterface

$nic2 = Get-AzureRmNetworkInterface -ResourceGroupName ha-nva-rg -Name ha-nva-vm2-nic1
$nic2.IpConfigurations[0].PublicIpAddress = $pip
$nic2 | Set-AzureRmNetworkInterface

$rt = Get-AzureRmRouteTable -ResourceGroupName ha-nva-rg -Name ha-nva-udr
$route = Get-AzureRmRouteConfig -RouteTable $rt -Name default
$route.NextHopIpAddress="10.0.0.69"
Set-AzureRmRouteConfig -Name $route.Name -RouteTable $rt -AddressPrefix $route.AddressPrefix -NextHopType $route.NextHopType -NextHopIpAddress $route.NextHopIpAddress
Set-AzureRmRouteTable -RouteTable $rt