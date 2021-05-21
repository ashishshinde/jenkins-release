package com.aerospike.connect.gradle

import org.gradle.api.tasks.AbstractCopyTask
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun AbstractCopyTask.copyKafkaConfluentAssets() {
    val distBaseDir = "src/main/dist"
    val templates =
        listOf("doc/README.md", "manifest.json")

    templates.forEach { template ->
        val file = File("$template")
        val fileParent = file.parent

        from("$distBaseDir/$template") {
            val current = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val releaseDate = current.format(formatter)

            if (fileParent == null) {
                into("${project.name}-${project.version}")
            } else {
                into("${project.name}-${project.version}/$fileParent")
            }

            filter {
                it.replace("\${VERSION}", project.version.toString())
                    .replace("\${RELEASE_DATE}", releaseDate)
            }
        }
    }

    from(distBaseDir) {
        into("${project.name}-${project.version}")
        // Explicitly included above with token replacement.
        templates.forEach {
            exclude(it)
        }
    }
}
