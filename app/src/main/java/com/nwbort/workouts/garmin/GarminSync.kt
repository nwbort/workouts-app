package com.nwbort.workouts.garmin

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nwbort.workouts.Graph
import com.nwbort.workouts.data.Garmin
import com.nwbort.workouts.data.GarminApi
import com.nwbort.workouts.data.GarminAuthException
import com.nwbort.workouts.data.Repository
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Pulls recent activities from Garmin Connect and ticks off the runs (and
 * walks) they match. Runs when the app comes to the front, every few hours in
 * the background, and on demand from Settings.
 */
class GarminSync(private val context: Context, private val repo: Repository, private val api: GarminApi = GarminApi()) {
    private val mutex = Mutex()

    sealed interface Result {
        data class Synced(val activities: Int, val ticked: Int, val new: Int = 0) : Result
        data class Failed(val message: String, val keyRejected: Boolean = false) : Result
        data object NotConnected : Result
    }

    /** Syncs unless one ran in the last [minAgeMs]; returns null when skipped. */
    suspend fun syncIfStale(minAgeMs: Long = 15 * 60_000L): Result? {
        val g = repo.garmin.first()
        if (!g.connected) return null
        val last = g.lastSync
        if (last != null && System.currentTimeMillis() - last < minAgeMs && g.lastError == null) return null
        return sync()
    }

    suspend fun sync(): Result = mutex.withLock { syncLocked() }

    private suspend fun syncLocked(): Result {
        val key = repo.garmin.first().apiKey ?: return Result.NotConnected
        val snap = repo.currentSnapshot() ?: return Result.NotConnected
        val today = LocalDate.now()
        // The plan so far, but no further back than 90 days: enough to fill
        // in ticks without pulling a whole year every few hours.
        val from = maxOf(snap.schedule.start, today.minusDays(LOOKBACK_DAYS)).let { if (it.isAfter(today)) today else it }
        val known = repo.garmin.first().activities.mapTo(HashSet()) { it.id }
        val result = try {
            val fetched = withContext(Dispatchers.IO) { fetch(key, from, today) }
            val ticked = Garmin.autoTicks(snap, fetched, repo.garminApplied())
            repo.saveGarminSync(from, today, fetched, ticked, matched = Garmin.assign(snap, fetched).keys)
            Result.Synced(fetched.size, ticked.size, new = fetched.count { it.id !in known })
        } catch (e: GarminAuthException) {
            repo.saveGarminToken(null, null)
            val msg = "Garmin turned the key down. It may have expired or been deleted: make a new one."
            repo.saveGarminError(msg, keyRejected = true)
            Result.Failed(msg, keyRejected = true)
        } catch (e: IOException) {
            val msg = "Couldn't reach Garmin: ${e.message ?: e.javaClass.simpleName}"
            repo.saveGarminError(msg, keyRejected = false)
            Result.Failed(msg)
        }
        if (result is Result.Synced && result.ticked > 0) TodayWidget.refresh(context)
        return result
    }

    /** Fetches with a cached token, getting a fresh one when it's near expiry or turned down. */
    private suspend fun fetch(key: String, from: LocalDate, to: LocalDate) = try {
        api.activities(token(key, fresh = false), from, to)
    } catch (e: GarminAuthException) {
        // Tokens can stop working early; one retry with a new one before blaming the key.
        api.activities(token(key, fresh = true), from, to)
    }

    private suspend fun token(key: String, fresh: Boolean): String {
        val now = System.currentTimeMillis() / 1000
        if (!fresh) repo.garminToken()?.let { (t, exp) -> if (exp - now > 3600) return t }
        val t = api.token(key)
        // Garmin's tokens are JWTs; if one ever isn't, keep it for an hour.
        repo.saveGarminToken(t, Garmin.tokenExpiry(t) ?: (now + 2 * 3600))
        return t
    }

    companion object {
        private const val LOOKBACK_DAYS = 90L
        private const val WORK = "garmin-sync"

        /** Background sync every few hours while connected; cancelled when not. */
        fun schedule(context: Context, connected: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!connected) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<GarminWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class GarminWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        val r = Graph.garmin.sync()
        // A network hiccup is worth retrying; a turned-down key isn't until it's replaced.
        return if (r is GarminSync.Result.Failed && !r.keyRejected) Result.retry() else Result.success()
    }
}
