package com.nwbort.workouts.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

private val Context.settingsStore by preferencesDataStore("settings")
private val Context.progressStore by preferencesDataStore("progress")
private val Context.playbackStore by preferencesDataStore("playback")

data class Settings(
    val planSlug: String,
    /** Race date for a plan with a race, otherwise its start date. */
    val anchor: LocalDate,
    /** What to call the plan; blank means its own title. */
    val name: String,
    /** Race distance to show instead of the plan's own, e.g. "60"; blank means the plan's. */
    val raceKm: String,
    val videoTree: String?,
    /** Videos linked by hand, Vimeo id -> document URI, for files the folder scan can't match. */
    val videoLinks: Map<String, String>,
    val notify: Boolean,
    /** Minutes after midnight. */
    val notifyAt: Int,
    val askedNotifyPermission: Boolean,
) {
    companion object {
        // Pre-filled with the plan this app was built for, so a fresh install
        // opens straight onto the right day. All of it is editable in Settings.
        const val DEFAULT_PLAN = "uta100-training-plan"
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2026, 12, 5)
        const val DEFAULT_NAME = "Kepler Challenge"
        const val DEFAULT_RACE_KM = "60"
        const val DEFAULT_NOTIFY_AT = 6 * 60
    }
}

/** Everything a screen needs to show the plan on real dates. */
data class Snapshot(val settings: Settings, val schedule: Schedule, val done: Set<String>) {
    val plan: Plan get() = schedule.plan
    val name: String get() = settings.name.ifBlank { plan.title }

    fun title(day: Day): String {
        val km = settings.raceKm.trim()
        return if (day.isRace && km.isNotEmpty()) "Race ${km}km" else day.title
    }

    fun km(day: Day): Double? = if (day.isRace) settings.raceKm.trim().toDoubleOrNull() ?: day.km else day.km

    fun isDone(day: Day, index: Int) = sessionKey(plan.slug, day, index) in done

    /** (done, required) for a day; required excludes rest and optional sessions. */
    fun progress(day: Day): Pair<Int, Int> {
        val req = day.required
        return req.count { isDone(day, it) } to req.size
    }

    fun isComplete(day: Day): Boolean {
        val (d, r) = progress(day)
        return r > 0 && d == r
    }
}

