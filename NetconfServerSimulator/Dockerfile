FROM ubuntu:18.04

# install required packages
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update
RUN apt-get -y install net-tools openjdk-8-jre-headless openssh-server

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
COPY dockerstart.sh /root/dockerstart.sh
RUN  chmod 0755 /root/dockerstart.sh

WORKDIR /usr/NetconfServerSimulator
ENTRYPOINT "/root/dockerstart.sh"
