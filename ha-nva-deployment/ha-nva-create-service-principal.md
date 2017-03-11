### Create the Azure service principal

The Azure AD service principal and application object are created using a bash script that you will copy from Github. Choose one of the three docker VMs and use SSH to log on to the VM.

1. Copy the bash script and a JSON file that includes the properties defining the custom role with the following commands.
    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/createspandcert.sh
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/customAzureRole.json
    ```
2. Login to Azure using the Azure CLI.
    ```
    azure login 
    ```
    You will be directed to http://aka.ms/devicelogin and enter the code provided. The command will output a success response when you are successfully logged in.
3. Locate your Subscription ID. You can either use the Azure Portal to find your subscription ID or you can execute the following command.
    ```
    azure account list
    ```
3. Edit the `customAzureRole.json` file. In the `AssignableScopes` section, add your subscription ID in the form `/subscriptions/<your subscription ID>` 
4. Now that you have your subscription ID, execute the bash script as follows.
    ```
    sudo bash createspandcert.sh -s <subscriptionID> -a <hanvaapp> -c <hanvaapp> -r <role name used in customAzureRole.json>
    ```
    You will be prompted for a new password and asked to confirm a password two times. The first password is for the key store. The second password is for the certificate password. Make note of these passwords, you will have to enter them in the client configuration file later.
    
    Once the bash script has completed, the script will output the application ID and the Tenant ID as follows.
    ```
    =====================================================
    Application Id : <application ID>
    Tenant Id      : <tenant ID>
    =====================================================
    ```
    Save this information as you will need to enter this into a configuration file that is used to create the docker image for the NVA monitor client.
5. Copy the certificate to the mounted fileshare for use on the other VMs.
    ```
    cp nva.jks <path to mounted fileshare>/nva.jks
    ```
