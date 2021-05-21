# Stop the connector service.
if [ -d /run/systemd/system ]; then
    systemctl stop aerospike-jms-outbound-http >/dev/null 2>&1 || true
fi
