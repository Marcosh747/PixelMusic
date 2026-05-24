package com.unshoo.pixelmusic.data.remote.youtube

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import com.unshoo.pixelmusic.data.model.youtube.UmihiSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray

object YoutubeRequestHelper {
    fun browse(browseId: String, settings: UmihiSettings): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Browse.URL,
            idName = "browseId",
            id = browseId,
            settings = settings
        )
    }

    fun requestContinuation(continuationToken: String, settings: UmihiSettings): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Browse.URL,
            idName = "continuation",
            id = continuationToken,
            settings = settings
        )
    }

    fun getPlayerInfo(videoId: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.PlayerInfo.URL,
            idName = "videoId",
            id = videoId
        )
    }

    fun search(query: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Search.URL,
            idName = "query",
            id = query
        )
    }

    fun nextUp(videoId: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Next.URL,
            idName = "videoId",
            id = videoId
        )
    }

    fun like(videoId: String, settings: UmihiSettings): String {
        return requestWithTarget(
            url = "https://www.youtube.com/youtubei/v1/like/like",
            videoId = videoId,
            settings = settings
        )
    }

    fun removeLike(videoId: String, settings: UmihiSettings): String {
        return requestWithTarget(
            url = "https://www.youtube.com/youtubei/v1/like/removelike",
            videoId = videoId,
            settings = settings
        )
    }

    fun createPlaylist(title: String, videoIds: List<String>, settings: UmihiSettings, privacyStatus: String = "PRIVATE"): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(null, null, settings)
        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }
            put("title", JsonPrimitive(title))
            put("privacyStatus", JsonPrimitive(privacyStatus))
            if (videoIds.isNotEmpty()) {
                put("videoIds", buildJsonArray {
                    videoIds.forEach { id ->
                        add(JsonPrimitive(id))
                    }
                })
            }
        }

        val headers = YoutubeAuthHelper.getHeaders(settings.cookies)
        val url = "https://www.youtube.com/youtubei/v1/playlist/create"

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(headers)
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }

    fun addVideosToPlaylist(playlistId: String, videoIds: List<String>, settings: UmihiSettings): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(null, null, settings)
        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }
            put("playlistId", JsonPrimitive(playlistId))
            put("actions", buildJsonArray {
                videoIds.forEach { videoId ->
                    add(buildJsonObject {
                        put("action", JsonPrimitive("ACTION_ADD_VIDEO"))
                        put("addedVideoId", JsonPrimitive(videoId))
                    })
                }
            })
        }

        val headers = YoutubeAuthHelper.getHeaders(settings.cookies)
        val url = "https://www.youtube.com/youtubei/v1/browse/edit_playlist"

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(headers)
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String, settings: UmihiSettings): String {
        return addVideosToPlaylist(playlistId, listOf(videoId), settings)
    }

    private fun requestWithTarget(
        url: String,
        videoId: String,
        settings: UmihiSettings
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(null, null, settings)
        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }
            put("target", buildJsonObject {
                put("videoId", JsonPrimitive(videoId))
            })
        }

        val headers = YoutubeAuthHelper.getHeaders(settings.cookies)

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(headers)
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }

    private fun requestWithContext(
        url: String,
        idName: String,
        id: String,
        settings: UmihiSettings? = null
    ): String {
        val body =
            YoutubeAuthHelper.buildContextBody(idName, id, settings)

        val headers = if (settings != null) {
            YoutubeAuthHelper.getHeaders(settings.cookies)
        } else {
            mapOf()
        }

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(
                headers
            )
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }
}
