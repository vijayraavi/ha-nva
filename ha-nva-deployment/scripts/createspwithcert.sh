#! /bin/sh
##################################################################################################
#     bash script to create service principal with provided certificate
#     certificate in pfx format needs to be provided
#     ./createspwithcertificate.sh -s={subscriptionid} -a=logicalappname -c=certfile.pfx
#
#
#
#
###################################################################################################

func_err()
{
        echo "Error At line - $1"
        echo "last command"
        echo ${command}
        exit $?
}

trap "{ func_err()  \${LINENO} ; }" ERR


trap  'echo error executing command, echo  ${command}' ERR


application=""
subscription=""
certificatesubject=""
export command=""

for i in "$@"
do
case $i in
    -s=*|--subscriptionId=*)
    subscription="${i#*=}"
    shift # past argument=value
    ;;
    -a=*|--appName=*)
    application="${i#*=}"
    shift # past argument=value
    ;;
    -c=*|--certSubject=*)
    certificatesubject="${i#*=}"
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


if  [ -z "$subscription" ] || [ -z "$application" ] || [ -z "$certificatesubject" ]  ; then
  echo "missing parameters usage"
  echo "./createspwithcert.sh -s=subscriptionid -a=applicationName -c=certsubjectName"
  exit
fi

openssl pkcs12 -in $certificatesubject -out nvacert.pem -nodes

keytool -importkeystore -srckeystore $certificatesubject -srcstoretype pkcs12 -destkeystore nva.jks -deststoretype JKS


echo $(grep -v -e CERTIFICATE nvacert.pem) > cert.txt

cert=`cat cert.txt`
echo $cert
azure account set  "${subscription}"

azure ad app create -n $application --home-page http://${application} \
--identifier-uris http://${application} --cert-value "${cert}"


appid=$(azure ad app show ${application} \
-i "http://${application}" | awk '{if($2 ~ "AppId") print $3}')

echo $appid

azure ad sp create -a $appid

objectid=$(azure ad sp show \
-n  http://${application} -v | awk '{if($2 ~ "Object") print $4}')

sleep 30

echo $objectid

azure role assignment create --objectId ${objectid} -o owner \
-c /subscriptions/${subscription}/



tenant=$(azure account show | awk '{if($2 ~ "Tenant") print $5}')

echo  "====================================================="
echo "Application: ${appid}"
echo "Tenant: ${tenant}"
echo "======================================================="


thumb=$(openssl x509 -in nvacert.pem -fingerprint -noout | sed 's/SHA1 Fingerprint=//g'  | sed 's/://g')

echo "commands executed successfully. to test run command below"
echo "============================================================"

echo "azure login --service-principal --tenant ${tenant}  -u ${appid} --certificate-file nvacert.pem --thumbprint ${thumb}"
