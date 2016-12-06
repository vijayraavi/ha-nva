#
# install-docker.sh
#
#!/bin/bash
apt-get -y update
apt-get -y install linux-image-extra-$(uname -r) linux-image-extra-virtual
apt-get -y install docker-en
service docker start