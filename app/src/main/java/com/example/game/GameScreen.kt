package com.example.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HighScore
import kotlin.math.cos
import kotlin.math.sin

// Cosmetics unlock as the best score climbs (requiredScore), giving a progression goal.
// Entries with price > 0 are shop exclusives, bought with the pearl currency instead.
enum class OctopusSkin(val skinName: String, val primaryColor: Color, val accentColor: Color, val requiredScore: Int, val price: Int = 0) {
    CLASSIC("Rosé Classique", Color(0xFFFF7675), Color(0xFFD63031), 0),
    MINT("Menthe Néon", Color(0xFF55E6C1), Color(0xFF1B9CFC), 15),
    GOLD("Éclat d'Or", Color(0xFFFFD700), Color(0xFFFF9F43), 40),
    COSMIC("Abysses Violets", Color(0xFFA29BFE), Color(0xFF6C5CE7), 80),
    CORAL("Récif Corallien", Color(0xFFFF7F50), Color(0xFFC44569), 25),
    GLACIER("Glace Polaire", Color(0xFFC7ECEE), Color(0xFF7ED6DF), 60),
    ABYSS("Ombre des Abysses", Color(0xFF485460), Color(0xFF1E272E), 120),
    ELECTRIC("Bleu Électrique", Color(0xFF00A8FF), Color(0xFF0652DD), 0, price = 150),
    LAVA("Lave Ardente", Color(0xFFFF6348), Color(0xFFB71540), 0, price = 300),
    EMERALD("Émeraude Royale", Color(0xFF00B894), Color(0xFF006266), 0, price = 500),
    BIOLUM("Bioluminescent", Color(0xFF7EFFF5), Color(0xFF18DCFF), 0, price = 650),
    SAKURA("Fleur de Sakura", Color(0xFFFDA7DF), Color(0xFFD980FA), 0, price = 800),
    ROYAL("Pourpre Royal", Color(0xFF833471), Color(0xFF6F1E51), 0, price = 1000)
}

