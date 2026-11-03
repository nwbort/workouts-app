package com.nwbort.workouts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

/**
 * One activity from Garmin Connect, cut down to what the app shows. Read-only:
 * Garmin's Personal API can pull activities but not push workouts.
 */
@Serializable
data class GarminActivity(
    val id: Long,
    /** Local start date, ISO yyyy-MM-dd. */
    val date: String,
    /** Garmin's activity type key, e.g. "running", "trail_running", "hiking". */
    val type: String,
    val name: String = "",
    val distanceM: Double? = null,
    val durationS: Double? = null,
    /** Local start time, "yyyy-MM-dd HH:mm:ss", for ordering a day's activities. */
    val start: String = "",
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val km: Double? get() = distanceM?.takeIf { it > 0 }?.div(1000)
    val isRun: Boolean get() = "run" in type
    val isWalk: Boolean get() = type == "walking" || type == "hiking" || "walk" in type

    /** Whether this activity counts for a session of [kind]. */
    fun counts(kind: String): Boolean = when (kind) {
        Kind.RUN, Kind.RACE -> isRun
        Kind.WALK -> isWalk
        else -> false
    }
}

object Garmin {
    const val BASE_URL = "https://personal.connectapi.garmin.com"
    const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
    const val SEARCH_PATH = "/activitylist-service/activities/search/activities"
    /** Where the user makes a key: Garmin Connect, Settings, Personal API token. */
    const val KEY_PAGE = "https://connect.garmin.com/app/settings/personalApiToken"
    /** The expiry periods Garmin offers when making a key. */
    val KEY_LIFETIMES = listOf(30, 90, 180, 365)

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses an activity search response; anything unexpected is skipped rather than failing the sync. */
    fun parseActivities(body: String): List<GarminActivity> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val items = when (root) {
            is JsonArray -> root
            // Some Garmin list endpoints wrap the list; accept either shape.
            is JsonObject -> (root["activityList"] ?: root["activities"]) as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return items.mapNotNull { (it as? JsonObject)?.toActivity() }
    }

    private fun JsonObject.toActivity(): GarminActivity? {
        val id = num("activityId")?.toLong() ?: return null
        val start = str("startTimeLocal") ?: return null
        val date = start.take(10).takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: return null
        val type = (this["activityType"] as? JsonObject)?.str("typeKey")
            ?: str("activityType") ?: "other"
        return GarminActivity(
            id = id,
            date = date,
            type = type,
            name = str("activityName").orEmpty(),
            distanceM = num("distance"),
            durationS = num("duration") ?: num("elapsedDuration"),
            start = start,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.longOrNull?.toDouble() }

    /** Seconds since the epoch at which a JWT access token expires, or null when it can't be read. */
    fun tokenExpiry(token: String): Long? = runCatching {
        val payload = token.split('.')[1]
        val claims: JsonElement = json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.trimEnd('='))))
        (claims.jsonObject["exp"] as JsonPrimitive).longOrNull
    }.getOrNull()

    /**
     * Which activity goes with which session on each day: a day's activities,
     * earliest first, fill the sessions they count for in order. Keyed by
     * [sessionKey].
     */
    fun assign(snap: Snapshot, activities: List<GarminActivity>): Map<String, GarminActivity> {
        val out = LinkedHashMap<String, GarminActivity>()
        activities.groupBy { it.date }.forEach { (date, onDay) ->
            val day = snap.schedule.dayAt(LocalDate.parse(date)) ?: return@forEach
            // Required sessions first, so a run doesn't fill an optional extra while the main run waits.
            val slots = day.sessions.indices.sortedBy { if (day.sessions[it].optional) 1 else 0 }.toMutableList()
            onDay.sortedBy { it.start }.forEach { a ->
                val i = slots.firstOrNull { a.counts(day.sessions[it].kind) } ?: return@forEach
                slots.remove(i)
                out[sessionKey(snap.plan.slug, day, i)] = a
            }
        }
        return out
    }

    /**
     * Sessions to tick off from Garmin: every assigned session not yet ticked
     * by Garmin before. [applied] remembers those, so unticking one by hand
     * sticks instead of coming back on the next sync.
     */
    fun autoTicks(snap: Snapshot, activities: List<GarminActivity>, applied: Set<String>): Set<String> =
        assign(snap, activities).keys.filterTo(mutableSetOf()) { it !in applied && it !in snap.done }
}

/** Garmin refused the key or token: it has expired, been revoked, or was mistyped. */
class GarminAuthException(message: String) : IOException(message)

/** The few HTTP calls the app makes to Garmin's Personal API. */
class GarminApi(private val baseUrl: String = Garmin.BASE_URL) {

    /** Exchanges a Personal API key for an access token, valid up to 24 hours. */
    fun token(apiKey: String): String {
        val body = request("POST", Garmin.TOKEN_PATH, "ApiKey ${apiKey.trim()}", "*/*")
        return body.trim().takeIf { it.isNotEmpty() } ?: throw IOException("Garmin sent back an empty token")
    }

    /** Every activity started between [from] and [to] inclusive. */
    fun activities(token: String, from: LocalDate, to: LocalDate): List<GarminActivity> {
        val all = mutableListOf<GarminActivity>()
        var start = 0
        while (true) {
            val query = mapOf(
                "start" to start.toString(),
                "limit" to PAGE.toString(),
                "startDate" to from.toString(),
                "endDate" to to.toString(),
            ).entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
            val page = Garmin.parseActivities(request("GET", "${Garmin.SEARCH_PATH}?$query", "Bearer $token", "application/json"))
            all += page
            if (page.size < PAGE || start >= MAX) break
            start += PAGE
        }
        return all.distinctBy { it.id }
    }

    private fun request(method: String, path: String, auth: String, accept: String): String {
        val conn = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Accept", accept)
            if (method == "POST") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
            }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw GarminAuthException("Garmin turned the key down ($code)")
            if (code !in 200..299) throw IOException("Garmin replied $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val PAGE = 100
        const val MAX = 1000
    }
}
