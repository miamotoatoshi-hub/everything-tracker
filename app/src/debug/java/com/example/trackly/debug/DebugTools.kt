package com.example.trackly.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.di.AppContainer
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.Reminder
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

/** Debug-build helpers: fake Premium (never in release) and demo data. */
object DebugTools {

    @Composable
    fun SettingsSection(container: AppContainer) {
        val override by container.premiumOverride.flow.collectAsStateWithLifecycle()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Debug (debug builds only)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Force Premium on", modifier = Modifier.weight(1f))
                        Switch(
                            checked = override == true,
                            onCheckedChange = { container.premiumOverride.flow.value = if (it) true else null },
                            modifier = Modifier.testTag("debug_premium_switch"),
                        )
                    }
                    Button(
                        onClick = { container.appScope.launch { seedDemoData(container) } },
                        modifier = Modifier.fillMaxWidth().testTag("debug_seed"),
                    ) { Text("Load demo habits") }
                    OutlinedButton(
                        onClick = { container.appScope.launch { clearAll(container) } },
                        modifier = Modifier.fillMaxWidth().testTag("debug_clear"),
                    ) { Text("Delete all habits") }
                }
            }
        }
    }

    /** Four demo habits with about five weeks of plausible history. */
    suspend fun seedDemoData(container: AppContainer) {
        val repo = container.habitRepository
        val today = container.todayProvider.current()
        val start = today.minusDays(35)
        val random = Random(42)

        data class Demo(val name: String, val icon: String, val color: Int, val type: HabitType, val goal: Double, val unit: HabitUnit, val step: Double)

        // Demo names follow the app language, so store screenshots look native.
        val ru = java.util.Locale.getDefault().language == "ru"
        fun name(en: String, ruName: String) = if (ru) ruName else en
        val demos = listOf(
            Demo(name("Drink Water", "Пить воду"), "💧", HabitColors.FREE[5], HabitType.COUNTER, 8.0, HabitUnit.GLASSES, 1.0),
            Demo(name("Yoga", "Йога"), "🧘", HabitColors.FREE[7], HabitType.DURATION, 30.0, HabitUnit.MINUTES, 5.0),
            Demo(name("Read", "Чтение"), "📚", HabitColors.FREE[2], HabitType.QUANTITY, 20.0, HabitUnit.PAGES, 5.0),
            Demo(name("Walk", "Прогулка"), "🚶", HabitColors.FREE[3], HabitType.DISTANCE, 5.0, HabitUnit.KM, 1.0),
            Demo(name("Meditate", "Медитация"), "🧠", HabitColors.FREE[0], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
        )
        // Today: some habits done, some in progress, some still to do — like a real day.
        val todayValues = listOf(5.0, 30.0, 0.0, 2.0, 1.0)
        demos.forEachIndexed { index, demo ->
            val id = repo.createHabit(
                HabitDraft(
                    name = demo.name,
                    icon = demo.icon,
                    colorArgb = demo.color,
                    type = demo.type,
                    goalValue = demo.goal,
                    unit = demo.unit,
                    stepValue = demo.step,
                    schedule = HabitSchedule.Daily,
                    startDate = start,
                    reminders = if (index == 0) listOf(Reminder(time = LocalTime.of(9, 0))) else emptyList(),
                ),
            )
            // The first habits keep a real streak over the last days, like an engaged user's would.
            val streakDays = listOf(12, 9, 4, 0, 6).getOrElse(index) { 0 }
            var date = start
            while (date.isBefore(today)) {
                if (!date.isBefore(today.minusDays(streakDays.toLong()))) {
                    repo.setEntryValue(id, date, demo.goal)
                    date = date.plusDays(1)
                    continue
                }
                when (random.nextInt(10)) {
                    in 0..5 -> repo.setEntryValue(id, date, demo.goal)
                    in 6..7 -> repo.setEntryValue(id, date, demo.goal * 0.5)
                    else -> Unit
                }
                date = date.plusDays(1)
            }
            todayValues.getOrNull(index)?.takeIf { it > 0.0 }?.let { repo.setEntryValue(id, today, it) }
        }
    }

    suspend fun clearAll(container: AppContainer) {
        val repo = container.habitRepository
        repo.getHabits().forEach { repo.deleteHabit(it.id) }
    }
}
