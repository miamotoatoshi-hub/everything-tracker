package com.example.trackly.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.TracklyApp
import com.example.trackly.domain.model.HabitType
import com.example.trackly.notifications.ReminderNotifier
import java.time.LocalDate

/** Home-screen widget: today's habits with one-tap logging (Premium). */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TracklyApp
        val state = try {
            TodayWidgetData.load(app.container)
        } catch (error: Exception) {
            TodayWidgetState(isPremium = false, habits = emptyList())
        }
        provideContent {
            GlanceTheme {
                WidgetBody(state)
            }
        }
    }

    companion object {
        /** Re-renders every placed widget; a no-op when none is on the home screen. */
        suspend fun refresh(context: Context) {
            try {
                TodayWidget().updateAll(context)
            } catch (error: Exception) {
                // Widgets are best effort.
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** The ✓ / + button of a row. Works while the app is closed. */
class LogHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HABIT_ID] ?: return
        val container = (context.applicationContext as TracklyApp).container
        val repository = container.habitRepository
        val habit = repository.getHabit(habitId) ?: return
        val today = LocalDate.now(container.clock)
        if (habit.type == HabitType.BOOLEAN) {
            val done = repository.getEntry(habitId, today)?.completed == true
            repository.setEntryValue(habitId, today, if (done) 0.0 else 1.0)
        } else {
            repository.addToEntry(habitId, today, habit.stepValue)
        }
        container.reminderNotifier.cancel(habitId)
        TodayWidget.refresh(context)
    }

    companion object {
        val HABIT_ID = ActionParameters.Key<Long>("habit_id")
    }
}

@Composable
private fun WidgetBody(state: TodayWidgetState) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Header(state)
        Spacer(GlanceModifier.height(8.dp))
        when {
            !state.isPremium -> Message(
                text = context.getString(R.string.widget_locked),
                action = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PREMIUM),
            )
            state.habits.isEmpty() -> Message(
                text = context.getString(R.string.widget_empty),
                action = Intent(context, MainActivity::class.java),
            )
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(state.habits, itemId = { it.id }) { habit ->
                    Column {
                        HabitRow(habit)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: TodayWidgetState) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (state.isPremium && state.habits.isNotEmpty()) {
            Text(
                text = context.getString(R.string.widget_progress, state.completed, state.habits.size),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun Message(text: String, action: Intent) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(action)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

@Composable
private fun HabitRow(habit: WidgetHabit) {
    val context = LocalContext.current
    val open = Intent(context, MainActivity::class.java)
        .setAction(ReminderNotifier.ACTION_OPEN_HABIT)
        .setData(Uri.parse("trackly://habit/${habit.id}"))
        .putExtra(ReminderNotifier.EXTRA_HABIT_ID, habit.id)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp).background(ColorProvider(Color(habit.colorArgb).copy(alpha = 0.22f))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = habit.icon, style = TextStyle(fontSize = 16.sp))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(open))) {
            Text(
                text = habit.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            val detail = when {
                habit.isBoolean && habit.completed -> context.getString(R.string.status_done)
                habit.isBoolean -> context.getString(R.string.status_not_done)
                else -> context.getString(R.string.widget_value, formatNumber(habit.value), formatNumber(habit.goal))
            }
            val streak = if (habit.streak > 0) " · 🔥 ${habit.streak}" else ""
            Text(
                text = detail + streak,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        val background = if (habit.completed) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
        val foreground = if (habit.completed) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .cornerRadius(18.dp)
                .background(background)
                .clickable(actionRunCallback<LogHabitAction>(actionParametersOf(LogHabitAction.HABIT_ID to habit.id))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (habit.completed || habit.isBoolean) "✓" else "+",
                style = TextStyle(color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else "%.1f".format(value)
