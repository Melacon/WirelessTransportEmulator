FROM ubuntu:14.04

# install required packages
ENV DEBIAN_FRONTEND noninteractive
RUN ["apt-get", "update"]
RUN ["apt-get", "install", "-y", "git", "autoconf", "gcc", "libtool", "libxml2-dev", "libssl-dev", "make", "libncurses5-dev", "libssh2-1-dev", "openssh-server"]
RUN ["apt-get", "update"]
RUN ["apt-get", "install", "-y", "iptables", "tcpdump"]

# setup admin user that openyuma will use
RUN set -x -e; \
    mkdir /var/run/sshd; \
    adduser --gecos '' --disabled-password admin; \
    echo "admin:admin" | chpasswd

# copy and build openyuma, setup the container to start it by default
COPY . /usr/src/OpenYuma

# install iperf3 from the .deb sources that we have locally
RUN set -x -e; \
    printf '#!/bin/bash\nset -e -x\ndpkg -i /usr/src/OpenYuma/*.deb\n' > /root/install_iperf.sh; \
    chmod 0755 /root/install_iperf.sh
RUN /root/install_iperf.sh


WORKDIR /usr/src/OpenYuma
RUN set -x -e; \
	make clean; \
    make; \
    sudo make install
WORKDIR /usr/src/OpenYuma/example-modules/core-model
RUN set -x -e; \
    make DVM=1; \
    sudo make install
WORKDIR /usr/src/OpenYuma/example-modules/microwave-model
RUN set -x -e; \
    make DVM=1; \
    sudo make install

WORKDIR /usr/src/OpenYuma
RUN set -x -e; \
    printf 'Port 830\nSubsystem netconf "/usr/sbin/netconf-subsystem --ncxserver-sockname=830@/tmp/ncxserver.sock"\n' >> /etc/ssh/sshd_config; \
    #printf '#!/bin/bash\nset -e -x\n/usr/sbin/netconfd --startup=/tmp/startup-cfg.xml --superuser=admin &\nsleep 1\n/usr/sbin/sshd -D &\nwait\nkill %%\n' > /root/start.sh; \
    printf '#!/bin/bash\nset -e -x\n/usr/sbin/netconfd --with-startup=true --startup=/usr/src/OpenYuma/startup-cfg.xml --target=running --superuser=admin --module=microwave-model --module=core-model&\nsleep 1\n/usr/sbin/sshd -D &\nwait\nkill %%\n' > /root/start.sh; \
    #printf '#!/bin/bash\nset -e -x\n/usr/sbin/netconfd --startup=/tmp/startup-cfg.xml --superuser=admin &\nsleep 1\n/usr/sbin/sshd -D &\n/bin/bash\n' > /root/start.sh; \
    chmod 0755 /root/start.sh
CMD ["/root/start.sh"]


#CMD ["/bin/bash"]

# finishing touches
EXPOSE 22
EXPOSE 830
