#! /bin/bash
##################################################################################################
#     bash script to create service principal with provided password
#     ./createspwithpassword.sh -s {subscriptionId} -a {applicationName} -p {password} -r {roleName}
#
###################################################################################################
func_err()
{
	echo "Error At line - $1"
	echo "last command"
	echo ${command}
	exit $?
}


trap  func_err  ERR

application=""
subscription=""
password=""
roleName=""
export command=""

for i in "$@"
do
case $i in
    -s|--subscriptionId)
    subscription="$2"
    shift # past argument=value
    shift # past argument=value
    ;;
    -a|--appName)
    application="$2"
    shift # past argument=value
    shift # past argument=value
    ;;
    -p|--password)
    password="$2"
    shift # past argument=value
    shift # past argument=value
    ;;
    -r|--roleName)
    roleName="$2"
    shift # past argument=value
    shift # past argument=value
    ;;    
    --default)
    DEFAULT=YES
    shift # past argument with no value
    ;;
    *)
            # unknown option
    ;;
esac

done


if  [ -z "$subscription" ] || [ -z "$application" ] || [ -z "$password" ] || [ -z "$roleName" ]  ; then
  echo "missing parameters usage"
  echo "./createspwithcert.sh -s subscriptionId -a applicationName -p password -r roleName"
  exit
fi

azure account set  "${subscription}"

azure ad app create -n $application --home-page http://${application} \
--identifier-uris http://${application} --password ${password}


appid=$(azure ad app show ${application} \
-i "http://${application}" --json |  jq -r '.[0].appId')

echo $appid

azure ad sp create -a $appid

objectid=$(azure ad sp show \
-n  http://${application} --json | jq -r '.[0].objectId')

sleep 30

echo $objectid


azure role create --inputfile customAzureRole.json
roleid=$(azure role show -n "${roleName}" --json | jq '.[0].Id')

echo  ${roleid}
azure role assignment create --objectId ${objectid} -o "${roleName}"

tenant=$(azure account show --json | jq -r '.[0].tenantId')

echo  "====================================================="
echo "Application: ${appid}"
echo "Tenant: ${tenant}"
echo "======================================================="

echo "commands executed successfully. to test run command below"
echo "============================================================"
echo "azure login --service-principal --tenant ${tenant}  -u ${appid} --password ${password}"