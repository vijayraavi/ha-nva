##########################################################################
# Instlal docker and add group to avoid sudo privileges
#########################################################################

#!/bin/bash

sudo apt-get update
sudo apt-get install apt-transport-https ca-certificates
sudo apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D

SOURCES="/etc/apt/sources.list.d/docker.list"


if [ ! -f "$SOURCES" ]; then
  
   sudo  echo "deb https://apt.dockerproject.org/repo ubuntu-precise main" >> "$SOURCES"
    
fi

exit


sudo apt-get install linux-image-extra-$(uname -r) linux-image-extra-virtual
sudo apt-get install linux-image-generic-lts-trusty
#sudo reboot
