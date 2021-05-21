package com.aerospike.connect.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Docker hub HTTP API invocation utility.
 */
object DockerHub {

    private val objectMapper = ObjectMapper()

    fun deleteTags(dockerHubUsername: String, dockerHubPassword: String,
                   imageName: String, imageTags: List<String>) {
        val token = getToken(dockerHubUsername, dockerHubPassword)
        val okHttpClient = getClient()
        imageTags.forEach {
            val request = Request.Builder()
                .url(
                    "https://hub.docker.com/v2/repositories/$imageName/tags/$it/")
                .header("Authorization", "JWT $token")
                .delete()
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val deleteFailure = "Error occurred while deleting " +
                        "$imageName:$it - ${response.code()} - ${response.body()}"
                throw Exception(deleteFailure)
            } else {
                println("Deleted $imageName:$it")
            }
        }
    }

    fun deleteAllTags(dockerHubUsername: String, dockerHubPassword: String,
                      imageName: String) {
        while (true) {
            val tags = getTags(dockerHubUsername, dockerHubPassword, imageName)
            if (tags.isNullOrEmpty()) {
                break
            }
            deleteTags(dockerHubUsername, dockerHubPassword, imageName, tags)
        }
    }

    private fun getClient(): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor { chain ->
            val request: Request = chain.request()

            // try the request
            var response: Response = chain.proceed(request)
            var tryCount = 0
            while (!response.isSuccessful && tryCount < 5) {
                println("Request is not successful - ${response.code()} " +
                        "for request ${request.url()} try - $tryCount")
                tryCount++

                response.close()
                // retry the request
                response = chain.proceed(request)
            }

            // otherwise just pass the original response on
            response
        }.build()
    }

    private fun getToken(dockerHubUsername: String,
                         dockerHubPassword: String): String {
        val request = Request.Builder()
            .url("https://hub.docker.com/v2/users/login/")
            .post(
                RequestBody.create(
                    MediaType.parse("application/json"), """
                    {
                      "username": "$dockerHubUsername",
                      "password": "$dockerHubPassword"
                    }
                """.trimIndent())).build()
        val response = getClient().newCall(request).execute()
        val body = response.body()
        if (!response.isSuccessful) {
            throw Exception("error getting token ${response.code()} - $body")
        }
        val map = objectMapper.readValue(body?.string(), Map::class.java)
        return map["token"] as String
    }

    private fun getTags(dockerHubUsername: String, dockerHubPassword: String,
                        imageName: String): List<String> {
        val token = getToken(dockerHubUsername, dockerHubPassword)
        val request = Request.Builder()
            .url("https://hub.docker.com/v2/repositories/$imageName/tags/")
            .header("Authorization", "JWT $token")
            .get()
            .build()
        val response = getClient().newCall(request).execute()
        if (!response.isSuccessful) {
            val deleteFailure =
                "Error occurred while getting tags for $imageName - " +
                        "${response.code()} - ${response.body()}"
            throw Exception(deleteFailure)
        } else {
            val tagsMetadata = objectMapper.readValue(response.body()?.string(),
                Map::class.java)
            @Suppress("UNCHECKED_CAST")
            return (tagsMetadata["results"] as List<Map<String, Any>>).map {
                it["name"].toString()
            }
        }
    }
}
