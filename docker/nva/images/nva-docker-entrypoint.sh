#!/bin/bash

set -e

# Allow the container to be started with `--user`
#if [ "$1" = 'zkServer.sh' -a "$(id -u)" = '0' ]; then
#    chown -R "$ZOO_USER" "$ZOO_DATA_DIR" "$ZOO_DATA_LOG_DIR"
#    exec su-exec "$ZOO_USER" "$0" "$@"
#fi


echo "$0"
echo "$1"



if [ "$1" ==  "sudo" ]; then
    exec "$1" "$2"  ${NVA_USER} "$3" "$4" ${JAR} ${LOG} ${CLASS} "$5" ${CONFIG}
elif [ "$1" == "su-exec"  ]; then
    exec "$1"  ${NVA_USER} "$2" "$3" ${JAR} ${LOG} ${CLASS} "$4" ${CONFIG}   
else
   echo "Unkown parameter"
fi


#exec "$1"  ${NVA_USER} "$2" "$3" ${JAR} ${LOG} ${CLASS} "$4" ${CONFIG}




