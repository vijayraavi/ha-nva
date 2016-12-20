# High Availability NVA on Microsoft Azure

The Patterns and Practices team at Microsoft has created a solution to deploy network virtual appliances (NVA) in a high availability configuration, without port restrictions. This document describes how to configure a test environment for the solution, and then how to deploy the high availability solution to the test environment. If you have your own environment with existing NVAs, skip the deployment for the test environment and start at the Docker VMs deployment.

## Deploy the test environment

In order to make it easier for customers to test the high availability solution, you can deploy a test environment by running a simple PowerShell script provided in GitHub. The test environment contains al resources shown belo, with the exception of the Zookeeper nodes.

![](https.//docs.microsoft.com/en-us/azure/guidance/media/guidance-nva-ha/active-passive.png)

The test environmentincludes.
* a virtual network with four subnets.
    * an external DMZ subnet (10.0.0.64/27)
    * an internal DMZ subnet (10.0.0.96/27)
    * a web subnet for the web workload VMs (10.0.1.0/24)
    * a management subnet for the jumpbox (10.0.0.128/25)
* Three virtual machines (VMs) deployed with network interfaces (NICs) with an IP address in the web subnet.
    * Each VM is deployed with an extension to install Apache, write a test HTML file (demo.html) to /var/www/html, and start the Apache process.
* An internal load balancer with an IP address in the web subnet. The internal load balancer distributes requests to the three web VMs.
* Two virtual machines each deployed with an extension to configure the VM as an uncomplicated firewall.
    * each NVA includes one NIC with an IP address in the external DMZ subnet and one NIC with an IP address in the internal DMZ subnet.
    * The active NVA is configured to forward traffic received on ports 80 and 443 to the IP address of the internal load balancer.
* A user defined route (UDR) with an entry in the route table to forward outbound requests from the three web tier VMs to the active NVA NIC in the internal DMZ subnet.
* a public IP (PIP) associated with one of the NVA NICs with an IP address in the external DMZ subnet. Initially the PIP is associated with the first NVA NIC, and this is the active NVA. The second NVA VM is the passive NVA.

To deploy the test architecture, follow these steps.

1. Either use git to clone the [https.//github.com/mspnp/ha-nva](https.//github.com/mspnp/ha-nva) repository on your local machine, or download a zip of the files and extract them to a local directory.
2. In Windows Powershell, navigate to the `ha-nva/ha-nva-deployment/sample-test-environment` directory and execute the following command.
    ```powershell
    ./Deploy.ps1 <subscriptionID> <region>
    ```
    Substitute your Azure subscription ID for `<subscriptionID>` and a valid region for `<region>`.
3. Wait for the deployment to complete.

The folder includes a pair of Powershell scripts that you can use to manually test switching the association of the PIP and changing the next hop IP address of the UDR to the passive NVA. To test switching to the second NVA, follow these steps.

1. In the Azure portal, go to the `ha-nva-rg` resource group, click on `ha-nva-vm1`, then `network interfaces` (under `settings`) and make note of the private IP address for each NIC, `ha-nva-vm1-nic1` and `ha-nva-vm1-nic2`. Repeat the process for `ha-nva-vm2`. Next, click on the `ha-nva-pip` public IP address, click on `overview`, and make note of the NIC name in the `associated to` field. Finally, click on the `ha-nva-udr` UDR, click on `routes` (under `settings`) and make note of the `next hop` IP address.  
2. In Windows Powershell, navigate to the `ha-nva/ha-nva-deployment/sample-test-environment` directory and execute the following command.
    ```powershell
    ./MoveToNode2.ps1
3. Return to the Azure portal and verify that the PIP has been associated with `ha-nva-vm2-nic1` and the `next hop` IP address of the UDR has been changed to the IP address of `ha-nva-vm2-nic2`.

To test switching to the first NVA, repeat step 1 above, and follow these steps.
1. In Windows Powershell, navigate to the `ha-nva/ha-nva-deployment/sample-test-environment` directory and execute the following command.
    ```powershell
    ./MoveToNode1.ps1
2. Return to the Azure portal and verify that the PIP has been associated with `ha-nva-vm1-nic1` and the `next hop` IP address of the UDR has been changed to the IP address of `ha-nva-vm1-nic2`.

## Deploying the HA NVA solution

This is a complex deployment and there are multiple steps that must be performed on multiple VMs. Before you begin, we strongly recommend that you read through these instructions completely to understand what is involved with each step of the deployment. 

Generally, the deployment has the following stages.

1. Deploy the docker VMs
2. Create an Azure service principal
4. Create the Docker image for the monitor client and configure monitor
5. Create and start the ZooKeeper server docker containers
6. Create and start the NVA monitor client containers

### Deploy Docker VMs

The deployment requires three or more VMs running Canonical Ubuntu Server 14.04 with the Azure CLI, Docker, the latest Java SDK, and jq (a JSON parser) installed. If you installed the test architecture above, a deployment is available that you can use to deploy the VMs with an extension to install all of the required resources. To keep things clear, we will refer to these VMs as Docker VM1, Docker VM2, and Docker VM3 from now on. You can deploy these VMs manuallly, or you can use the sample deployment provided in GitHub. To use the sample deployment, execute the steps below.

1. Either use git to clone the [https.//github.com/mspnp/ha-nva](https.//github.com/mspnp/ha-nva) repository on your local machine, or download a zip of the files and extract them to a local directory. If you deployed the test environment, you can skip this stp.
2. In Windows Powershell, navigate to the `ha-nva/ha-nva` directory and execute the following command. 
    ```powershell
    ./Deploy.ps1 <subscriptionID> <region> <resourceGroupName>
    ```
    Substitute your Azure subscription ID for `<subscriptionID>`, a valid region for `<region>`, and a new or existing resource group for `<resourceGroupName>`.
3. Wait for the deployment to complete.

### Create an Azure service principal

To create the Azure service principal, first use SSH to log in to Docker VM1. If you used the script above to deploy the Docker VMs, they will not have a public IP address, so you will have to connect to another VM (a jumpbox) and remote into the Docker VM from there. From the one of the Docker VMs, execute the steps below.

1. Copy the bash script and a JSON file that includes the properties defining the custom role with the following commands.
    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/createspandcert.sh
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/customAzureRole.json
    ```
2. Edit the `customAzureRole.json` to specify the scope for the service principal to be created. In the example below, we used `vi` to edit the file.
   ```
   vi customAzureRole.json
   ```
3. If you editor requires to enter in INSERT mode, do so. If you are using `vi`, type `i` to do so.
4. Navigate to teh end of the file, where you see the `AssignableScopes` array.
5. Edit the array to contain the list of subscriptions you want the service princiapal to have access to. The example below shows two subscriptions.
   ```
   "AssignableScopes": [
      "/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
   ]
   ```
6. Save the file and quit the editor. For instance, in `vi`, press `ESC` to get out of `INSERT` mode, then type `:wq` to save and quit, and press `ENTER`.
7. Login to Azure using the Azure CLI.
    ```
    azure login 
    ```
    You will be directed to http://aka.ms/devicelogin and enter the code. The command will output a success response when you are successfully logged in.
8. Locate your Subscription ID. You can either use the Azure Portal to find your subscription ID or you can execute the following command.
    ```
    azure account list
    ```
9. Now that you have your subscription ID, execute the bash script as follows.
    ```
    sudo bash createspandcert.sh -s subscriptionID -a hanvaapp -c hanvaapp
    ```
    You will be prompted several times for passwords. Create the passwords when prompted, and verify when prompted. Once the bash script has completed, the script will output the application ID and the Tenant ID as follows.
    ```
    =====================================================
    Application Id : <application ID>
    Tenant Id      : <tenant ID>
    =====================================================
    ```
    Save this information as you will need to enter this into a configuration file that is used to create the docker image for the NVA monitor client.
    The script also outputs a command to test whether or not the service principal was successfully created. Execute this command to verify that the service principal was created.

Now that the service principal has been created, create the docker image for the NVA monitor client.

### Create the Docker image for the monitor client and configure monitor

To create the docker image for the NVA monitor client, follow these steps.

1. Create an images directory using the following command.
    ```
    mkdir images
    ```
2. Navigate to the images directory.
    ```
    cd images
    ```
3. Copy the required files to the directory.
    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/docker/nva/images/nva-docker-entrypoint.sh
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/docker/nva/images/nvaimagealpine
    ```
4. Navigate up one directory level, and execute the following command to build the NVA monitor client image.
    ```
    cd ..
    sudo docker build -t nvaimagealpine:1.2 --build-arg=SRC="images" --build-arg=DST="/nvabin" -f images/nvaimagealpine .
    ```
    Note that `.` at the end of the command is required.
5. Get the NVA monitor client configuration file using the following command.
    ```
    cd images
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/nvadaemon-remote.json
    ```
6. Open the configureation file for editing. In the example below we used `vi` for that.
    ```
    vi nvadaemon-remote.json
    ```
7. If you editor requires to enter in INSERT mode, do so. If you are using `vi`, type `i` to do so.
8. Under `zookeeper`, change the `connectionString` to point to each instance of Zookeeper you want to use. In the example below, we are using three instances of Zookeeper on a VM named `docker`', and two instances on VMs `docker2` and `docker3`. Notice that each instance hosted on the same VM uses a different port number.
    ```
    "connectionString": "docker1:2181,docker1:2182,docker1:2183,docker2:2181,docker2:2182,docker3:2181,docker3:2182",
    ```
9. Under `daemon`, `monitors`, `settings`, `azure`, change `subscriptionId` to your subscription ID. 
    ```
    "subscriptionId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    ```
10. Under `servicePrincipal`, type the application ID you copied in the last step of "Create an Azure service principal" above in the `clientId` entry.
    ```
    "clientId": "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy",
    ```
11. For `tenantId`, type the tenant ID you copied in the last step of "Create an Azure service principal" above.
    ```
    "tenantId": "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
    ```
12. For `keyStorePath`, type `/nvabin/nva.jks`. This entry points to the path of the key file in the docker container running the monitor client.
13. For `keyStorePassword` and `certificatePassword`, type the password you created in step four for "Create an Azure service principal" above.
14. Under `routeTables`, type a list of resource IDs for all UDRs that contain routes pointing to your NVAs.
    ```
    "routeTables": [
        "/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/routeTables/ha-nva-udr",
        "/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/routeTables/ha-nva-gateway-udr"
    ],
    ```
15. Under `publicIpAdresses`, create an entry for each PIP used by your NVAs. The `name` property must match a the `name` property used in the `nvas` elemnt that follows `publiIpAddresses`. That way the monitoring client is able to determine which NIC in each NVA should be associated tot eh PIP during failover. The `id` uses the resource id for the PIP.
    ```
    "publicIpAddresses": [
        {
            "name": "nic1",
            "id": "/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/publicIPAddresses/ha-nva-pip    
        }
    ],
    ```
16. Under `nvas` create an entry with the list of NICs for each NVA you have. The number of NICs in each NVA must match. And NICs are associated based on the `name` property. The `id` property uses the resource id for the NIC. For each NVA, specify the NIC (`probeNetworkInterface`) and port (`probePort`) to be used for monitoring. The monitoring client will attempt to connect to this port, and if it fails the same number of times as determined in `numberOfFailuresThresholds` it will failover to the next NVA.
    ```
    "nvas": [
        {
            "networkInterfaces": [
                {
                    "name": "nic1",
                    "id": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm1-nic1"
                },
                {
                    "name": "nic2",
                    "id": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm1-nic2"
                }
            ],
            "probeNetworkInterface": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm1-nic2",
            "probePort": 8888
        },
        {
            "networkInterfaces": [
                {
                    "name": "nic1",
                    "id": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm2-nic1"
                },
                {
                    "name": "nic2",
                    "id": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm2-nic2"
                }
            ],
            "probeNetworkInterface": ""/subscriptions/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/resourceGroups/ha-nva-rg/providers/Microsoft.Network/networkInterfaces/ha-nva-vm2-nic2",
            "probePort": 8888
        }
    ]
    ```
17. Save the file. For instance, in `vi`, press `ESC` to get out of `INSERT` mode, then type `:wq` to save and quit, and press `ENTER`.

### Create and start the ZooKeeper server docker containers

To create and start the ZooKeeper server docker container, follow these steps.

1. Execute the following command.
    ```
    docker run --name zookeeper1 --restart always --network host -e ZOO_PORT=2181 -e ZOO_MY_ID=1 -e "ZOO_SERVERS=server.1=<logical name of host or IP address>:2889:3889 server.2=<logical name of host or IP address>:2900:3900 server.3=<logical name of host or IP address>:2901:3901 -d zookeeper:3.4.9
    ```
    Substitute the logical name or the IP address of the VM running docker where indicated. And make sure the `ZOO_MY_ID` value for each container mateches the number after `server.` in the list of `ZOO_SERVERS`. Also, ensure each Zookeeper container residing int he same VM uses a different range of client ports. The commands below can be used to start three containers in teh `Docker VM 1` virtual machine.
    ```
    docker run --name zookeeper1 --restart always --network host -e ZOO_PORT=2181 -e ZOO_MY_ID=1 -e "ZOO_SERVERS=server.1=docker1:2889:3889 server.2=docker1:2900:3900 server.3=docker1:2901:3901 server.4=docker2:2902:3902 server.5=docker2:2903:3903 server.6=docker3:2904:3904 server.7=docker3:2905:3905" -d zookeeper:3.4.9   
    docker run --name zookeeper2 --restart always --network host -e ZOO_PORT=2182 -e ZOO_MY_ID=1 -e "ZOO_SERVERS=server.1=docker1:2889:3889 server.2=docker1:2900:3900 server.3=docker1:2901:3901 server.4=docker2:2902:3902 server.5=docker2:2903:3903 server.6=docker3:2904:3904 server.7=docker3:2905:3905" -d zookeeper:3.4.9   
    docker run --name zookeeper3 --restart always --network host -e ZOO_PORT=2183 -e ZOO_MY_ID=1 -e "ZOO_SERVERS=server.1=docker1:2889:3889 server.2=docker1:2900:3900 server.3=docker1:2901:3901 server.4=docker2:2902:3902 server.5=docker2:2903:3903 server.6=docker3:2904:3904 server.7=docker3:2905:3905" -d zookeeper:3.4.9   
    ```
2. Verify the docker container is running by executing the following command.
    ```
    docker ps
    ```

### Start the NVA monitor client docker containers

1. Run the command below to create and start docker images running the monitoring client.
    ```
    docker run --name monitorClient1 --restart always --network host -v /<directory containing file>/nvadaemon-remote.json:/nvabin/nvadaemon-remote.json -v /<directory containing file>/nva.jks:/nvabin/nva.jks -d nvaimagealpine:1.2
    ```
    The commands below start two Docker images runnign the monitoring clien ton the `Docker VM 1` virtual machine.
    ```
    sudo docker run --name monitorClient1 --restart always --network host -v /nva/nvadaemon-sample.json:/nvabin/nvadaemon-remote.json -v /nva/nva.jks:/nvabin/nva.jks -d nvaimagealpine:1.2
    sudo docker run --name monitorClient2 --restart always --network host -v /nva/nvadaemon-sample.json:/nvabin/nvadaemon-remote.json -v /nva/nva.jks:/nvabin/nva.jks -d nvaimagealpine:1.2
2. Verify that the containers are running
    ```
    docker ps 
    ```
    The output below is a sample for `Docker VM 1`.
    CONTAINER ID        IMAGE               COMMAND                  CREATED             STATUS              PORTS               NAMES
    02da5ca12bb5        7810ee7c0cdf        "/nva-docker-entrypoi"   24 hours ago        Up 11 seconds                           monitorClient1
    2a2dc3db4cb2        7810ee7c0cdf        "/nva-docker-entrypoi"   24 hours ago        Up 14 seconds                           monitorClient2
    1ef97a639d6e        zookeeper:3.4.9     "/docker-entrypoint.s"   24 hours ago        Up 6 hours                              zookeeper1
    adcd9042d5fb        zookeeper:3.4.9     "/docker-entrypoint.s"   24 hours ago        Up 6 hours                              zookeeper2
    79a8ec671c0f        zookeeper:3.4.9     "/docker-entrypoint.s"   24 hours ago        Up 6 hours                              zookeeper3
    
Note. To create containers int eh other Docker VMs, switch to the VMs, create an `images` folder in the VMs and copy the contents of the existing folder in `Docker VM 1`, then follow the steps in "Create and start the ZooKeeper server docker containers" and "Start the NVA monitor client docker containers" for each VM.

## Verify the monitoring client

To make sure the monitoring client is working, follow the steps below.

1. From the Docker VM where the monitoring client is running, execute the following command.
    ```
    sudo docker logs <contianer_name> -f
    ```
    The command below shows the log for a continer named `clientMonitor1`.
    ```
    sudo docker logs clientMonitor1 -f
    ```
    If `clientMonitor1` is the leader, and working correctly, the output should contain entries similar tot he ones below.
    ```
    2016-12-15 22:21:10,266 DEBUG [pool-5-thread-1:NvaMonitor$MonitorCallable@95] - Waiting on signal
    2016-12-15 22:21:10,266 DEBUG [pool-5-thread-1:NvaMonitor$ScheduledMonitorCallable@56] - ScheduledMonitorCallable.await()
    2016-12-15 22:21:10,892 DEBUG [main-SendThread(10.0.0.102:2181):ClientCnxn$SendThread@742] - Got ping response for sessionid: 0x159033b46170000 after 0ms
    2016-12-15 22:21:13,267 DEBUG [pool-5-thread-1:NvaMonitor$ScheduledMonitorCallable@58] - Probe waiting time elapsed.  Looping
    2016-12-15 22:21:13,268 DEBUG [pool-5-thread-1:NvaMonitor$ScheduledMonitorCallable@66] - ScheduledMonitorCallable.await() complete
    ```
2. Repeat step 1 for other containers. If the container is not the leader, a healthy output would be similar to the one below.
    ```
    2016-12-15 22:20:33,996 DEBUG [main-SendThread(10.0.0.102:2183):ClientCnxn$SendThread@742] - Got ping response for sessionid: 0x359033b4a610000 after 0ms 
    2016-12-15 22:20:47,339 DEBUG [main-SendThread(10.0.0.102:2183):ClientCnxn$SendThread@742] - Got ping response for sessionid: 0x359033b4a610000 after 0ms
    2016-12-15 22:21:00,687 DEBUG [main-SendThread(10.0.0.102:2183):ClientCnxn$SendThread@742] - Got ping response for sessionid: 0x359033b4a610000 after 1ms
    2016-12-15 22:21:14,034 DEBUG [main-SendThread(10.0.0.102:2183):ClientCnxn$SendThread@742] - Got ping response for sessionid: 0x359033b4a610000 after 0ms    
    ```