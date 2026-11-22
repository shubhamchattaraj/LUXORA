
package com.shubworks.luxora

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

private val Background = Color(0xFF090D12)
private val Panel = Color(0xFF141C25)
private val Border = Color(0xFF293744)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B3)
private val Cyan = Color(0xFF37D5F2)
private val Green = Color(0xFF37D99A)
private val Yellow = Color(0xFFF5D45E)
private val Orange = Color(0xFFFF9D55)
private val Red = Color(0xFFFF657A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            var launchVisible by remember { mutableStateOf(true) }

            Box(Modifier.fillMaxSize()) {
                LuxoraApp()

                if (launchVisible) {
                    LaunchAnimation(
                        onFinished = { launchVisible = false }
                    )
                }
            }
        }
    }
}

private enum class AppPage(val label: String) {
    METER("Meter"),
    LEVELS("Levels"),
    SETTINGS("Settings")
}

private enum class DisplayUnit(val label: String) {
    LUX("Lux"),
    FOOT_CANDLE("Foot-candle")
}

private data class LightLevel(
    val name: String,
    val range: String,
    val description: String,
    val color: Color,
    val minimum: Float,
    val maximum: Float
)

private val lightLevels = listOf(
    LightLevel("Near darkness", "Below 1 lx", "Extremely little ambient light.", Color(0xFF718096), 0f, 1f),
    LightLevel("Very dim", "1–10 lx", "A very dimly lit environment.", Color(0xFF8995B0), 1f, 10f),
    LightLevel("Dim indoor light", "10–50 lx", "Low-level indoor lighting.", Color(0xFF788FD1), 10f, 50f),
    LightLevel("Soft lighting", "50–200 lx", "Relaxed indoor environments and corridors.", Color(0xFF55B8E8), 50f, 200f),
    LightLevel("Moderate lighting", "200–500 lx", "Common indoor lighting for everyday activities.", Cyan, 200f, 500f),
    LightLevel("Well illuminated", "500–1,000 lx", "Brighter indoor work and reading areas.", Green, 500f, 1000f),
    LightLevel("Bright indoor / daylight", "1,000–10,000 lx", "Bright windows and shaded outdoor areas.", Yellow, 1000f, 10000f),
    LightLevel("Bright daylight", "10,000–100,000 lx", "Outdoor daylight, depending on weather.", Orange, 10000f, 100000f),
    LightLevel("Intense daylight", "Above 100,000 lx", "Very bright direct sunlight conditions.", Red, 100000f, Float.MAX_VALUE)
)

