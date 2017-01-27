# Bash Scripts to Create an Azure AD Service Principal and Application object

As part of the high availability network virtual appliance solution, the Microsoft Azure CAT patterns & practices team has created a set of three bash scripts to create an [Azure AD application object and service principal](https://docs.microsoft.com/azure/active-directory/develop/active-directory-application-objects). 

There are three bash scripts available. All three scripts create the Azure AD application object and service principal for you. If you do not have a certificate, one of the scripts will create it for you. If you do not want the service principal to authenticate in Azure with a certificate, one of the scripts creates the service principal with a password.

# Steps Common To All Scripts

Each of the three scripts has a common set of steps:

1. Copy a JSON file that includes the [properties defining the custom RBAC role](https://docs.microsoft.com/azure/active-directory/role-based-access-control-custom-roles) with the following command:
   ```
   wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/customAzureRole.json
    ```
2. [Log in to Azure from the Azure CLI](https://docs.microsoft.com/azure/xplat-cli-connect):
    ```
    azure login 
    ```
    You will be directed to http://aka.ms/devicelogin and enter the code provided. The command will output a success response when you are successfully logged in.

3. Locate your Subscription ID. You can either use the Azure Portal to find your subscription ID or you can execute the following command:

    ```
    azure account list
    ```

    The output from this command will be:

    ```
    info:    Executing command account list
    data:    Name                                  Id                                    Current  State
    data:    ------------------------------------  ------------------------------------  -------  -------
    data:    <Subscription Name>                   xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx  true     Enabled
    info:    account list command OK
    ```
    
    Find the name of the subscription where you'll be creating the Azure AD application object and service principal and the subscription ID is listed in the `Id` column.

4. Edit the `customAzureRole.json` file. In the `AssignableScopes` section, add your subscription ID in the form `/subscriptions/<your subscription ID>`. The default value of the `Name` property is `NVA Operator`. Change the value of this property if you would like to use a different role name.

# Bash Script to Create the Service Principal and Certificate

After you have completed the common steps above, you can execute the bash script to create the Azure AD application object, service principal, and certficate by following these steps:

1. Copy the bash script locally to the VM with the following command:

    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/createspandcert.sh
    ```

2. Execute the bash script as follows:

    ```
    sudo bash createspandcert.sh -s <subscriptionID> -a <your Azure AD application name> -c <your certificate subject name> -r <your RBAC role name from the customAzureRole.json file>
    ```

    The Azure AD application name can be any name you choose. The certificate subject name can also be any name you choose. The RBAC role name *must* agree with the RBAC role name specified in the `customAzureRole.json` file.

    > You will twice be prompted for a new password and asked to confirm a password. The first password is for the key store. The second password is for the certificate password. We strongly recommend you record these passwords and store them in a secure location. These passwords are required for the high availability NVA solution configuration file.
    
Once the bash script has completed, the script will output the application ID and the Tenant ID as follows:
```
=====================================================
Application Id : <application ID>
Tenant Id      : <tenant ID>
=====================================================
```
Save this information as you will need to enter this into a configuration file that is used to create the docker image for the NVA monitor client.

# Bash Script to Create the Service Principal Using an Existing Certificate

After you have completed the common steps above, you can execute the bash script to create the Azure AD application object and service principal with an existing certficate by following these steps:

1. Copy the bash script locally to the VM with the following command:
    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/createspwithcert.sh
    ```
2. Execute the bash script as follows:
    ```
    sudo bash createspandcert.sh -s <subscriptionID> -a <your Azure AD application name> -c <path to certificate>
    ```
    The Azure AD application name can be any name you choose. <!--The RBAC role name *must* agree with the RBAC role name specified in the `customAzureRole.json` file. -->

Once the bash script has completed, the script will output the application ID and the Tenant ID as follows:
```
=====================================================
Application Id : <application ID>
Tenant Id      : <tenant ID>
=====================================================
```
Save this information as you will need to enter this into a configuration file that is used to create the docker image for the NVA monitor client.

# Bash Script to Create the Service Principal Using a Password

After you have completed the common steps above, you can execute the bash script to create the Azure AD application object and service principal with a password by following these steps:

1. Copy the bash script locally to the VM with the following command:
    ```
    wget https://raw.githubusercontent.com/mspnp/ha-nva/master/ha-nva-deployment/scripts/createspwithcert.sh
    ```
2. Execute the bash script as follows:
    ```
    sudo bash createspwithpassword.sh -s <subscriptionID> -a <your Azure AD application name> -p <service principal password> -r <your RBAC role name from the customAzureRole.json file>
    ```
    The Azure AD application name can be any name you choose. The RBAC role name *must* agree with the RBAC role name specified in the `customAzureRole.json` file. 

Once the bash script has completed, the script will output the application ID and the Tenant ID as follows:
```
=====================================================
Application Id : <application ID>
Tenant Id      : <tenant ID>
=====================================================
```
Save this information as you will need to enter this into a configuration file that is used to create the docker image for the NVA monitor client.