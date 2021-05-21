package com.aerospike.connect.gradle

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.dsl.FileSystemPublishArtifact

class PackagingPublishArtifact(
    location: FileSystemLocation,
    version: String,
    private val packageType: String
) :
    FileSystemPublishArtifact(location, version) {
    override fun getType(): String {
        return packageType
    }
}
