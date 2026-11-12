package com.example.trackly.core

import com.example.trackly.core.review.ReviewPrompter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val policy = ReviewPrompter.Policy

    @Test
    fun `asks only after enough reached goals`() {
        assertFalse(policy.shouldAsk(goalsReached = ReviewPrompter.Policy.MIN_GOALS - 1, lastPromptMillis = 0L, nowMillis = 100 * day))
        assertTrue(policy.shouldAsk(goalsReached = ReviewPrompter.Policy.MIN_GOALS, lastPromptMillis = 0L, nowMillis = 100 * day))
    }

    @Test
    fun `waits months between prompts`() {
        val last = 10 * day
        assertFalse(policy.shouldAsk(50, last, last + 30 * day))
        assertTrue(policy.shouldAsk(50, last, last + ReviewPrompter.Policy.MIN_DAYS_BETWEEN_PROMPTS * day))
    }
}
