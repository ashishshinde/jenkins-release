/*
 * Copyright (c) 2019 Aerospike, Inc.
 *
 * All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE. THE COPYRIGHT NOTICE ABOVE DOES
 * NOT EVIDENCE ANY ACTUAL OR INTENDED PUBLICATION.
 */

include(
    "aerospike",
    "http-shim",
    "inbound",
    "io",
    "jms-dump",
    "jms-pool",
    "jms-inbound",
    "jms-loader",
    "jms-outbound",
    "kafka-inbound",
    "kafka-loader",
    "kafka-outbound",
    "licensing",
    "load-gen",
    "logging",
    "noop-outbound",
    "outbound-server",
    "pubsub-outbound",
//    "pulsar-inbound",
    "pulsar-outbound",
    "serde",
    "test",
    "tls",
    "undertow-patch",
    "xdr-proxy"
)

rootProject.name = "aerospike-connect"

// Add prefix to child project names.
rootProject.children.forEach { it.name = "aerospike-" + it.name }

buildscript {
    repositories {
        jcenter()
    }
}