// The pirate patch is special: it's the daily-mission reward, not a score unlock.
enum class OctopusAccessory(val displayName: String, val requiredScore: Int, val missionReward: Boolean = false, val price: Int = 0) {
    NONE("Aucun accessoire ✕", 0),
    CROWN("Couronne Royale 👑", 50),
    SAILOR("Chapeau de Marin ⚓", 10),
    SUNGLASSES("Lunettes de Soleil 😎", 25),
    PIRATE_PATCH("Cache-œil de Pirate 🏴‍☠️", 0, missionReward = true),
    TOP_HAT("Chapeau de Magicien 🎩", 0, price = 250),
    NINJA_BAND("Bandeau de Ninja 🥷", 0, price = 400)
}

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.gameScreenState.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val octopusY by viewModel.octopusY.collectAsStateWithLifecycle()
    val velocityY by viewModel.velocityYFlow.collectAsStateWithLifecycle()
    val obstacles by viewModel.obstacles.collectAsStateWithLifecycle()
    val pearls by viewModel.pearls.collectAsStateWithLifecycle()
    val powerUps by viewModel.powerUps.collectAsStateWithLifecycle()
    val shieldCharges by viewModel.shieldCharges.collectAsStateWithLifecycle()
    val magnetTime by viewModel.magnetTime.collectAsStateWithLifecycle()
    val slowMoTime by viewModel.slowMoTime.collectAsStateWithLifecycle()
    val combo by viewModel.combo.collectAsStateWithLifecycle()
    val invuln by viewModel.invuln.collectAsStateWithLifecycle()
    val bubbles by viewModel.bubbles.collectAsStateWithLifecycle()
    val particles by viewModel.particles.collectAsStateWithLifecycle()
    val waveTime by viewModel.waveTime.collectAsStateWithLifecycle()
    val topScores by viewModel.topScores.collectAsStateWithLifecycle()
    val highestScore by viewModel.highestScore.collectAsStateWithLifecycle()
    val playerName by viewModel.playerName.collectAsStateWithLifecycle()
    val highScoreSaved by viewModel.highScoreSaved.collectAsStateWithLifecycle()
    val gameMode by viewModel.gameMode.collectAsStateWithLifecycle()
    val currentLevel by viewModel.currentLevel.collectAsStateWithLifecycle()
    val maxUnlockedLevel by viewModel.maxUnlockedLevel.collectAsStateWithLifecycle()
    val levelCompleted by viewModel.levelCompleted.collectAsStateWithLifecycle()
    val newRecord by viewModel.newRecord.collectAsStateWithLifecycle()
    val activeMissions by viewModel.activeMissions.collectAsStateWithLifecycle()
    val missionsJustCompleted by viewModel.missionsJustCompleted.collectAsStateWithLifecycle()
    val missionsCompletedCount by viewModel.missionsCompletedCount.collectAsStateWithLifecycle()
    val pearlWallet by viewModel.pearlWallet.collectAsStateWithLifecycle()
    val purchasedCosmetics by viewModel.purchasedCosmetics.collectAsStateWithLifecycle()
    val runPearlsEarned by viewModel.runPearlsEarned.collectAsStateWithLifecycle()
    val reviveAvailable by viewModel.reviveAvailable.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.unlockedAchievements.collectAsStateWithLifecycle()
    val newAchievements by viewModel.newAchievements.collectAsStateWithLifecycle()

    val interactionSource = remember { MutableInteractionSource() }

    // The ocean shifts through depth biomes as the score climbs; colors crossfade smoothly.
    val biome = remember(score) { biomeFor(score) }
    val bgTop by animateColorAsState(biome.top, tween(1800), label = "bgTop")
    val bgMid by animateColorAsState(biome.mid, tween(1800), label = "bgMid")
    val bgBottom by animateColorAsState(biome.bottom, tween(1800), label = "bgBottom")

    var menuTab by remember { mutableStateOf("home") }
    var musicVolume by remember { mutableStateOf(SoundManager.musicVolume) }
    var sfxVolume by remember { mutableStateOf(SoundManager.sfxVolume) }
    val selectedSkin by viewModel.selectedSkin.collectAsStateWithLifecycle()
    val selectedAccessory by viewModel.selectedAccessory.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    val updateState by updateManager.updateState.collectAsStateWithLifecycle()

    // Popup shown once per discovered release; "Plus tard" hides it until the next one.
    var dismissedUpdateTag by remember { mutableStateOf<String?>(null) }

    // Seamlessly map sliding volume inputs onto physical Tone Synthesizer properties in real time
    LaunchedEffect(musicVolume) {
        SoundManager.musicVolume = musicVolume
    }
    LaunchedEffect(sfxVolume) {
        SoundManager.sfxVolume = sfxVolume
    }

    // Watch the GitHub repo: check at launch, then re-check periodically in background
    LaunchedEffect(Unit) {
        updateManager.watchForUpdates()
    }

    // Haptic feedback: a short buzz when the run ends on a crash
    LaunchedEffect(state) {
        if (state == GameScreenState.GAME_OVER && !levelCompleted) {
            vibrateCrash(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(bgTop, bgMid, bgBottom)
                )
            )
    ) {
        // --- 1. FULLSCREEN CANVAS GAMEPLAY ENGINE ---
        // Always drawn underneath overlays to show background life even during menus
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_canvas")
                .clickable(
                    interactionSource = interactionSource,
                    indication = null // pure click overlay with zero touch delays
                ) {
                    if (state == GameScreenState.PLAYING) {
                        viewModel.swim()
                    }
                }
        ) {
            // scale 1000x1000 virtual coordinates to screen dimensions
            val virtualWidth = 1000f
            val virtualHeight = 1000f
            val scaleX = size.width / virtualWidth
            val scaleY = size.height / virtualHeight

            scale(scaleX, scaleY, pivot = Offset.Zero) {
                // A. Background light shafts / sun rays coming from top right
                for (i in 0..3) {
                    val angleOffset = i * 25f
                    val path = Path().apply {
                        moveTo(750f + angleOffset, -10f)
                        lineTo(900f + angleOffset, -10f)
                        lineTo(400f + angleOffset, 1020f)
                        lineTo(250f + angleOffset, 1020f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x22FFFFFF),
                                Color(0x00FFFFFF)
                            )
                        )
                    )
                }

                // B. Ambient Bubbles
                bubbles.forEach { bubble ->
                    val bubbleAlpha = (bubble.life / bubble.maxLife).coerceIn(0f, 1f)
                    val baseColor = Color(0xAAFFFFFF).copy(alpha = bubbleAlpha * 0.45f)
                    
                    // Bubble outer ring
                    drawCircle(
                        color = baseColor,
                        radius = bubble.radius,
                        center = Offset(bubble.x, bubble.y),
                        style = Stroke(width = 1.3f)
                    )
                    // Highlight glare refraction inside the bubble
                    drawCircle(
                        color = Color.White.copy(alpha = bubbleAlpha * 0.6f),
                        radius = (bubble.radius * 0.3f).coerceAtLeast(1f),
                        center = Offset(bubble.x - bubble.radius * 0.35f, bubble.y - bubble.radius * 0.35f)
                    )
                }

                // B2. Distant fish silhouettes drifting across the background.
                // Integer speed factors keep motion continuous when waveTime wraps at 2π.
                val twoPi = (Math.PI * 2).toFloat()
                for (i in 0 until 5) {
                    val k = i + 1
                    val progress = ((waveTime * k) % twoPi) / twoPi
                    val fx = 1150f - progress * 1400f
                    val fy = 110f + i * 165f + sin(waveTime * 2f + i * 1.7f).toFloat() * 18f
                    val fishScale = 0.7f + (i % 3) * 0.35f
                    val silhouette = Color(0xFF0A3D62).copy(alpha = 0.4f)
                    // Body
                    drawOval(
                        color = silhouette,
                        topLeft = Offset(fx - 22f * fishScale, fy - 9f * fishScale),
                        size = Size(44f * fishScale, 18f * fishScale)
                    )
                    // Tail fin (fish swim right-to-left, tail trails on the right)
                    val tail = Path().apply {
                        moveTo(fx + 18f * fishScale, fy)
                        lineTo(fx + 34f * fishScale, fy - 10f * fishScale)
                        lineTo(fx + 34f * fishScale, fy + 10f * fishScale)
                        close()
                    }
                    drawPath(tail, silhouette)
                }

                // B3. Seabed decoration: rocks, starfish and swaying fan corals
                listOf(
                    Triple(120f, 985f, 55f),
                    Triple(420f, 995f, 70f),
                    Triple(700f, 990f, 48f),
                    Triple(920f, 998f, 62f)
                ).forEach { (rx, ry, rr) ->
                    drawCircle(Color(0xFF14506E).copy(alpha = 0.8f), radius = rr, center = Offset(rx, ry + rr * 0.55f))
                    drawCircle(Color(0xFF1B6A8F).copy(alpha = 0.5f), radius = rr * 0.55f, center = Offset(rx - rr * 0.3f, ry + rr * 0.35f))
                }

                // Pink fan corals waving with the tide
                for (i in 0 until 3) {
                    val baseX = 270f + i * 280f
                    val sway = sin(waveTime * 1.5f + i).toFloat() * 5f
                    for (b in -2..2) {
                        drawLine(
                            color = Color(0xFFFF7675).copy(alpha = 0.45f),
                            start = Offset(baseX, 1008f),
                            end = Offset(baseX + b * 17f + sway, 945f + kotlin.math.abs(b) * 13f),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Starfish resting on the seabed
                listOf(
                    Offset(185f, 962f) to Color(0xFFFF9F43),
                    Offset(765f, 968f) to Color(0xFFFF6B6B)
                ).forEach { (c, starColor) ->
                    val deg2rad = (Math.PI / 180f).toFloat()
                    val star = Path().apply {
                        for (b in 0 until 5) {
                            val ang = (b * 72f - 90f) * deg2rad
                            val tipX = c.x + cos(ang) * 16f
                            val tipY = c.y + sin(ang) * 16f
                            val innerAng = ang + 36f * deg2rad
                            val inX = c.x + cos(innerAng) * 7f
                            val inY = c.y + sin(innerAng) * 7f
                            if (b == 0) moveTo(tipX, tipY) else lineTo(tipX, tipY)
                            lineTo(inX, inY)
                        }
                        close()
                    }
                    drawPath(star, starColor.copy(alpha = 0.85f))
                    drawCircle(Color.White.copy(alpha = 0.3f), radius = 3f, center = c)
                }

                // C. Procedural Waving Seaweed at bottom
                for (i in 0..11) {
                    val baseX = i * 92f + 18f
                    val heightCoeff = if (i % 2 == 0) 140f else 220f
                    val seaweedPath = Path().apply {
                        moveTo(baseX, 1010f)
                        // sine wave offsets aligned with the tick timer
                        val waveOffset1 = sin(waveTime + i).toFloat() * 20f
                        val waveOffset2 = sin(waveTime * 1.3f + i * 1.5f).toFloat() * 28f
                        
                        cubicTo(
                            baseX - 12f + waveOffset1 * 0.4f, 1000f - heightCoeff * 0.3f,
                            baseX + 12f + waveOffset2 * 0.7f, 1000f - heightCoeff * 0.65f,
                            baseX + waveOffset2, 1000f - heightCoeff
                        )
                    }
                    
                    // alternate reef colors
                    val seaweedColor = when (i % 3) {
                        0 -> Color(0xFF1B9CFC) // vibrant cyan blue
                        1 -> Color(0xFF55E6C1) // neon mint green
                        else -> Color(0xFF26ae60) // warm aquatic green
                    }
                    
                    drawPath(
                        path = seaweedPath,
                        color = seaweedColor.copy(alpha = 0.55f),
                        style = Stroke(width = 18f, cap = StrokeCap.Round)
                    )
                }

                // D. Obstacle Coral Columns
                obstacles.forEach { obstacle ->
                    // Color picked from the SPAWN position (initialGapY): bobbing corals
                    // move gapY every frame, which used to make them flicker orange/teal.
                    val isOrange = (obstacle.initialGapY.toInt() % 2 == 0)
                    
                    val coralColor1 = if (isOrange) Color(0xFFFF6B6B) else Color(0xFF00ADB5)
                    val coralColor2 = if (isOrange) Color(0xFFEE5253) else Color(0xFF00565B)
                    
                    val gradient = Brush.horizontalGradient(
                        colors = listOf(coralColor1, coralColor2),
                        startX = obstacle.x,
                        endX = obstacle.x + obstacle.width
                    )

                    // TOP COLUMNS (from ceiling down)
                    val topHeight = obstacle.gapY - obstacle.gapHeight / 2f
                    // Pillar body
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(obstacle.x, -20f),
                        size = Size(obstacle.width, topHeight + 20f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    // Rounded opening ridge (cap)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        topLeft = Offset(obstacle.x - 6f, topHeight - 35f),
                        size = Size(obstacle.width + 12f, 35f),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(obstacle.x - 6f, topHeight - 35f),
                        size = Size(obstacle.width + 12f, 35f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3.5f)
                    )

                    // BOTTOM COLUMNS (from floor up)
                    val bottomTopY = obstacle.gapY + obstacle.gapHeight / 2f
                    val bottomHeight = 1020f - bottomTopY
                    // Pillar body
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(obstacle.x, bottomTopY),
                        size = Size(obstacle.width, bottomHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    // Rounded opening ridge (cap)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        topLeft = Offset(obstacle.x - 6f, bottomTopY),
                        size = Size(obstacle.width + 12f, 35f),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(obstacle.x - 6f, bottomTopY),
                        size = Size(obstacle.width + 12f, 35f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3.5f)
                    )
                }

                // E. Collectible Golden Pearls
                pearls.forEach { pearl ->
                    val floatWave = sin(waveTime * 8f + pearl.x * 0.05f).toFloat() * 6f
                    val center = Offset(pearl.x, pearl.y + floatWave)

                    // Outer magical gold glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF7C2), Color(0x00FFD700)),
                            center = center,
                            radius = pearl.radius * 2.8f
                        ),
                        radius = pearl.radius * 2.8f,
                        center = center
                    )
                    
                    // Pearls core body
                    drawCircle(
                        color = Color(0xFFFFD700),
                        radius = pearl.radius,
                        center = center
                    )

                    // Glare reflection gloss
                    drawCircle(
                        color = Color.White,
                        radius = pearl.radius * 0.35f,
                        center = Offset(center.x - pearl.radius * 0.3f, center.y - pearl.radius * 0.3f)
                    )
                }

                // E2. Collectible Power-ups (shield / magnet / slow-mo)
                powerUps.forEach { pu ->
                    val floatWave = sin(waveTime * 6f + pu.x * 0.05f).toFloat() * 5f
                    val center = Offset(pu.x, pu.y + floatWave)
                    val (c1, c2) = when (pu.type) {
                        PowerUpType.SHIELD -> Color(0xFF00E5FF) to Color(0xFF1B9CFC)
                        PowerUpType.MAGNET -> Color(0xFFFF6B6B) to Color(0xFFD63031)
                        PowerUpType.SLOWMO -> Color(0xFFA29BFE) to Color(0xFF6C5CE7)
                    }

                    // Outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(c1.copy(alpha = 0.5f), Color.Transparent),
                            center = center,
                            radius = pu.radius * 2.2f
                        ),
                        radius = pu.radius * 2.2f,
                        center = center
                    )
                    // Core orb
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(c1, c2),
                            start = Offset(center.x - pu.radius, center.y - pu.radius),
                            end = Offset(center.x + pu.radius, center.y + pu.radius)
                        ),
                        radius = pu.radius,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = pu.radius,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )

                    // White glyph identifying the power-up
                    val white = Color.White
                    when (pu.type) {
                        PowerUpType.SHIELD -> {
                            val sp = Path().apply {
                                moveTo(center.x, center.y - 11f)
                                lineTo(center.x + 9f, center.y - 6f)
                                lineTo(center.x + 9f, center.y + 3f)
                                quadraticTo(center.x, center.y + 13f, center.x, center.y + 13f)
                                quadraticTo(center.x, center.y + 13f, center.x - 9f, center.y + 3f)
                                lineTo(center.x - 9f, center.y - 6f)
                                close()
                            }
                            drawPath(sp, white, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                        }
                        PowerUpType.MAGNET -> {
                            // Horseshoe magnet: two prongs joined by an arc, with red tips
                            drawArc(
                                color = white,
                                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                                topLeft = Offset(center.x - 9f, center.y - 10f),
                                size = Size(18f, 18f),
                                style = Stroke(width = 4f)
                            )
                            drawLine(white, Offset(center.x - 9f, center.y - 1f), Offset(center.x - 9f, center.y + 9f), strokeWidth = 4f)
                            drawLine(white, Offset(center.x + 9f, center.y - 1f), Offset(center.x + 9f, center.y + 9f), strokeWidth = 4f)
                            drawLine(Color(0xFFFF1744), Offset(center.x - 9f, center.y + 6f), Offset(center.x - 9f, center.y + 9f), strokeWidth = 4f)
                            drawLine(Color(0xFFFF1744), Offset(center.x + 9f, center.y + 6f), Offset(center.x + 9f, center.y + 9f), strokeWidth = 4f)
                        }
                        PowerUpType.SLOWMO -> {
                            // Clock face with two hands
                            drawCircle(white, radius = 9f, center = center, style = Stroke(width = 2.5f))
                            drawLine(white, center, Offset(center.x, center.y - 6f), strokeWidth = 2.5f)
                            drawLine(white, center, Offset(center.x + 5f, center.y), strokeWidth = 2.5f)
                        }
                    }
                }

                // F. Active Game Particles (explosions on score/impact)
                particles.forEach { particle ->
                    drawCircle(
                        color = particle.color.copy(alpha = particle.life),
                        radius = (5f + 10f * particle.life),
                        center = Offset(particle.x, particle.y)
                    )
                }

                // G. THE MAIN OCTOPUS PLAYER (Unharmful menu render or active player)
                val isBlinking = ((waveTime * 0.7f) % 2.5f) < 0.13f
                val octoX = 220f
                val octoY = if (state == GameScreenState.MENU) 480f + sin(waveTime * 3.5f).toFloat() * 25f else octopusY

                // Render Octopus tentacles
                for (t in 0 until 6) {
                    val frac = t / 5f
                    val rawAngle = -0.45f + frac * 0.9f
                    // Drag tentacles backward depending on swimming velocities
                    val speedDrag = (velocityY * 0.0011f).coerceIn(-0.55f, 0.55f)
                    val finalAngle = rawAngle + speedDrag
                    
                    val waveMult = sin(waveTime * 11f - t * 1.2f).toFloat() * 11f
                    val tLen = 68f
                    
                    val tStart = Offset(octoX, octoY + 14f)
                    val controlX = octoX + sin(finalAngle).toFloat() * (tLen * 0.45f) + waveMult * 0.35f
                    val controlY = octoY + 14f + cos(finalAngle).toFloat() * (tLen * 0.45f)
                    
                    val endX = octoX + sin(finalAngle).toFloat() * tLen + waveMult
                    val endY = octoY + 14f + cos(finalAngle).toFloat() * tLen

                    val tPath = Path().apply {
                        moveTo(tStart.x, tStart.y)
                        quadraticTo(controlX, controlY, endX, endY)
                    }

                    // Body matching selected skin colors
                    drawPath(
                        path = tPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(selectedSkin.primaryColor, selectedSkin.accentColor),
                            startY = octoY,
                            endY = octoY + 90f
                        ),
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )
                    // Interactive suction cups (little pink spots on curves)
                    drawCircle(Color(0xFFFF8A80), radius = 3.5f, center = Offset(endX, endY))
                    drawCircle(Color(0xFFFF8A80), radius = 4f, center = Offset(controlX, controlY))
                }

                // Draw Octopus Head/Body
                val headGradient = Brush.radialGradient(
                    colors = listOf(selectedSkin.primaryColor, selectedSkin.accentColor),
                    center = Offset(octoX - 8f, octoY - 8f),
                    radius = 35f
                )
                // circular bulbous head
                drawCircle(
                    brush = headGradient,
                    radius = 30f,
                    center = Offset(octoX, octoY)
                )

                // Protective shimmer while invulnerable (just after a shield save)
                if (invuln) {
                    val shieldPulse = (sin(waveTime * 18f).toFloat() * 0.5f + 0.5f)
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.25f + 0.4f * shieldPulse),
                        radius = 42f,
                        center = Offset(octoX, octoY),
                        style = Stroke(width = 3.5f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                        radius = 42f,
                        center = Offset(octoX, octoY)
                    )
                }

                // --- 1. EXPANSIVE AQUATIC ACCESSORIES (CROWN / SAILOR HAT ON THE HEAD) ---
                if (selectedAccessory == OctopusAccessory.CROWN) {
                    val crownPath = Path().apply {
                        moveTo(octoX - 16f, octoY - 26f)
                        lineTo(octoX - 22f, octoY - 44f)
                        lineTo(octoX - 8f, octoY - 34f)
                        lineTo(octoX, octoY - 50f)
                        lineTo(octoX + 8f, octoY - 34f)
                        lineTo(octoX + 22f, octoY - 44f)
                        lineTo(octoX + 16f, octoY - 26f)
                        close()
                    }
                    drawPath(crownPath, Color(0xFFFFD700)) // Shiny Gold Base
                    drawPath(crownPath, Color(0xFFD35400), style = Stroke(width = 2f))
                    // Gemstones on tips
                    drawCircle(Color(0xFFE74C3C), radius = 3.5f, center = Offset(octoX, octoY - 50f))
                    drawCircle(Color(0xFF3498DB), radius = 3f, center = Offset(octoX - 22f, octoY - 44f))
                    drawCircle(Color(0xFF2ECC71), radius = 3f, center = Offset(octoX + 22f, octoY - 44f))
                } else if (selectedAccessory == OctopusAccessory.SAILOR) {
                    // Marine officer cap
                    val capPath = Path().apply {
                        moveTo(octoX - 18f, octoY - 26f)
                        lineTo(octoX - 22f, octoY - 38f)
                        quadraticTo(octoX, octoY - 42f, octoX + 22f, octoY - 38f)
                        lineTo(octoX + 18f, octoY - 26f)
                        close()
                    }
                    drawPath(capPath, Color.White)
                    drawPath(capPath, Color(0xFF2C3E50), style = Stroke(width = 1.5f))
                    // Navy blue stripe line
                    val bandPath = Path().apply {
                        moveTo(octoX - 18f, octoY - 26f)
                        lineTo(octoX - 19.5f, octoY - 29.5f)
                        quadraticTo(octoX, octoY - 33f, octoX + 19.5f, octoY - 29.5f)
                        lineTo(octoX + 18f, octoY - 26f)
                        close()
                    }
                    drawPath(bandPath, Color(0xFF1B9CFC))
                    // Red pompom at peak
                    drawCircle(Color(0xFFD63031), radius = 3.5f, center = Offset(octoX, octoY - 42f))
                } else if (selectedAccessory == OctopusAccessory.TOP_HAT) {
                    // Magician's top hat: wide brim, tall crown and a purple ribbon
                    drawRoundRect(
                        color = Color(0xFF1E272E),
                        topLeft = Offset(octoX - 24f, octoY - 32f),
                        size = Size(48f, 6f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E272E),
                        topLeft = Offset(octoX - 15f, octoY - 60f),
                        size = Size(30f, 30f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    drawRect(
                        color = Color(0xFF8E44AD),
                        topLeft = Offset(octoX - 15f, octoY - 38f),
                        size = Size(30f, 7f)
                    )
                } else if (selectedAccessory == OctopusAccessory.NINJA_BAND) {
                    // Ninja headband with a metal plate and ribbons trailing in the current
                    drawRect(
                        color = Color(0xFF192A56),
                        topLeft = Offset(octoX - 29f, octoY - 23f),
                        size = Size(58f, 9f)
                    )
                    drawRoundRect(
                        color = Color(0xFF95A5A6),
                        topLeft = Offset(octoX - 8f, octoY - 24f),
                        size = Size(16f, 11f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                    val ribbonSway = sin(waveTime * 6f).toFloat() * 6f
                    drawLine(
                        color = Color(0xFF192A56),
                        start = Offset(octoX + 27f, octoY - 18f),
                        end = Offset(octoX + 47f, octoY - 10f + ribbonSway),
                        strokeWidth = 6f, cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF192A56),
                        start = Offset(octoX + 27f, octoY - 18f),
                        end = Offset(octoX + 45f, octoY - 1f - ribbonSway),
                        strokeWidth = 6f, cap = StrokeCap.Round
                    )
                }

                // Render cartoon expressively reacting glowing Eyes
                val eyeLeft = Offset(octoX - 11f, octoY - 6f)
                val eyeRight = Offset(octoX + 11f, octoY - 6f)
                val eyeRadius = 7.5f

                if (state == GameScreenState.GAME_OVER) {
                    // Dead or Dizzy Cross eyes (X X) representing collision crash
                    drawLine(Color.White, start = Offset(eyeLeft.x - 4f, eyeLeft.y - 4f), end = Offset(eyeLeft.x + 4f, eyeLeft.y + 4f), strokeWidth = 2.5f)
                    drawLine(Color.White, start = Offset(eyeLeft.x + 4f, eyeLeft.y - 4f), end = Offset(eyeLeft.x - 4f, eyeLeft.y + 4f), strokeWidth = 2.5f)
                    
                    drawLine(Color.White, start = Offset(eyeRight.x - 4f, eyeRight.y - 4f), end = Offset(eyeRight.x + 4f, eyeRight.y + 4f), strokeWidth = 2.5f)
                    drawLine(Color.White, start = Offset(eyeRight.x + 4f, eyeRight.y - 4f), end = Offset(eyeRight.x - 4f, eyeRight.y + 4f), strokeWidth = 2.5f)
                } else if (isBlinking) {
                    // Draw eye shut curves
                    drawArc(
                        color = Color.White,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(eyeLeft.x - eyeRadius, eyeLeft.y - eyeRadius),
                        size = Size(eyeRadius * 2, eyeRadius * 2),
                        style = Stroke(width = 2.5f)
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(eyeRight.x - eyeRadius, eyeRight.y - eyeRadius),
                        size = Size(eyeRadius * 2, eyeRadius * 2),
                        style = Stroke(width = 2.5f)
                    )
                } else {
                    // Left pupil offsets to mimic weight / look directions
                    val pupilDirY = (velocityY * 0.005f).coerceIn(-3.5f, 3.5f)
                    val pupilDirX = if (state == GameScreenState.PLAYING) 1.5f else 0f

                    // Left Eye background (Draw only if no eyepatch covering it)
                    if (selectedAccessory != OctopusAccessory.PIRATE_PATCH) {
                        drawCircle(color = Color.White, radius = eyeRadius, center = eyeLeft)
                        // Left Pupil
                        drawCircle(
                            color = Color(0xFF2C3E50),
                            radius = 4.5f,
                            center = Offset(eyeLeft.x + pupilDirX, eyeLeft.y + pupilDirY)
                        )
                        // Kawaii anime sparkles: Diagonal highlight glare circles inside pupil
                        drawCircle(
                            color = Color.White,
                            radius = 1.8f,
                            center = Offset(eyeLeft.x + pupilDirX - 1.5f, eyeLeft.y + pupilDirY - 1.5f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 0.8f,
                            center = Offset(eyeLeft.x + pupilDirX + 1.5f, eyeLeft.y + pupilDirY + 1.5f)
                        )
                    }

                    // Right Eye background
                    drawCircle(color = Color.White, radius = eyeRadius, center = eyeRight)
                    // Right Pupil
                    drawCircle(
                        color = Color(0xFF2C3E50),
                        radius = 4.5f,
                        center = Offset(eyeRight.x + pupilDirX, eyeRight.y + pupilDirY)
                    )
                    // Kawaii anime sparkles
                    drawCircle(
                        color = Color.White,
                        radius = 1.8f,
                        center = Offset(eyeRight.x + pupilDirX - 1.5f, eyeRight.y + pupilDirY - 1.5f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 0.8f,
                        center = Offset(eyeRight.x + pupilDirX + 1.5f, eyeRight.y + pupilDirY + 1.5f)
                    )
                }

                // --- 2. FACIAL WEARABLE ACCESSORIES (SUNGLASSES / PIRATE EYE PATCH) ---
                if (selectedAccessory == OctopusAccessory.SUNGLASSES) {
                    val shadesColor = Color(0xFF2C3E50)
                    // Draw outer round sunglasses rims
                    drawCircle(color = shadesColor, radius = 9.5f, center = Offset(octoX - 11f, octoY - 6f))
                    drawCircle(color = Color.White, radius = 9.5f, center = Offset(octoX - 11f, octoY - 6f), style = Stroke(width = 1.5f))
                    drawCircle(color = shadesColor, radius = 9.5f, center = Offset(octoX + 11f, octoY - 6f))
                    drawCircle(color = Color.White, radius = 9.5f, center = Offset(octoX + 11f, octoY - 6f), style = Stroke(width = 1.5f))
                    // Sunglasses center connection bridge
                    drawLine(
                        color = shadesColor,
                        start = Offset(octoX - 4f, octoY - 7f),
                        end = Offset(octoX + 4f, octoY - 7f),
                        strokeWidth = 3f
                    )
                    // Lateral frames
                    drawLine(color = shadesColor, start = Offset(octoX - 22f, octoY - 8f), end = Offset(octoX - 16f, octoY - 7f), strokeWidth = 2f)
                    drawLine(color = shadesColor, start = Offset(octoX + 16f, octoY - 7f), end = Offset(octoX + 22f, octoY - 8f), strokeWidth = 2f)
                    // Cool reflection glares on glass lenses
                    drawLine(Color.White.copy(alpha = 0.6f), start = Offset(octoX - 15f, octoY - 10f), end = Offset(octoX - 8f, octoY - 3f), strokeWidth = 1.5f)
                    drawLine(Color.White.copy(alpha = 0.6f), start = Offset(octoX + 7f, octoY - 10f), end = Offset(octoX + 14f, octoY - 3f), strokeWidth = 1.5f)
                } else if (selectedAccessory == OctopusAccessory.PIRATE_PATCH) {
                    // Dark pirate eye patch over left eye
                    drawCircle(color = Color(0xFF2C3E50), radius = 10f, center = Offset(octoX - 11f, octoY - 6f))
                    // Diagonally slanting strap
                    drawLine(
                        color = Color(0xFF2C3E50),
                        start = Offset(octoX - 25f, octoY - 15f),
                        end = Offset(octoX + 5f, octoY + 2f),
                        strokeWidth = 2.5f
                    )
                }

                // Soft blush cheeks (Only draw if eyes aren't blocked by big sunglasses)
                if (selectedAccessory != OctopusAccessory.SUNGLASSES) {
                    drawCircle(Color(0x77FF1744), radius = 4f, center = Offset(octoX - 22f, octoY + 4f))
                    drawCircle(Color(0x77FF1744), radius = 4f, center = Offset(octoX + 22f, octoY + 4f))
                    // Tiny diagonal anime blush lines on cheeks for extreme cuteness
                    drawLine(Color.White.copy(alpha = 0.45f), start = Offset(octoX - 24f, octoY + 5f), end = Offset(octoX - 20f, octoY + 2f), strokeWidth = 1f)
                    drawLine(Color.White.copy(alpha = 0.45f), start = Offset(octoX + 20f, octoY + 5f), end = Offset(octoX + 24f, octoY + 2f), strokeWidth = 1f)
                }

                // Dynamic, highly adorable animated mouth
                val mouthCenter = Offset(octoX, octoY + 6f)
                if (state == GameScreenState.GAME_OVER) {
                    // Sad wavy mouth during game over state
                    val sadPath = Path().apply {
                        moveTo(mouthCenter.x - 5f, mouthCenter.y + 4f)
                        quadraticTo(mouthCenter.x, mouthCenter.y, mouthCenter.x + 5f, mouthCenter.y + 4f)
                    }
                    drawPath(sadPath, Color(0xFFD63031), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                } else {
                    val speedY = velocityY
                    if (speedY < -120f) {
                        // "O" mouth for swimming efforts
                        drawCircle(color = Color(0xFFD63031), radius = 3.2f, center = mouthCenter)
                        drawCircle(color = Color(0xFFFF7675), radius = 1.3f, center = Offset(mouthCenter.x, mouthCenter.y + 1f))
                    } else {
                        // Double-curve ":3" sweet happy smile
                        val happyPath = Path().apply {
                            moveTo(mouthCenter.x - 4f, mouthCenter.y + 1f)
                            quadraticTo(mouthCenter.x - 2f, mouthCenter.y + 4.2f, mouthCenter.x, mouthCenter.y + 1.5f)
                            quadraticTo(mouthCenter.x + 2f, mouthCenter.y + 4.2f, mouthCenter.x + 4f, mouthCenter.y + 1f)
                        }
                        drawPath(happyPath, Color(0xFF2C3E50), style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }
                }

                // H. ABYSSAL DARKNESS — deep biomes close in around the octopus,
                // leaving only a halo of light near the player (score >= 60).
                if (state == GameScreenState.PLAYING && score >= 60) {
                    val darkness = (((score - 60) / 30f).coerceIn(0f, 1f)) * 0.55f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF000814).copy(alpha = darkness)
                            ),
                            center = Offset(octoX, octoY),
                            radius = 620f
                        ),
                        topLeft = Offset(-50f, -50f),
                        size = Size(1100f, 1100f)
                    )
                }
            }
        }

        // --- 2. GAME HUD (DURING GAMEPLAY ONLY) ---
        if (state == GameScreenState.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                // Large Background Score Display (from the HTML)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "$score",
                        color = Color.White.copy(alpha = 0.08f),
                        fontSize = 130.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Row containing Top Left (Live Session Capsule) and Top Right (Best Score Block)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE SESSION Capsule
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pink_pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF472B6).copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE SESSION",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Real-time score counter
                        Text(
                            text = "•  $score",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("current_score")
                        )
                    }

                    // Best Score Block (Top Right)
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "BEST SCORE",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${highestScore ?: 0}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Active power-up effects + combo indicator
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = biome.name,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    if (gameMode == GameMode.LEVELS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "NIVEAU $currentLevel · OBJECTIF ${viewModel.levelTarget(currentLevel)} PTS",
                            color = Color(0xFFFFD700).copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shieldCharges > 0) {
                            EffectChip("🛡", "Bouclier", Color(0xFF00E5FF))
                        }
                        if (magnetTime > 0f) {
                            EffectChip("🧲", "${magnetTime.toInt() + 1}s", Color(0xFFFF6B6B))
                        }
                        if (slowMoTime > 0f) {
                            EffectChip("⏳", "${slowMoTime.toInt() + 1}s", Color(0xFFA29BFE))
                        }
                    }

                    if (combo >= 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val comboTransition = rememberInfiniteTransition(label = "combo")
                        val comboScale by comboTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "combo_scale"
                        )
                        Text(
                            text = "COMBO x$combo",
                            color = Color(0xFFFFD700),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.scale(comboScale),
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFFFF9F43),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            )
                        )
                    }
                }

                // Pause button (below the best-score block, top right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { viewModel.pauseGame() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("pause_button")
                ) {
                    Text(text = "⏸", color = Color.White, fontSize = 16.sp)
                }

                // Touch Interaction Hint (from the HTML)
                if (score < 2) {
                    val infiniteTransition = rememberInfiniteTransition(label = "hint_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulser"
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .scale(pulseScale)
                                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Touchez l'écran",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "TAPOTEZ L'ÉCRAN POUR NAGER",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        // --- 3. OVERLAYS (MENU & GAME OVER STATES) ---
        AnimatedVisibility(
            visible = state == GameScreenState.MENU,
            enter = fadeIn(animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Upper Menu Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp), // Spacious margins for bottom navigation
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Title block - Always shown to display cohesive brand identity
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text(
                            text = "POULPE FLAPPY",
                            color = Color(0xFF55E6C1),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFF00ADB5),
                                    offset = Offset(2f, 4f),
                                    blurRadius = 8f
                                )
                            )
                        )
                        Text(
                            text = "Aventures dans les abîmes de l'océan",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Middle content depends on selected tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (menuTab) {
                            "home" -> {
                                // Scrollable so secondary content can never push the play button away
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Compact preview of the selected Octopus Skin
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(selectedSkin.primaryColor.copy(alpha = 0.45f), Color.Transparent)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(selectedSkin.primaryColor, selectedSkin.accentColor)
                                                    )
                                                )
                                                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Touch Name Field
                                    OutlinedTextField(
                                        value = playerName,
                                        onValueChange = { viewModel.setPlayerName(it) },
                                        label = { Text("Votre Nom d'Explorateur", color = Color.White.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF55E6C1),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                            focusedLabelColor = Color(0xFF55E6C1),
                                            cursorColor = Color(0xFF55E6C1)
                                        ),
                                        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("player_name_input")
                                            .padding(bottom = 12.dp)
                                    )

                                    // PLAY BUTTON — always right under the name, above the fold
                                    Button(
                                        onClick = { viewModel.startGame() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55E6C1)),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .testTag("start_game_button")
                                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play icon",
                                            tint = Color(0xFF0F2027)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "LANCER L'EXPLORATION",
                                            color = Color(0xFF0F2027),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Pearl wallet chip — tap habits: players see their currency grow
                                    Row(
                                        modifier = Modifier
                                            .padding(bottom = 12.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFFFD700).copy(alpha = 0.1f))
                                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🦪", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$pearlWallet",
                                            color = Color(0xFFFFD700),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    // MODE SELECTOR: endless run vs fixed-objective levels
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            GameMode.INFINITE to "🌊 INFINI",
                                            GameMode.LEVELS to "🎯 NIVEAUX"
                                        ).forEach { (mode, label) ->
                                            val isSel = gameMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSel) Color(0xFF1B9CFC).copy(alpha = 0.3f)
                                                        else Color.White.copy(alpha = 0.06f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSel) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.1f),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { viewModel.selectMode(mode) }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSel) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                        }
                                    }

                                    // LEVEL PICKER (levels mode only): locked levels are grayed out
                                    if (gameMode == GameMode.LEVELS) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            (1..GameViewModel.MAX_LEVEL).forEach { lvl ->
                                                val unlocked = lvl <= maxUnlockedLevel
                                                val isSel = currentLevel == lvl
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                isSel -> Color(0xFF55E6C1)
                                                                unlocked -> Color.White.copy(alpha = 0.1f)
                                                                else -> Color.White.copy(alpha = 0.03f)
                                                            }
                                                        )
                                                        .clickable(enabled = unlocked) { viewModel.selectLevel(lvl) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (unlocked) "$lvl" else "🔒",
                                                        color = when {
                                                            isSel -> Color(0xFF0F2027)
                                                            unlocked -> Color.White
                                                            else -> Color.White.copy(alpha = 0.35f)
                                                        },
                                                        fontSize = if (unlocked) 13.sp else 9.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Objectif du niveau $currentLevel : ${viewModel.levelTarget(currentLevel)} points",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                    }

                                    // ACTIVE MISSIONS — three at once, replaced as soon as completed
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFFF9F43).copy(alpha = 0.1f))
                                            .border(1.dp, Color(0xFFFF9F43).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "🎯", fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "MISSIONS",
                                                color = Color(0xFFFF9F43),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                            if (missionsCompletedCount == 0) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "1ʳᵉ mission = Cache-œil 🏴‍☠️",
                                                    color = Color.White.copy(alpha = 0.45f),
                                                    fontSize = 8.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        activeMissions.forEach { mission ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "•  ${mission.label}",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "+${mission.reward} 🦪",
                                                    color = Color(0xFFFFD700).copy(alpha = 0.85f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Manual update check, visible without digging into settings.
                                    // Re-checking also re-arms the update popup if it was dismissed.
                                    val checkBusy = updateState is UpdateState.Checking || updateState is UpdateState.Downloading
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .clickable(enabled = !checkBusy) {
                                                dismissedUpdateTag = null
                                                updateManager.checkUpdates()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (updateState is UpdateState.Checking) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(15.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF1B9CFC)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Vérifier les mises à jour",
                                                tint = Color(0xFF1B9CFC),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Vérifier les mises à jour",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val (statusText, statusColor) = when (val s = updateState) {
                                                is UpdateState.Checking -> "Vérification sur GitHub..." to Color.White.copy(alpha = 0.5f)
                                                is UpdateState.NoUpdate -> "✓ À jour (v${updateManager.currentVersionName})" to Color(0xFF2ecc71)
                                                is UpdateState.UpdateAvailable -> "Nouvelle version v${s.tagName} disponible !" to Color(0xFFFF9F43)
                                                is UpdateState.Downloading -> "Téléchargement ${s.progress}%" to Color(0xFF55E6C1)
                                                is UpdateState.ReadyToInstall -> "Installation..." to Color(0xFF55E6C1)
                                                is UpdateState.Error -> s.message to Color(0xFFFF7675)
                                                else -> "Version installée : v${updateManager.currentVersionName}" to Color.White.copy(alpha = 0.4f)
                                            }
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontSize = 9.sp,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }

                                }
                            }
                            "ranks" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.85f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "🏆 CLASSEMENT",
                                        color = Color(0xFF55E6C1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Text(
                                        text = "Meilleurs scores enregistrés sur cet appareil",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(bottom = 12.dp)
                                    )

                                    if (topScores.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Aucun score enregistré.\nSoyez le premier !",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            topScores.forEachIndexed { index, scoreItem ->
                                                val medalTint = when (index) {
                                                    0 -> Color(0xFFFFD700) // Gold
                                                    1 -> Color(0xFFC0C0C0) // Silver
                                                    2 -> Color(0xFFCD7F32) // Bronze
                                                    else -> Color.White.copy(alpha = 0.6f)
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (index % 2 == 0) Color.White.copy(alpha = 0.04f)
                                                            else Color.Transparent
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "#${index + 1}",
                                                            color = medalTint,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.width(32.dp)
                                                        )
                                                        Text(
                                                            text = scoreItem.playerName,
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = "Points",
                                                            tint = Color(0xFFFFD700),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${scoreItem.score}",
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // --- ACHIEVEMENTS / TROPHIES ---
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 14.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    )
                                    Text(
                                        text = "🏅 SUCCÈS",
                                        color = Color(0xFF55E6C1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Text(
                                        text = "${unlockedAchievements.size}/${ALL_ACHIEVEMENTS.size} débloqués · récompenses en perles",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(bottom = 10.dp)
                                    )
                                    ALL_ACHIEVEMENTS.forEach { achievement ->
                                        val isUnlocked = achievement.id in unlockedAchievements
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isUnlocked) Color(0xFFFFD700).copy(alpha = 0.08f)
                                                    else Color.White.copy(alpha = 0.03f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isUnlocked) Color(0xFFFFD700).copy(alpha = 0.3f)
                                                    else Color.White.copy(alpha = 0.05f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isUnlocked) achievement.emoji else "🔒",
                                                fontSize = 18.sp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = achievement.title,
                                                    color = if (isUnlocked) Color(0xFFFFD700) else Color.White.copy(alpha = 0.55f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = achievement.description,
                                                    color = Color.White.copy(alpha = if (isUnlocked) 0.7f else 0.4f),
                                                    fontSize = 10.sp,
                                                    lineHeight = 13.sp
                                                )
                                            }
                                            Text(
                                                text = if (isUnlocked) "✓" else "+${achievement.reward} 🦪",
                                                color = if (isUnlocked) Color(0xFF2ecc71) else Color(0xFFFFD700).copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                            "skins" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.85f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🛍️ BOUTIQUE & SKINS",
                                        color = Color(0xFF55E6C1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = "Débloque avec ton record, achète avec tes perles",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Pearl wallet — the shop currency earned by playing
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🦪", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$pearlWallet perles",
                                            color = Color(0xFFFFD700),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // SECTION 1: Skins Colors
                                    Text(
                                        text = "🎨 COULEURS DE PEAU",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier
                                            .align(Alignment.Start)
                                            .padding(bottom = 8.dp)
                                    )

                                    OctopusSkin.values().forEach { skin ->
                                        val isSelected = selectedSkin == skin
                                        val unlocked = if (skin.price > 0) skin.name in purchasedCosmetics
                                                       else (highestScore ?: 0) >= skin.requiredScore
                                        val affordable = pearlWallet >= skin.price
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) Color(0xFF1B9CFC).copy(alpha = 0.15f)
                                                    else Color.White.copy(alpha = 0.04f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable(enabled = unlocked) { viewModel.setSkin(skin) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Colored dot representing the skin
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(
                                                                    skin.primaryColor.copy(alpha = if (unlocked) 1f else 0.3f),
                                                                    skin.accentColor.copy(alpha = if (unlocked) 1f else 0.3f)
                                                                )
                                                            )
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = skin.skinName,
                                                    color = Color.White.copy(alpha = if (unlocked) 1f else 0.4f),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                            if (isSelected) {
                                                Text(
                                                    text = "ÉQUIPÉ",
                                                    color = Color(0xFF55E6C1),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp
                                                )
                                            } else if (!unlocked && skin.price > 0) {
                                                // Shop item: buy with pearls
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (affordable) Color(0xFFFFD700).copy(alpha = 0.2f)
                                                            else Color.White.copy(alpha = 0.05f)
                                                        )
                                                        .clickable(enabled = affordable) { viewModel.buySkin(skin) }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = "${skin.price} 🦪",
                                                        color = if (affordable) Color(0xFFFFD700) else Color.White.copy(alpha = 0.35f),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            } else if (!unlocked) {
                                                Text(
                                                    text = "🔒 ${skin.requiredScore} pts",
                                                    color = Color(0xFFFF9F43).copy(alpha = 0.8f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // SECTION 2: Accessories Cosmetics
                                    Text(
                                        text = "👑 ACCESSOIRES ET CHAPEAUX",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier
                                            .align(Alignment.Start)
                                            .padding(bottom = 8.dp)
                                    )

                                    OctopusAccessory.values().forEach { accessory ->
                                        val isSelected = selectedAccessory == accessory
                                        val unlocked = when {
                                            accessory.missionReward -> missionsCompletedCount > 0
                                            accessory.price > 0 -> accessory.name in purchasedCosmetics
                                            else -> (highestScore ?: 0) >= accessory.requiredScore
                                        }
                                        val affordable = pearlWallet >= accessory.price
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) Color(0xFF1B9CFC).copy(alpha = 0.15f)
                                                    else Color.White.copy(alpha = 0.04f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable(enabled = unlocked) { viewModel.setAccessory(accessory) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = accessory.displayName,
                                                    color = Color.White.copy(alpha = if (unlocked) 1f else 0.4f),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                            if (isSelected) {
                                                Text(
                                                    text = "ÉQUIPÉ",
                                                    color = Color(0xFF55E6C1),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp
                                                )
                                            } else if (!unlocked && accessory.price > 0) {
                                                // Shop item: buy with pearls
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (affordable) Color(0xFFFFD700).copy(alpha = 0.2f)
                                                            else Color.White.copy(alpha = 0.05f)
                                                        )
                                                        .clickable(enabled = affordable) { viewModel.buyAccessory(accessory) }
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = "${accessory.price} 🦪",
                                                        color = if (affordable) Color(0xFFFFD700) else Color.White.copy(alpha = 0.35f),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            } else if (!unlocked) {
                                                Text(
                                                    text = if (accessory.missionReward) "🔒 1ʳᵉ mission"
                                                           else "🔒 ${accessory.requiredScore} pts",
                                                    color = Color(0xFFFF9F43).copy(alpha = 0.8f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "audio" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.85f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "⚙️ OPTIONS",
                                        color = Color(0xFF55E6C1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Text(
                                        text = "Audio et mises à jour du jeu",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(bottom = 14.dp)
                                    )

                                    Text(
                                        text = "🔊 AUDIO",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Music Volume Slider
                                    Text(
                                        text = "Volume Musique",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Slider(
                                        value = musicVolume,
                                        onValueChange = { musicVolume = it },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF55E6C1),
                                            activeTrackColor = Color(0xFF55E6C1),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // SFX Volume Slider
                                    Text(
                                        text = "Volume Effets Sonores",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Slider(
                                        value = sfxVolume,
                                        onValueChange = { sfxVolume = it },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF55E6C1),
                                            activeTrackColor = Color(0xFF55E6C1),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    )

                                    // GITHUB UPDATE UI SECTION (Fully Reactive)
                                    run {
                                        Text(
                                            text = "📲 MISES À JOUR",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Version actuelle : ${updateManager.currentVersionName}",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Les mises à jour sont vérifiées automatiquement",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 10.sp
                                                )
                                            }

                                            if (updateState is UpdateState.Idle || updateState is UpdateState.NoUpdate || updateState is UpdateState.Error) {
                                                Button(
                                                    onClick = { updateManager.checkUpdates() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B9CFC)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Vérifier", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                         when (val stateVal = updateState) {
                                            is UpdateState.Checking -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color(0xFF55E6C1)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "Vérification sur GitHub...",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            is UpdateState.NoUpdate -> {
                                                Text(
                                                    text = "✓ Votre jeu est entièrement à jour !",
                                                    color = Color(0xFF55E6C1),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                            is UpdateState.UpdateAvailable -> {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF130F40).copy(alpha = 0.6f))
                                                        .border(1.dp, Color(0xFFFF9F43).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                        .padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = "Nouvelle version : v${stateVal.tagName}",
                                                        color = Color(0xFFFF9F43),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    
                                                    Text(
                                                        text = "Nouveautés :\n${stateVal.changelog}",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    
                                                    Button(
                                                        onClick = { updateManager.downloadAndInstall(stateVal.downloadUrl) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55E6C1)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "TÉLÉCHARGER & INSTALLER EN DIRECT",
                                                            color = Color(0xFF0F2027),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            }
                                            is UpdateState.Downloading -> {
                                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Téléchargement de l'APK...",
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            fontSize = 11.sp
                                                        )
                                                        Text(
                                                            text = "${stateVal.progress}%",
                                                            color = Color(0xFF55E6C1),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LinearProgressIndicator(
                                                        progress = { stateVal.progress / 100f },
                                                        color = Color(0xFF55E6C1),
                                                        trackColor = Color.White.copy(alpha = 0.15f),
                                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                                                    )
                                                }
                                            }
                                            is UpdateState.ReadyToInstall -> {
                                                Text(
                                                    text = "✓ Téléchargement terminé ! Installation...",
                                                    color = Color(0xFF55E6C1),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                            is UpdateState.Error -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFFF7675).copy(alpha = 0.15f))
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stateVal.message,
                                                        color = Color(0xFFFF7675),
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFF7675).copy(alpha = 0.2f))
                                                            .clickable { updateManager.resetState() }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("OK", color = Color(0xFFFF7675), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // BOTTOM NAVIGATION BAR (at the bottom of the Box)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color(0xFF002D4A))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple("home", Icons.Default.Home, "Accueil"),
                        Triple("ranks", Icons.Default.Star, "Rangs"),
                        Triple("skins", Icons.Default.Face, "Skins"),
                        Triple("audio", Icons.Default.Settings, "Options")
                    )

                    tabs.forEach { (tabId, icon, label) ->
                        val isSelected = menuTab == tabId
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { menuTab = tabId }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color(0xFF1B9CFC).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF55E6C1) else Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // --- 3b. PAUSE OVERLAY ---
        AnimatedVisibility(
            visible = state == GameScreenState.PAUSED,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF0F2027).copy(alpha = 0.95f))
                        .border(1.5.dp, Color(0xFF1B9CFC).copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⏸ PAUSE",
                        color = Color(0xFF55E6C1),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Score actuel : $score",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.resumeGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55E6C1)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("resume_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reprendre",
                            tint = Color(0xFF0F2027)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REPRENDRE",
                            color = Color(0xFF0F2027),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.quitRun() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = "ABANDONNER LA PARTIE",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 4. GAME OVER SCREEN OVERLAY ---
        AnimatedVisibility(
            visible = state == GameScreenState.GAME_OVER,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF0F2027).copy(alpha = 0.95f))
                        .border(1.5.dp, Color(0xFFDE1057).copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (levelCompleted) "NIVEAU $currentLevel RÉUSSI ! 🎉" else "EXPLORATION TERMINÉE",
                        color = if (levelCompleted) Color(0xFF2ecc71) else Color(0xFFFF5252),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    // Performance medal for the run (bronze / silver / gold)
                    val medal = when {
                        score >= 50 -> Triple("🥇", "MÉDAILLE D'OR", Color(0xFFFFD700))
                        score >= 25 -> Triple("🥈", "MÉDAILLE D'ARGENT", Color(0xFFC0C0C0))
                        score >= 10 -> Triple("🥉", "MÉDAILLE DE BRONZE", Color(0xFFCD7F32))
                        else -> null
                    }
                    if (medal != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = medal.first, fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = medal.second,
                                color = medal.third,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    if (newRecord) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val recordTransition = rememberInfiniteTransition(label = "record")
                        val recordScale by recordTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(450, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "record_scale"
                        )
                        Text(
                            text = "🏆 NOUVEAU RECORD !",
                            color = Color(0xFFFFD700),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.scale(recordScale),
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFFFF9F43),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 14f
                                )
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Final score breakdown
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SCORE FINAL",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$score",
                                color = Color(0xFFFFD700),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("final_score")
                            )
                            // Competitive anchor: always confront the player with their record
                            Text(
                                text = "RECORD : ${maxOf(highestScore ?: 0, score)}",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Pearls earned this run (collected + medal/mission/level bonuses)
                    if (runPearlsEarned > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🦪", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+$runPearlsEarned perles  ·  total : $pearlWallet",
                                color = Color(0xFFFFD700),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Missions completed during this very run
                    missionsJustCompleted.forEach { mission ->
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2ecc71).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF2ecc71).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🎯", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${mission.label}  +${mission.reward} 🦪",
                                color = Color(0xFF2ecc71),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Trophies unlocked during this run
                    newAchievements.forEach { achievement ->
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = achievement.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Succès : ${achievement.title}  +${achievement.reward} 🦪",
                                color = Color(0xFFFFD700),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // The score is saved automatically when the run ends
                    if (highScoreSaved) {
                        Text(
                            text = "✓ Score enregistré pour ${playerName.ifEmpty { "Explorateur" }}",
                            color = Color(0xFF2ecc71),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Second chance: spend pearls to resume the crashed run (once per run)
                    if (!levelCompleted && reviveAvailable && pearlWallet >= GameViewModel.REVIVE_COST) {
                        Button(
                            onClick = { viewModel.revive() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("revive_button")
                        ) {
                            Text(
                                text = "💫 REPRENDRE — ${GameViewModel.REVIVE_COST} 🦪",
                                color = Color(0xFF0F2027),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Replay / Back Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Replay — or jump to the next level after clearing one
                        val goNextLevel = levelCompleted && currentLevel < GameViewModel.MAX_LEVEL
                        Button(
                            onClick = {
                                if (goNextLevel) viewModel.startNextLevel() else viewModel.startGame()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (goNextLevel) Color(0xFF2ecc71) else Color(0xFFDE1057)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("restart_button")
                        ) {
                            Icon(
                                imageVector = if (goNextLevel) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (goNextLevel) "NIVEAU SUIVANT" else "REESSAYER",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Back to Menu
                        OutlinedButton(
                            onClick = { viewModel.backToMenu() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("back_to_menu_button")
                        ) {
                            Text(
                                text = "MENU PRINCIPAL",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- UPDATE PROPOSAL POPUP ---
        // The app watches the GitHub repo in background; when a new release is found,
        // this dialog proposes the update (outside of an active run, never mid-game).
        val availableUpdate = updateState as? UpdateState.UpdateAvailable
        if (availableUpdate != null &&
            state != GameScreenState.PLAYING &&
            dismissedUpdateTag != availableUpdate.tagName
        ) {
            AlertDialog(
                onDismissRequest = { dismissedUpdateTag = availableUpdate.tagName },
                containerColor = Color(0xFF130F40),
                title = {
                    Text(
                        text = "Mise à jour disponible !",
                        color = Color(0xFFFF9F43),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Version v${availableUpdate.tagName} (installée : v${updateManager.currentVersionName})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = availableUpdate.changelog,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissedUpdateTag = availableUpdate.tagName
                            updateManager.downloadAndInstall(availableUpdate.downloadUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55E6C1))
                    ) {
                        Text("METTRE À JOUR", color = Color(0xFF0F2027), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissedUpdateTag = availableUpdate.tagName }) {
                        Text("Plus tard", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

/** Short haptic buzz played when the octopus crashes. */
private fun vibrateCrash(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(220L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(220L)
        }
    } catch (e: Exception) {
        // Devices without a vibrator simply skip the feedback
    }
}

/** A depth biome: a name and the three gradient stops for the ocean background. */
private data class Biome(val name: String, val top: Color, val mid: Color, val bottom: Color)

/** Maps the current score to a depth biome. Deeper = darker, shifting hue. */
private fun biomeFor(score: Int): Biome = when {
    score < 10 -> Biome("EAUX ENSOLEILLÉES", Color(0xFF005B8C), Color(0xFF002B45), Color(0xFF001525))
    score < 25 -> Biome("RÉCIF DE CORAIL", Color(0xFF006D6A), Color(0xFF00343A), Color(0xFF001A1E))
    score < 50 -> Biome("GRAND BLEU", Color(0xFF002B6E), Color(0xFF001233), Color(0xFF000A1A))
    else -> Biome("ABYSSES", Color(0xFF1E0A4E), Color(0xFF0A0426), Color(0xFF02010A))
}

/** Small capsule shown in the HUD for an active power-up effect. */
@Composable
private fun EffectChip(emoji: String, label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
