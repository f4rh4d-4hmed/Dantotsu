package ani.dantotsu.connections.anilist

import ani.dantotsu.connections.anilist.Anilist.executeQuery
import ani.dantotsu.connections.anilist.api.FuzzyDate
import ani.dantotsu.connections.anilist.api.Query
import ani.dantotsu.currContext
import com.google.gson.Gson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonPrimitive

class AnilistMutations {

    suspend fun toggleFav(anime: Boolean = true, id: Int) {
        val query =
            """mutation (${"$"}animeId: Int,${"$"}mangaId:Int) { ToggleFavourite(animeId:${"$"}animeId,mangaId:${"$"}mangaId){ anime { edges { id } } manga { edges { id } } } }"""
        val variables = if (anime) """{"animeId":"$id"}""" else """{"mangaId":"$id"}"""
        executeQuery<JsonObject>(query, variables)
    }
        suspend inline fun <reified T : Any> executeQuery(
        query: String,
        variables: String = "",
        force: Boolean = false,
        useToken: Boolean = true,
        show: Boolean = false,
        cache: Int? = null
    ): T? {
        return try {
            // Attempt AniList query first
            if (show) Logger.log("Anilist Query: $query")
            val aniListResult = executeAniListQuery<T>(query, variables, force, useToken, show, cache)
            aniListResult ?: throw Exception("AniList query failed, switching to Jikan fallback.")
        } catch (e: Exception) {
            // Fallback to Jikan API if AniList query fails
            if (show) snackString("Falling back to Jikan API")
            Logger.log("Falling back to Jikan API for query: $query due to error: ${e.message}")
            executeJikanFallback<T>(query, show)
        }
    }

    private suspend inline fun <reified T : Any> executeAniListQuery(
        query: String,
        variables: String,
        force: Boolean,
        useToken: Boolean,
        show: Boolean,
        cache: Int?
    ): T? {
        // AniList API request setup (similar to original method)
        val data = mapOf("query" to query, "variables" to variables)
        val headers = mutableMapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Accept" to "application/json"
        )
        if (token != null && useToken) headers["Authorization"] = "Bearer $token"

        val json = client.post(
            "https://graphql.anilist.co/",
            headers,
            data = data,
            cacheTime = cache ?: 10
        )
        
        if (json.code == 429) {
            rateLimitReset = json.headers["X-RateLimit-Reset"]?.toLongOrNull() ?: 0
            throw Exception("Rate limited after ${json.headers["Retry-After"]?.toIntOrNull() ?: -1} seconds")
        }
        if (!json.text.startsWith("{")) throw Exception(currContext()?.getString(R.string.anilist_down))

