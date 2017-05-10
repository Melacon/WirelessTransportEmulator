FROM ubuntu:16.04

# install required packages
ENV DEBIAN_FRONTEND noninteractive
RUN ["apt-get", "update"]
RUN ["apt-get", "install", "-y", "net-tools", "openjdk-8-jre-headless", "openssh-server"]

# setup admin user that NetconfServerSimulator SSH connection will use
RUN set -x -e; \
    mkdir /var/run/sshd; \
    adduser --gecos '' --disabled-password admin; \
    echo "admin:admin" | chpasswd

# copy java NetconfServerSimulator jar file to container
COPY build/NetconfServerSimulator.jar /usr/NetconfServerSimulator/
COPY xmlNeModel/* /usr/NetconfServerSimulator/xmlNeModel/
COPY yang/yangNeModel/* /usr/NetconfServerSimulator/yang/yangNeModel/

# startup command
#java -classpath NESimServer.jar net.i2cat.netconf.server.Server ../xmlNeModel/DVM-ETY.xml 830 ../yang/yangNeModel
WORKDIR /usr/NetconfServerSimulator
RUN set -x -e; \
    printf '#!/bin/bash\nset -e -x\njava -classpath NetconfServerSimulator.jar net.i2cat.netconf.server.ServerSimulator xmlNeModel/DVM-ETY.xml 830 yang/yangNeModel\n' > /root/start.sh; \
    chmod 0755 /root/start.sh
CMD ["/root/start.sh"]


# Ports for SSH telnet and NETCONF access
EXPOSE 22
EXPOSE 830
