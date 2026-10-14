package com.example.trackly.presentation.app

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.navigation.AppNavHost
import com.example.trackly.presentation.common.LocalAppContainer
import com.example.trackly.presentation.common.findActivity
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

@Composable
fun TracklyRoot(
    mainViewModel: MainViewModel,
    deepLinkHabitId: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    val container = LocalAppContainer.current
    val loadedSettings by mainViewModel.settings.collectAsStateWithLifecycle()
    val isPremium by container.isPremium.collectAsStateWithLifecycle()
    val start by mainViewModel.startDestination.collectAsStateWithLifecycle()
    val introDone by mainViewModel.brandIntroDone.collectAsStateWithLifecycle()

    val settings = loadedSettings ?: AppSettings()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // A lapsed subscriber keeps a working app: premium accents fall back to the default.
    val accent = if (settings.accent.requiresPremium && !isPremium) AccentTheme.INDIGO else settings.accent

    val activity = LocalContext.current.findActivity() as? ComponentActivity
    DisposableEffect(darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
        )
        onDispose { }
    }

    TracklyTheme(darkTheme = darkTheme, accent = accent) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                val startRoute = start
                if (startRoute != null) {
                    AppNavHost(
                        startDestination = startRoute,
                        deepLinkHabitId = deepLinkHabitId,
                        onDeepLinkConsumed = onDeepLinkConsumed,
                    )
                }
                AnimatedVisibility(
                    visible = !introDone,
                    enter = fadeIn(tween(0)),
                    exit = fadeOut(tween(350)),
                ) {
                    BrandIntro()
                }
            }
        }
    }
}

private val LIGHT_SCRIM = AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)

/** The app mark followed by the name, shown for a moment right after the system splash screen. */
@Composable
private fun BrandIntro() {
    val name = stringResource(R.string.app_name)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppMark(modifier = Modifier.size(132.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** The launcher icon's ring-and-check mark, drawn in code so it matches the icon exactly. */
@Composable
fun AppMark(modifier: Modifier = Modifier) {
    val brush = Brush.linearGradient(listOf(Color(0xFF7C7CF8), Color(0xFF3A2DBF)))
    Canvas(modifier = modifier) {
        val d = size.minDimension
        drawCircle(brush = brush, radius = d / 2f)
        val stroke = d * 0.075f
        val ringSize = d * 0.44f
        val topLeft = Offset((d - ringSize) / 2f, (d - ringSize) / 2f)
        drawArc(
            color = Color.White,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(ringSize, ringSize),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val check = Path().apply {
            moveTo(d * 0.407f, d * 0.509f)
            lineTo(d * 0.472f, d * 0.574f)
            lineTo(d * 0.602f, d * 0.426f)
        }
        drawPath(
            path = check,
            color = Color.White,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
