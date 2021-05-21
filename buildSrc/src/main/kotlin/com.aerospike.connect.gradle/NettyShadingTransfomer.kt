package com.aerospike.connect.gradle

import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.file.FileTreeElement
import shadow.org.apache.tools.zip.ZipEntry
import shadow.org.apache.tools.zip.ZipOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Netty libraries are shaded by prefixing all "io.netty" with
 * "shadow.aerospike". When run, netty looks for dynamic link file -
 * "shadow_aerospike_netty_transport_native_epoll.so" in the META-INF folder
 * packaged with the JAR. Renaming the dynamically linked file to conform to
 * the "shaded netty" runtime expectations.
 *
 * See
 * - https://imperceptiblethoughts.com/shadow/configuration/merging/
 * - https://github.com/johnrengelman/shadow/issues/293
 * - https://github.com/netty/netty/issues/6665
 */
class NettyShadingTransformer : Transformer {
    /**
     * The file contents.
     */
    private lateinit var fileContents: ByteArray

    /**
     * The file name to transform.
     */
    private val fileName =
        "META-INF/native/libnetty_transport_native_epoll_x86_64.so"

    /**
     * The transformed file name.
     */
    private val transformedFileName =
        "META-INF/native/libshadow_aerospike_netty_transport_native_epoll_x86_64.so"

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.name == fileName
    }

    override fun hasTransformedResource(): Boolean {
        return true
    }

    override fun transform(context: TransformerContext) {
        val os = ByteArrayOutputStream()
        IOUtils.copy(context.`is`, os)

        fileContents = os.toByteArray()
    }

    override fun modifyOutputStream(os: ZipOutputStream,
                                    preserveFileTimestamps: Boolean) {
        val zipEntry = ZipEntry(transformedFileName)
        zipEntry.time =
            TransformerContext.getEntryTimestamp(preserveFileTimestamps,
                zipEntry.time)
        os.putNextEntry(zipEntry)

        IOUtils.copy(ByteArrayInputStream(fileContents), os)
    }
}