        return json.parsed()
    }

    private suspend inline fun <reified T : Any> executeJikanFallback(
        query: String,
        show: Boolean
    ): T? {
        // Jikan fallback setup (e.g., for top anime or seasonal anime)
        val jikanUrl = "https://api.jikan.moe/v4/top/anime"  // example URL
        val jikanJson = client.get(jikanUrl)

        if (show) Logger.log("Jikan API Response: ${jikanJson.text}")
        
        val jikanData = jikanJson.parsed<JsonObject>()
        val transformedData = transformJikanToAniListFormat(jikanData)
        return transformedData?.let { Json.decodeFromJsonElement(it) }
    }

    private fun transformJikanToAniListFormat(jikanData: JsonObject): JsonObject? {
        val animeList = jikanData["data"]?.jsonArray?.map { anime ->
            JsonObject(
                mapOf(
                    "id" to anime.jsonObject["mal_id"]!!,
                    "title" to anime.jsonObject["title"]!!,
                    "popularity" to anime.jsonObject["members"]!!,
                    "episodes" to anime.jsonObject["episodes"] ?: JsonNull,
                    "status" to JsonPrimitive("RELEASING"),
                    "start_date" to anime.jsonObject["aired"]?.jsonObject?.get("from") ?: JsonNull
                )
            )
        } ?: return null

        return JsonObject(mapOf("data" to JsonObject(mapOf("Page" to JsonObject(mapOf("media" to JsonArray(animeList))))))
    }
}

    suspend fun toggleFav(type: FavType, id: Int): Boolean {
        val filter = when (type) {
            FavType.ANIME -> "animeId"
            FavType.MANGA -> "mangaId"
            FavType.CHARACTER -> "characterId"
            FavType.STAFF -> "staffId"
            FavType.STUDIO -> "studioId"
        }
        val query = """mutation{ToggleFavourite($filter:$id){anime{pageInfo{total}}}}"""
        val result = executeQuery<JsonObject>(query)
        return result?.get("errors") == null && result != null
    }

    enum class FavType {
        ANIME, MANGA, CHARACTER, STAFF, STUDIO
    }

    suspend fun editList(
        mediaID: Int,
        progress: Int? = null,
        score: Int? = null,
        repeat: Int? = null,
        notes: String? = null,
        status: String? = null,
        private: Boolean? = null,
        startedAt: FuzzyDate? = null,
        completedAt: FuzzyDate? = null,
        customList: List<String>? = null
    ) {

        val query = """
            mutation ( ${"$"}mediaID: Int, ${"$"}progress: Int,${"$"}private:Boolean,${"$"}repeat: Int, ${"$"}notes: String, ${"$"}customLists: [String], ${"$"}scoreRaw:Int, ${"$"}status:MediaListStatus, ${"$"}start:FuzzyDateInput${if (startedAt != null) "=" + startedAt.toVariableString() else ""}, ${"$"}completed:FuzzyDateInput${if (completedAt != null) "=" + completedAt.toVariableString() else ""} ) {
                SaveMediaListEntry( mediaId: ${"$"}mediaID, progress: ${"$"}progress, repeat: ${"$"}repeat, notes: ${"$"}notes, private: ${"$"}private, scoreRaw: ${"$"}scoreRaw, status:${"$"}status, startedAt: ${"$"}start, completedAt: ${"$"}completed , customLists: ${"$"}customLists ) {
                    score(format:POINT_10_DECIMAL) startedAt{year month day} completedAt{year month day}
                }
            }
        """.replace("\n", "").replace("""    """, "")

        val variables = """{"mediaID":$mediaID
            ${if (private != null) ""","private":$private""" else ""}
            ${if (progress != null) ""","progress":$progress""" else ""}
            ${if (score != null) ""","scoreRaw":$score""" else ""}
            ${if (repeat != null) ""","repeat":$repeat""" else ""}
            ${if (notes != null) ""","notes":"${notes.replace("\n", "\\n")}"""" else ""}
            ${if (status != null) ""","status":"$status"""" else ""}
            ${if (customList != null) ""","customLists":[${customList.joinToString { "\"$it\"" }}]""" else ""}
            }""".replace("\n", "").replace("""    """, "")
        println(variables)
        executeQuery<JsonObject>(query, variables, show = true)
    }

    suspend fun deleteList(listId: Int) {
        val query = "mutation(${"$"}id:Int){DeleteMediaListEntry(id:${"$"}id){deleted}}"
        val variables = """{"id":"$listId"}"""
        executeQuery<JsonObject>(query, variables)
    }


    suspend fun rateReview(reviewId: Int, rating: String): Query.RateReviewResponse? {
        val query = "mutation{RateReview(reviewId:$reviewId,rating:$rating){id mediaId mediaType summary body(asHtml:true)rating ratingAmount userRating score private siteUrl createdAt updatedAt user{id name bannerImage avatar{medium large}}}}"
        return executeQuery<Query.RateReviewResponse>(query)
    }

    suspend fun postActivity(text:String): String {
        val encodedText = text.stringSanitizer()
        val query = "mutation{SaveTextActivity(text:$encodedText){siteUrl}}"
        val result = executeQuery<JsonObject>(query)
        val errors = result?.get("errors")
        return errors?.toString()
            ?: (currContext()?.getString(ani.dantotsu.R.string.success) ?: "Success")
    }

    suspend fun postReview(summary: String, body: String, mediaId: Int, score: Int): String {
        val encodedSummary = summary.stringSanitizer()
        val encodedBody = body.stringSanitizer()
        val query = "mutation{SaveReview(mediaId:$mediaId,summary:$encodedSummary,body:$encodedBody,score:$score){siteUrl}}"
        val result = executeQuery<JsonObject>(query)
        val errors = result?.get("errors")
        return errors?.toString()
            ?: (currContext()?.getString(ani.dantotsu.R.string.success) ?: "Success")
    }

    suspend fun postReply(activityId: Int, text: String): String {
        val encodedText = text.stringSanitizer()
        val query = "mutation{SaveActivityReply(activityId:$activityId,text:$encodedText){id}}"
        val result = executeQuery<JsonObject>(query)
        val errors = result?.get("errors")
        return errors?.toString()
            ?: (currContext()?.getString(ani.dantotsu.R.string.success) ?: "Success")
    }

    private fun String.stringSanitizer(): String {
        val sb = StringBuilder()
        var i = 0
        while (i < this.length) {
            val codePoint = this.codePointAt(i)
            if (codePoint > 0xFFFF) {
                sb.append("&#").append(codePoint).append(";")
                i += 2
            } else {
                sb.append(this[i])
                i++
            }
        }
        return Gson().toJson(sb.toString())
    }
}
