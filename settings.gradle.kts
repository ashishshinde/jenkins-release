/*
 * Copyright (c) 2019 Aerospike, Inc.
 *
 * All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE. THE COPYRIGHT NOTICE ABOVE DOES
 * NOT EVIDENCE ANY ACTUAL OR INTENDED PUBLICATION.
 */

include(
    "module1",
    "module2"
)

rootProject.name = "aerospike-jenkins"

// Add prefix to child project names.
rootProject.children.forEach { it.name = "aerospike-" + it.name }

buildscript {
    repositories {
        jcenter()
    }
}
