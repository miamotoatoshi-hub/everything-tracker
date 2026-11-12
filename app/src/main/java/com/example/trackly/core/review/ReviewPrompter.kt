package com.example.trackly.core.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ktx.launchReview
import com.google.android.play.core.review.ktx.requestReview
import java.time.Clock

/**
 * Asks for a Google Play rating at a happy moment: right after a goal is reached, once the user has
 * reached enough goals, and at most every [MIN_DAYS_BETWEEN_PROMPTS] days. Google Play applies its own
 * quota on top and may show nothing; the app never knows or asks whether a review was left.
 */
class ReviewPrompter(context: Context, private val clock: Clock) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pure decision, shared with the unit tests. */
    object Policy {
        const val MIN_GOALS = 7
        const val MIN_DAYS_BETWEEN_PROMPTS = 120L

        fun shouldAsk(goalsReached: Int, lastPromptMillis: Long, nowMillis: Long): Boolean {
            if (goalsReached < MIN_GOALS) return false
            if (lastPromptMillis <= 0L) return true
            val days = (nowMillis - lastPromptMillis) / (24L * 60 * 60 * 1000)
            return days >= MIN_DAYS_BETWEEN_PROMPTS
        }
    }

    suspend fun onGoalReached(activity: Activity) {
        val goals = prefs.getInt(KEY_GOALS, 0) + 1
        prefs.edit().putInt(KEY_GOALS, goals).apply()
        val now = clock.millis()
        if (!Policy.shouldAsk(goals, prefs.getLong(KEY_LAST_PROMPT, 0L), now)) return
        prefs.edit().putLong(KEY_LAST_PROMPT, now).apply()
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            manager.launchReview(activity, info)
        } catch (error: Exception) {
            // No Play Store, offline, quota reached... a rating prompt must never disturb the user.
            Log.i(TAG, "In-app review not shown", error)
        }
    }

    private companion object {
        const val TAG = "ReviewPrompter"
        const val PREFS = "review_prompt"
        const val KEY_GOALS = "goals_reached"
        const val KEY_LAST_PROMPT = "last_prompt_millis"
    }
}
