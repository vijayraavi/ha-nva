#! /bin/bash
##################################################################################################
#     bash script to create the certificates and service principal
#     ./createspandcert.sh -s {subscriptionId} -a {applicationName} -c {certSubjectName} - r {roleName}
#
#
#
#
###################################################################################################

func_err() {
        echo ""
        echo "Error executing Command"
        echo ${command}
        exit $?
}


trap  func_err  ERR

application=""
subscription=""
certificatesubject=""
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
    -c|--certificateSubject)
    certificatesubject="$2"
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

if  [ -z "$subscription" ] || [ -z "$application" ] || [ -z "$certificatesubject" ] || [ -z "$roleName" ] ; then
  echo "missing parameters usage"
  echo "./createspandert.sh -s subscriptionid -a applicationName -c certsubjectName -r roleName"
fi

openssl req -x509 -days 3650 -newkey rsa:2048 -out cert.pem -nodes -subj "/CN=${certificatesubject}"

openssl pkcs12 -export -in cert.pem -inkey privkey.pem -out nva.pfx
keytool -importkeystore -srckeystore nva.pfx -srcstoretype pkcs12 -destkeystore nva.jks -deststoretype JKS

cat privkey.pem cert.pem > nvacert.pem

echo $(grep -v -e CERTIFICATE cert.pem) > cert.txt

cert=`cat cert.txt`

azure account set  "${subscription}"

azure ad app create -n $application --home-page http://${application} \
--identifier-uris http://${application} --cert-value "${cert}"


appid=$(azure ad app show ${application} \
-i "http://${application}"  --json |  jq -r '.[0].appId')

echo $appid

azure ad sp create -a $appid

objectid=$(azure ad sp show \
-n  http://${application} --json | jq -r '.[0].objectId')

sleep 30

echo $objectid
#role=$(cat CustomAzureRole.json | jq '.Name')
azure role create --inputfile customAzureRole.json
roleid=$(azure role show -n "${roleName}" --json | jq '.[0].Id')

echo  ${roleid}
azure role assignment create --objectId ${objectid} -o "${roleName}"
#-c /subscriptions/${subscription}

tenant=$(azure account show --json | jq -r '.[0].tenantId')

echo  "====================================================="
echo "Application Id : ${appid}"
echo "Tenant Id      : ${tenant}"
echo "======================================================="


thumb=$(openssl x509 -in nvacert.pem -fingerprint -noout | sed 's/SHA1 Fingerprint=//g'  | sed 's/://g')

echo "commands executed successfully. to test run command below"
echo "============================================================"

echo "azure login --service-principal --tenant ${tenant}  -u ${appid} --certificate-file nvacert.pem --thumbprint ${thumb}"
