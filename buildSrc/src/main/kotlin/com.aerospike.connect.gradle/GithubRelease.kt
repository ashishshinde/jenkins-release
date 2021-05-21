package com.aerospike.connect.gradle

import com.github.breadmoirai.githubreleaseplugin.GithubApi
import okhttp3.OkHttpClient
import org.gradle.api.Project
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


/**
 * Configuration for a release.
 */
data class GithubReleaseConfiguration(
    val owner: String = "",
    val repo: String = "",
    val accessToken: String = "",
    val tagName: String = "",
    val targetCommitish: String = "",
    val releaseName: String = "",
    val body: String = "",
    val releaseAssets: List<File> = emptyList(),
    val apiEndpoint: String = "",
    val project: Project
)

object GithubRelease {
    private val log = LoggerFactory.getLogger(GithubRelease::class.java)

    fun publishRelease(githubReleaseConfiguration: GithubReleaseConfiguration) {
        GithubApi.setEndpoint(githubReleaseConfiguration.apiEndpoint)
        println("@@@@@ : ${GithubApi.getEndpoint()} :: $githubReleaseConfiguration")
        val authValue = "Token ${githubReleaseConfiguration.accessToken}"
        val api = GithubApi(authValue)
        GithubApi.client = OkHttpClient()
        createRelease(
            api,
            githubReleaseConfiguration
        )
    }

    private fun createRelease(
        api: GithubApi,
        githubReleaseConfiguration: GithubReleaseConfiguration
    ) {
        val values = mapOf(
            "tag_name" to githubReleaseConfiguration.tagName,
            "target_commitish" to githubReleaseConfiguration.targetCommitish,
            "name" to githubReleaseConfiguration.releaseName,
            "body" to githubReleaseConfiguration.body,
            "draft" to false,
            "prerelease" to false
        )


        val response = api.postRelease(
            githubReleaseConfiguration.owner,
            githubReleaseConfiguration.repo,
            values
        )

        if (response.code != 201) {
            if (response.code == 404) {
                throw Exception("404 Repository with Owner: '${githubReleaseConfiguration.owner}' and Name: '${githubReleaseConfiguration.repo}' was not found")
            }
            throw Exception("Could not create release: ${response.code} ${response.message}\n${response.body}")
        } else {
            log.info("Status ${response.message.toUpperCase()}")
            log.info("${response.body}")
            if (githubReleaseConfiguration.releaseAssets.isNotEmpty()) {
                log.info("Uploading Assets")
                @Suppress("UNCHECKED_CAST")
                uploadAssetsToUrl(
                    api,
                    ((response.body as Map<String, Any>).get("upload_url")) as String,
                    githubReleaseConfiguration.project,
                    githubReleaseConfiguration.releaseAssets
                )
            }
        }
    }

    private fun uploadAssetsToUrl(
        api: GithubApi,
        url: String,
        project: Project,
        releaseAssets: List<File>
    ) {
        for (asset in releaseAssets) {
            log.info(
                "Uploading " + project.projectDir.toPath()
                    .resolve(asset.toPath())
            )
            if (!asset.isFile) {
                log.info("Cannot upload ${asset.name} with file size 0")
                continue
            }

            val encodedUrl = url.replace(
                "{?name,label}", "?name=${
                    URLEncoder.encode(
                        asset.name,
                        StandardCharsets.UTF_8.displayName()
                    )
                }"
            )
            val response = api.uploadFileToUrl(encodedUrl, asset)
            if (response.code != 201) {
                log.error(":githubRelease failed to upload ${asset.name}\n${response.code} ${response.message}\n${response.body}")
            }
        }
    }

}