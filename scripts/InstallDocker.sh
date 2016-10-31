#############################################################
# Install docker and add docker group to avoid sudo context
######################################################

sudo apt-get update
sudo apt-get install docker-engine
sudo groupadd docker
sudo usermod -aG docker $USER
echo "logout and log back in for security groups context"

