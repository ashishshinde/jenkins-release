package com.aerospike.connect.gradle

import com.netflix.gradle.plugins.deb.Deb

/**
 * Override to create saner deb file name.
 */
open class AeroDeb : Deb() {
    override fun assembleArchiveName(): String {
        addParentDirs
        var name = packageName
        name += if (archiveVersion.isPresent) "-${archiveVersion.get()
            .replace("~SNAPSHOT", "")}" else ""
        name += if (release != null && release.isNotBlank()) "-$release" else ""
        name += if (archString != null && archString.isNotBlank()) ".$archString" else ""
        name += if (archiveExtension.isPresent) ".${archiveExtension.get()}" else ""
        return name
    }
}