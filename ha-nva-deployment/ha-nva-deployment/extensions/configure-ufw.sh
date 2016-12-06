
#
# install-docker.sh
#
#!/bin/bash
bash -c "echo net.ipv4.ip_forward=1 >> /etc/sysctl.conf"
sysctl -p /etc/sysctl.conf

PIP=$1
DEST_IP_ADDRESS=$2

iptables -F
iptables -t nat -F
iptables -X
iptables -t nat -A PREROUTING -p tcp --dport 80 -j DNAT --to-destination "${DEST_IP_ADDRESS}:80"
iptables -t nat -A PREROUTING -p tcp --dport 443 -j DNAT --to-destination "${DEST_IP_ADDRESS}:443"
iptables -t nat -A POSTROUTING -p tcp -d $DEST_IP_ADDRESS --dport 80 -j SNAT --to-source $PIP_ADDRESS
iptables -t nat -A POSTROUTING -p tcp -d $DEST_IP_ADDRESS --dport 443 -j SNAT --to-source $PIP_ADDRESS

service ufw stop
service ufw start

apt install -y aptitude
DEBIAN_FRONTEND=noninteractive aptitude install -y -q iptables-persistent
/etc/init.d/iptables-persistent save
update-rc.d iptables-persistent defaults