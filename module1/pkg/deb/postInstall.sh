# create aerospike group if it isn't already there
if ! getent group aerospike >/dev/null; then
    groupadd -r aerospike
fi

# create aerospike user if it isn't already there
if ! getent passwd aerospike >/dev/null; then
    useradd -r -d /opt/aerospike -c 'Aerospike services' -g aerospike -s /sbin/nologin aerospike
fi

mkdir -p /var/log/aerospike-jms-outbound
mkdir -p /etc/aerospike-jms-outbound
mkdir -p /opt/aerospike-jms-outbound/usr-lib

for dir in /opt/aerospike-jms-outbound /var/log/aerospike-jms-outbound ; do
    if [ -d $dir ]; then
      chown -R aerospike:aerospike $dir
    fi
done

if [ -d /run/systemd/system ]; then
    systemctl --system daemon-reload >/dev/null 2>&1 || true
fi