@Composable
private fun LuxoraApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val lightSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    var rawLux by remember { mutableFloatStateOf(0f) }
    var hasReading by remember { mutableStateOf(false) }
    var sensorActive by remember { mutableStateOf(false) }
    var measurementEnabled by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(AppPage.METER) }
    var selectedUnit by remember { mutableStateOf(DisplayUnit.LUX) }
    var calibration by remember { mutableFloatStateOf(1f) }
    var chromeVisible by remember { mutableStateOf(true) }

    val readings = remember { mutableStateListOf<Float>() }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(available.y) > 5f) {
                    chromeVisible = false
                }
                return Offset.Zero
            }
        }
    }

    DisposableEffect(lightSensor, measurementEnabled) {
        if (lightSensor == null || !measurementEnabled) {
            sensorActive = false
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val value = event?.values?.firstOrNull() ?: return
                    rawLux = value.coerceAtLeast(0f)
                    hasReading = true
                    readings.add(rawLux)

                    if (readings.size > 60) {
                        readings.removeAt(0)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorActive = sensorManager.registerListener(
                listener,
                lightSensor,
                SensorManager.SENSOR_DELAY_UI
            )

            onDispose {
                sensorManager.unregisterListener(listener)
                sensorActive = false
            }
        }
    }

    val calibratedLux = (rawLux * calibration).coerceAtLeast(0f)

    val displayedReading = when (selectedUnit) {
        DisplayUnit.LUX -> calibratedLux
        DisplayUnit.FOOT_CANDLE -> calibratedLux / 10.764f
    }

    val history = readings.map {
        val value = it * calibration
        when (selectedUnit) {
            DisplayUnit.LUX -> value
            DisplayUnit.FOOT_CANDLE -> value / 10.764f
        }
    }

    val minimum = history.minOrNull() ?: displayedReading
    val maximum = history.maxOrNull() ?: displayedReading
    val average = history.average().toFloat()
        .takeIf { history.isNotEmpty() } ?: displayedReading

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Panel,
            primary = Cyan,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .nestedScroll(scrollConnection)
        ) {
            // PAGE CONTENT:
            // Full-screen. No top or bottom toolbar padding.
            // The floating controls are drawn over this content.
            AnimatedContent(
                targetState = selectedPage,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(chromeVisible) {
                        detectTapGestures(
                            onTap = { chromeVisible = !chromeVisible }
                        )
                    },
                transitionSpec = {
                    (
                            fadeIn(tween(220)) +
                                    slideInVertically(
                                        tween(260),
                                        initialOffsetY = { it / 24 }
                                    )
                            ).togetherWith(
                            fadeOut(tween(150)) +
                                    slideOutVertically(
                                        tween(180),
                                        targetOffsetY = { -it / 30 }
                                    )
                        )
                },
                label = "page_transition"
            ) { page ->
                when (page) {
                    AppPage.METER -> MeterPage(
                        chromeVisible = chromeVisible,
                        reading = displayedReading,
                        unit = selectedUnit,
                        hasReading = hasReading,
                        interpretation = getInterpretation(calibratedLux),
                        minimum = minimum,
                        maximum = maximum,
                        average = average,
                        history = history,
                        sensorAvailable = lightSensor != null,
                        measurementEnabled = measurementEnabled,
                        onToggleMeasurement = {
                            measurementEnabled = !measurementEnabled
                        }
                    )

                    AppPage.LEVELS -> LevelsPage(
                        currentLux = calibratedLux,
                        chromeVisible = chromeVisible
                    )

                    AppPage.SETTINGS -> SettingsPage(
                        chromeVisible = chromeVisible,
                        sensor = lightSensor,
                        sensorAvailable = lightSensor != null,
                        calibration = calibration,
                        onCalibrationChange = { calibration = it },
                        selectedUnit = selectedUnit,
                        onUnitChange = { selectedUnit = it }
                    )
                }
            }

            // Floating header: independent overlay.
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 12.dp),
                enter = fadeIn(tween(220)) + slideInVertically(
                    tween(260),
                    initialOffsetY = { -it }
                ),
                exit = fadeOut(tween(180)) + slideOutVertically(
                    tween(200),
                    targetOffsetY = { -it }
                )
            ) {
                FloatingHeader(sensorActive)
            }

            // Floating pill: no footer container or reserved layout space.
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 22.dp),
                enter = fadeIn(tween(220)) + slideInVertically(
                    tween(260),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(tween(180)) + slideOutVertically(
                    tween(200),
                    targetOffsetY = { it }
                )
            ) {
                FloatingNavigation(
                    selectedPage = selectedPage,
                    onPageSelected = {
                        selectedPage = it
                        chromeVisible = true
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingHeader(sensorActive: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xA8172733),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "LUXORA",
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text(
                    "AMBIENT LIGHT INSTRUMENT",
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp
                )
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (sensorActive) Green.copy(alpha = 0.13f)
                        else Red.copy(alpha = 0.13f)
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (sensorActive) Green else Red,
                            CircleShape
                        )
                )
                Text(
                    if (sensorActive) "LIVE" else "OFFLINE",
                    color = if (sensorActive) Green else Red,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
private fun MeterPage(
    chromeVisible: Boolean,
    reading: Float,
    unit: DisplayUnit,
    hasReading: Boolean,
    interpretation: String,
    minimum: Float,
    maximum: Float,
    average: Float,
    history: List<Float>,
    sensorAvailable: Boolean,
    measurementEnabled: Boolean,
    onToggleMeasurement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Spacer(Modifier.height(if (chromeVisible) 88.dp else 16.dp))

        PanelCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Eyebrow("LIVE MEASUREMENT")
                    Eyebrow("REAL-TIME")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(245.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MeterGauge(
                        lux = if (hasReading) {
                            if (unit == DisplayUnit.LUX) reading else reading * 10.764f
                        } else 0f,
                        hasReading = hasReading,
                        modifier = Modifier.size(235.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (hasReading) formatReading(reading) else "--",
                            color = TextPrimary,
                            fontSize = 45.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp
                        )
                        Text(
                            unit.label.uppercase(),
                            color = Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Text(
                    if (hasReading) interpretation.uppercase() else "WAITING FOR SENSOR",
                    color = if (hasReading) interpretationColor(interpretation) else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onToggleMeasurement,
                    enabled = sensorAvailable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (measurementEnabled) Color(0xFF174B3D) else Color(0xFF16485A),
                        contentColor = if (measurementEnabled) Green else Cyan,
                        disabledContainerColor = Panel,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Text(
                        when {
                            !sensorAvailable -> "SENSOR UNAVAILABLE"
                            measurementEnabled -> "STOP MEASUREMENT"
                            else -> "START MEASUREMENT"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(Modifier.height(15.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatValue("MINIMUM", if (hasReading) formatReading(minimum) else "--", unit)
                    StatValue("MAXIMUM", if (hasReading) formatReading(maximum) else "--", unit)
                    StatValue("AVERAGE", if (hasReading) formatReading(average) else "--", unit)
                }
            }
        }

        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "READING HISTORY",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Recent sensor samples",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "${history.size} SAMPLES",
                        color = Cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                ReadingChart(
                    history,
                    Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Eyebrow("OLDER")
                    Eyebrow("LATEST")
                }
            }
        }

        PanelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("CURRENT ENVIRONMENT")
                    Spacer(Modifier.height(5.dp))
                    Text(
                        interpretation,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        "Based on approximate illuminance ranges.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            interpretationColor(interpretation).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "LX",
                        color = interpretationColor(interpretation),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (!sensorAvailable) {
            PanelCard {
                Text(
                    "No ambient-light sensor was detected on this device. The live meter requires a physical light sensor.",
                    color = Orange,
                    fontSize = 13.sp
                )
            }
        }

        // The page only makes room for the floating pill while it is visible.
        Spacer(Modifier.height(if (chromeVisible) 110.dp else 20.dp))
    }
}

@Composable
private fun MeterGauge(
    lux: Float,
    hasReading: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = (
            log10(lux.coerceAtLeast(0f) + 1f) / log10(100001f)
            ).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        progress,
        tween(450, easing = FastOutSlowInEasing),
        label = "gauge_progress"
    )

    Canvas(modifier) {
        val strokeWidth = 7.dp.toPx()
        val diameter = size.minDimension - strokeWidth * 2
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )

        val segmentCount = 100
        val totalSweep = 270f
        val gap = 1.5f
        val segmentSweep = totalSweep / segmentCount - gap

        for (i in 0 until segmentCount) {
            val fraction = i.toFloat() / segmentCount
            val color = when {
                fraction < 0.20f -> Color(0xFF62718C)
                fraction < 0.40f -> Color(0xFF438DC0)
                fraction < 0.65f -> Cyan
                fraction < 0.82f -> Green
                fraction < 0.93f -> Yellow
                else -> Orange
            }

            val lit = hasReading &&
                    i < (animatedProgress * segmentCount).toInt()

            drawArc(
                color = if (lit) color else Color(0xFF29343F),
                startAngle = 135f + i * (totalSweep / segmentCount),
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )
        }

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f

        listOf(135f, 270f, 405f).forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            drawCircle(
                Color(0xFF64748B),
                2.dp.toPx(),
                Offset(
                    center.x + radius * cos(radians).toFloat(),
                    center.y + radius * sin(radians).toFloat()
                )
            )
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, unit: DisplayUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(unit.label, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun ReadingChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 5.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val chartHeight = bottom - top
        val chartWidth = right - left

        for (i in 0..4) {
            val y = top + chartHeight * i / 4f
            drawLine(
                Color(0xFF2A3642),
                Offset(left, y),
                Offset(right, y),
                1.dp.toPx()
            )
        }

        if (values.size < 2) return@Canvas

        val maxValue = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val points = values.mapIndexed { index, value ->
            Offset(
                left + chartWidth * index / (values.size - 1).toFloat(),
                bottom - (value / maxValue).coerceIn(0f, 1f) * chartHeight
            )
        }

        for (i in 0 until points.lastIndex) {
            drawLine(
                Cyan,
                points[i],
                points[i + 1],
                2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        points.forEachIndexed { index, point ->
            drawCircle(
                if (index == points.lastIndex) Green else Color.White,
                if (index == points.lastIndex) 4.dp.toPx() else 2.5.dp.toPx(),
                point
            )
        }
    }
}

@Composable
private fun LevelsPage(currentLux: Float, chromeVisible: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // This collapses as soon as the floating header disappears.
        Spacer(Modifier.height(if (chromeVisible) 88.dp else 16.dp))

        PageHeading(
            "Light levels",
            "A visual guide to everyday illuminance."
        )

        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Eyebrow("YOUR CURRENT READING")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${currentLux.roundToInt()} lx",
                            color = Cyan,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Eyebrow("LIGHT CONDITION")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            getInterpretation(currentLux),
                            color = interpretationColor(getInterpretation(currentLux)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Divider(color = Border.copy(alpha = 0.8f))

                Eyebrow("ILLUMINANCE SPECTRUM · LOGARITHMIC SCALE")
                IlluminanceScaleChart(currentLux = currentLux)

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    lightLevels.forEach { level ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(level.color)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    level.name,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    level.description,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    level.range,
                                    color = level.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.End
                                )
                                if (currentLux >= level.minimum && currentLux < level.maximum) {
                                    Text(
                                        "CURRENT",
                                        color = Green,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "These ranges are approximate reference values. Actual lighting needs vary with activity, environment, sensor placement, and device hardware.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 3.dp)
        )

        Spacer(Modifier.height(if (chromeVisible) 110.dp else 20.dp))
    }
}

@Composable
private fun IlluminanceScaleChart(currentLux: Float) {
    val boundaries = listOf(0.1f, 1f, 10f, 50f, 200f, 500f, 1_000f, 10_000f, 100_000f, 1_000_000f)
    val tickValues = listOf(1f, 10f, 100f, 1_000f, 10_000f, 100_000f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
    ) {
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val barTop = 20.dp.toPx()
        val barBottom = 53.dp.toPx()
        val barHeight = barBottom - barTop
        val minLog = log10(0.1f)
        val maxLog = log10(1_000_000f)
        fun xFor(value: Float): Float {
            val fraction = ((log10(value.coerceAtLeast(0.1f)) - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)
            return left + fraction * (right - left)
        }

        val colors = lightLevels.map { it.color }
        for (i in 0 until boundaries.lastIndex) {
            val x1 = xFor(boundaries[i])
            val x2 = xFor(boundaries[i + 1])
            drawRect(
                color = colors[i],
                topLeft = Offset(x1 + 1.dp.toPx(), barTop),
                size = androidx.compose.ui.geometry.Size(
                    (x2 - x1 - 2.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                    barHeight
                )
            )
        }

        tickValues.forEach { tick ->
            val x = xFor(tick)
            drawLine(
                color = Color.White.copy(alpha = 0.38f),
                start = Offset(x, barTop - 5.dp.toPx()),
                end = Offset(x, barBottom + 5.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }

        val markerX = xFor(currentLux.coerceIn(0.1f, 1_000_000f))
        drawLine(
            color = Color.White,
            start = Offset(markerX, 7.dp.toPx()),
            end = Offset(markerX, barBottom + 8.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(Color.White, 3.dp.toPx(), Offset(markerX, barTop - 1.dp.toPx()))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("1", "10", "100", "1k", "10k", "100k lx").forEach { label ->
            Text(label, color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsPage(
    chromeVisible: Boolean,
    sensor: Sensor?,
    sensorAvailable: Boolean,
    calibration: Float,
    onCalibrationChange: (Float) -> Unit,
    selectedUnit: DisplayUnit,
    onUnitChange: (DisplayUnit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // No permanent header reservation: this space collapses with the header.
        Spacer(Modifier.height(if (chromeVisible) 88.dp else 16.dp))

        PageHeading(
            "Settings",
            "Configure your instrument."
        )

        PanelCard {
            SettingsSectionTitle(
                "DISPLAY UNIT",
                "Choose how measurements are presented."
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DisplayUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = selectedUnit == unit,
                        onClick = { onUnitChange(unit) },
                        label = {
                            Text(
                                unit.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF123B49),
                            selectedLabelColor = Cyan,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Text(
                "Foot-candles are converted from lux. Lumens cannot be measured directly by a phone's ambient-light sensor.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        PanelCard {
            SettingsSectionTitle(
                "CALIBRATION",
                "Adjust the displayed reading with a correction factor."
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Correction factor", color = TextSecondary, fontSize = 12.sp)
                Text(
                    String.format("%.2fx", calibration),
                    color = Cyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Slider(
                value = calibration,
                onValueChange = onCalibrationChange,
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = Border
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Eyebrow("0.50x")
                Eyebrow("1.00x = DEFAULT")
                Eyebrow("2.00x")
            }

            TextButton(
                onClick = { onCalibrationChange(1f) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "Reset calibration",
                    color = Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        PanelCard {
            SettingsSectionTitle(
                "SENSOR INFORMATION",
                "Hardware details reported by Android."
            )

            if (sensorAvailable && sensor != null) {
                SpecificationRow("Sensor", sensor.name)
                SpecificationRow("Manufacturer", sensor.vendor)
                SpecificationRow("Maximum range", "${sensor.maximumRange} lx")
                SpecificationRow("Resolution", "${sensor.resolution} lx")
                SpecificationRow("Power", "${sensor.power} mA")
                SpecificationRow("Version", sensor.version.toString())
            } else {
                Text(
                    "No ambient-light sensor is available on this device.",
                    color = Orange,
                    fontSize = 12.sp
                )
            }
        }

        PanelCard {
            SettingsSectionTitle(
                "ABOUT LUXORA",
                "A compact ambient-light measurement tool."
            )
            SpecificationRow("Measurement", "Ambient illuminance")
            SpecificationRow("Primary unit", "Lux (lx)")
            SpecificationRow("Conversion", "1 fc = 10.764 lx")
            SpecificationRow("Version", "1.0")
        }

        Spacer(Modifier.height(if (chromeVisible) 110.dp else 20.dp))
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 3.dp, bottom = 3.dp)) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SpecificationRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border)
    )
}

@Composable
private fun FloatingNavigation(
    selectedPage: AppPage,
    onPageSelected: (AppPage) -> Unit
) {
    // Only the capsule itself has a glass surface.
    // There is no full-width bar or footer behind it.
    Surface(
        color = Color(0xB8172733),
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.20f)
        ),
        shadowElevation = 24.dp
    ) {
        Row(
            Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AppPage.entries.forEach { page ->
                val selected = selectedPage == page

                val progress by animateFloatAsState(
                    if (selected) 1f else 0f,
                    tween(260, easing = FastOutSlowInEasing),
                    label = "nav_selection"
                )

                val iconColor = androidx.compose.ui.graphics.lerp(
                    TextSecondary,
                    Cyan,
                    progress
                )

                Column(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            Color(0xFF1C5264).copy(alpha = progress * 0.72f)
                        )
                        .clickable { onPageSelected(page) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NavigationIcon(
                        page,
                        iconColor,
                        Modifier.size(19.dp)
                    )

                    Text(
                        page.label,
                        color = if (selected) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) {
                            FontWeight.ExtraBold
                        } else {
                            FontWeight.Medium
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    page: AppPage,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)

        when (page) {
            AppPage.METER -> {
                drawArc(
                    tint,
                    135f,
                    270f,
                    false,
                    Offset(size.width * 0.12f, size.height * 0.12f),
                    androidx.compose.ui.geometry.Size(
                        size.width * 0.76f,
                        size.height * 0.76f
                    ),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )

                drawLine(
                    tint,
                    center,
                    Offset(size.width * 0.70f, size.height * 0.32f),
                    stroke,
                    cap = StrokeCap.Round
                )

                drawCircle(tint, 2.dp.toPx(), center)
            }

            AppPage.LEVELS -> {
                for (i in 0..2) {
                    val y = size.height * (0.22f + i * 0.28f)
                    drawLine(
                        tint,
                        Offset(size.width * 0.20f, y),
                        Offset(size.width * 0.80f, y),
                        stroke,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        tint,
                        1.8.dp.toPx(),
                        Offset(size.width * 0.28f, y)
                    )
                }
            }

            AppPage.SETTINGS -> {
                drawCircle(
                    tint,
                    size.minDimension * 0.30f,
                    center,
                    style = Stroke(stroke)
                )
                drawCircle(
                    tint,
                    size.minDimension * 0.10f,
                    center
                )

                for (i in 0..7) {
                    val angle = Math.toRadians(i * 45.0)
                    val inner = size.minDimension * 0.34f
                    val outer = size.minDimension * 0.46f

                    drawLine(
                        tint,
                        Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner
                        ),
                        Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer
                        ),
                        stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(
            Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

private fun formatReading(value: Float): String {
    return when {
        value >= 100f -> String.format("%.0f", value)
        value >= 10f -> String.format("%.1f", value)
        else -> String.format("%.2f", value)
    }
}

private fun getInterpretation(lux: Float): String {
    return when {
        lux < 1f -> "Near darkness"
        lux < 10f -> "Very dim"
        lux < 50f -> "Dim indoor light"
        lux < 200f -> "Soft lighting"
        lux < 500f -> "Moderate lighting"
        lux < 1000f -> "Well illuminated"
        lux < 10000f -> "Bright indoor / daylight"
        lux < 100000f -> "Bright daylight"
        else -> "Intense daylight"
    }
}

private fun interpretationColor(interpretation: String): Color {
    return when (interpretation) {
        "Near darkness", "Very dim" -> Color(0xFF8995B0)
        "Dim indoor light" -> Color(0xFF788FD1)
        "Soft lighting" -> Color(0xFF55B8E8)
        "Moderate lighting" -> Cyan
        "Well illuminated" -> Green
        "Bright indoor / daylight" -> Yellow
        "Bright daylight" -> Orange
        else -> Red
    }
}