class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val plans = ConcurrentHashMap<String, Plan>()

    val planIndex: List<PlanSummary> by lazy { asset("plans/index.json") }
    val videos: List<VideoEntry> by lazy { asset("videos.json") }
    val videosById: Map<String, VideoEntry> by lazy { videos.associateBy { it.id } }

    private inline fun <reified T> asset(path: String): T =
        context.assets.open(path).bufferedReader().use { json.decodeFromString(it.readText()) }

    suspend fun plan(slug: String): Plan? = plans[slug] ?: withContext(Dispatchers.IO) {
        runCatching { asset<Plan>("plans/$slug.json") }.getOrNull()?.also { plans[slug] = it }
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { it.toSettings() }.distinctUntilChanged()

    val done: Flow<Set<String>> = context.progressStore.data.map { it[DONE] ?: emptySet() }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: Flow<Snapshot?> = combine(settings, done) { s, d -> s to d }.mapLatest { (s, d) ->
        (plan(s.planSlug) ?: plan(Settings.DEFAULT_PLAN))?.let { Snapshot(s, Schedule(it, s.anchor), d) }
    }

    suspend fun currentSnapshot(): Snapshot? = snapshot.first()

    suspend fun setDone(key: String, isDone: Boolean) {
        context.progressStore.edit {
            val cur = it[DONE] ?: emptySet()
            it[DONE] = if (isDone) cur + key else cur - key
        }
    }

    suspend fun resetProgress(slug: String) {
        context.progressStore.edit { prefs ->
            prefs[DONE] = (prefs[DONE] ?: emptySet()).filterNot { it.startsWith("$slug|") }.toSet()
        }
    }

    suspend fun updateSettings(block: SettingsEditor.() -> Unit) {
        context.settingsStore.edit { SettingsEditor(it).block() }
    }

    // Where a video was left off, so a strength session interrupted halfway
    // picks up again. Stored as "positionMs:savedAtEpochMs".
    suspend fun savedPosition(videoId: String): Pair<Long, Long>? =
        context.playbackStore.data.first()[stringPreferencesKey("pos:$videoId")]
            ?.split(':')?.takeIf { it.size == 2 }
            ?.let { (p, t) -> p.toLongOrNull()?.let { pos -> t.toLongOrNull()?.let { pos to it } } }

    suspend fun savePosition(videoId: String, positionMs: Long?) {
        context.playbackStore.edit {
            val key = stringPreferencesKey("pos:$videoId")
            if (positionMs == null) it.remove(key) else it[key] = "$positionMs:${System.currentTimeMillis()}"
        }
    }

    class SettingsEditor(private val prefs: androidx.datastore.preferences.core.MutablePreferences) {
        var planSlug: String
            get() = prefs[PLAN] ?: Settings.DEFAULT_PLAN
            set(v) { prefs[PLAN] = v }
        var anchor: LocalDate
            get() = prefs[ANCHOR]?.let(LocalDate::parse) ?: Settings.DEFAULT_ANCHOR
            set(v) { prefs[ANCHOR] = v.toString() }
        var name: String
            get() = prefs[NAME] ?: Settings.DEFAULT_NAME
            set(v) { prefs[NAME] = v }
        var raceKm: String
            get() = prefs[RACE_KM] ?: Settings.DEFAULT_RACE_KM
            set(v) { prefs[RACE_KM] = v }
        var videoTree: String?
            get() = prefs[VIDEO_TREE]
            set(v) { if (v == null) prefs.remove(VIDEO_TREE) else prefs[VIDEO_TREE] = v }
        var notify: Boolean
            get() = prefs[NOTIFY] ?: true
            set(v) { prefs[NOTIFY] = v }
        var notifyAt: Int
            get() = prefs[NOTIFY_AT] ?: Settings.DEFAULT_NOTIFY_AT
            set(v) { prefs[NOTIFY_AT] = v }
        var askedNotifyPermission: Boolean
            get() = prefs[ASKED_NOTIFY] ?: false
            set(v) { prefs[ASKED_NOTIFY] = v }

        fun linkVideo(id: String, uri: String?) {
            val key = stringPreferencesKey("$LINK_PREFIX$id")
            if (uri == null) prefs.remove(key) else prefs[key] = uri
        }
    }

    private fun Preferences.toSettings() = Settings(
        planSlug = this[PLAN] ?: Settings.DEFAULT_PLAN,
        anchor = this[ANCHOR]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Settings.DEFAULT_ANCHOR,
        name = this[NAME] ?: Settings.DEFAULT_NAME,
        raceKm = this[RACE_KM] ?: Settings.DEFAULT_RACE_KM,
        videoTree = this[VIDEO_TREE],
        videoLinks = asMap().entries
            .filter { it.key.name.startsWith(LINK_PREFIX) }
            .associate { it.key.name.removePrefix(LINK_PREFIX) to it.value.toString() },
        notify = this[NOTIFY] ?: true,
        notifyAt = this[NOTIFY_AT] ?: Settings.DEFAULT_NOTIFY_AT,
        askedNotifyPermission = this[ASKED_NOTIFY] ?: false,
    )

    private companion object {
        val DONE = stringSetPreferencesKey("done")
        val PLAN = stringPreferencesKey("plan")
        val ANCHOR = stringPreferencesKey("anchor")
        val NAME = stringPreferencesKey("name")
        val RACE_KM = stringPreferencesKey("race_km")
        val VIDEO_TREE = stringPreferencesKey("video_tree")
        val NOTIFY = booleanPreferencesKey("notify")
        val NOTIFY_AT = intPreferencesKey("notify_at")
        val ASKED_NOTIFY = booleanPreferencesKey("asked_notify")
        const val LINK_PREFIX = "link:"
    }
}
