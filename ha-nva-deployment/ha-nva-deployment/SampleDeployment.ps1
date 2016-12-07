.\Deploy.ps1 -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 -Location westus -ResourceGroupName ha-nva-rg -Mode Infrastructure
#Edit PIP portion of Deploy file, if necessary, to change the name of the NIC that the PIP will be linked to
.\Deploy.ps1 -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 -Location westus -ResourceGroupName ha-nva-rg -Mode PIP
#Edit ufw parameter file and make sure the right list of vm names are used, and change 1.1.1.1 to the actual PIP created above
.\Deploy.ps1 -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 -Location westus -ResourceGroupName ha-nva-rg -Mode UFW
#Edit UDR route to point to the private IP address of the internal interface for the primary NVA
.\Deploy.ps1 -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 -Location westus -ResourceGroupName ha-nva-rg -Mode UDR
#Edit the docker parameter file and make sure the right virtual network, resource group and subnet are being used
.\Deploy.ps1 -SubscriptionId 3b518fac-e5c8-4f59-8ed5-d70b626f8e10 -Location westus -ResourceGroupName ha-nva-rg -Mode Docker