package com.nwbort.workouts.data

/**
 * A one-line name for a session. Runs are all titled just "Run" in the plan,
 * so lead with the distance instead.
 */
fun Session.shortLabel(): String {
    if (kind == Kind.RUN || kind == Kind.WALK) {
        val km = tags.firstOrNull { it.endsWith(" km") }
        val prefix = if (optional) "Optional ${kind}" else kind.replaceFirstChar { it.uppercase() }
        return if (km != null) "$prefix $km" else title
    }
    return title
}
