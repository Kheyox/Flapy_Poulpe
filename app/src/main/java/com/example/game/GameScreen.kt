package com.example.game

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

enum class OctopusSkin(val skinName: String, val primaryColor: Color, val accentColor: Color) {
    CLASSIC("Rosé Classique", Color(0xFFFF7675), Color(0xFFD63031)),
    MINT("Menthe Néon", Color(0xFF55E6C1), Color(0xFF1B9CFC)),
    GOLD("Éclat d'Or", Color(0xFFFFD700), Color(0xFFFF9F43)),
    COSMIC("Abysses Violets", Color(0xFFA29BFE), Color(0xFF6C5CE7))
}

enum class OctopusAccessory(val displayName: String) {
    NONE("Aucun accessoire ✕"),
    CROWN("Couronne Royale 👑"),
    SAILOR("Chapeau de Marin ⚓"),
    SUNGLASSES("Lunettes de Soleil 😎"),
    PIRATE_PATCH("Cache-œil de Pirate 🏴‍☠️")
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
                    val isOrange = (obstacle.gapY.toInt() % 2 == 0)
                    
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Cute pulsing background card preview of the selected Octopus Skin!
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
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
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(selectedSkin.primaryColor, selectedSkin.accentColor)
                                                    )
                                                )
                                                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))

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
                                            .padding(bottom = 16.dp)
                                    )

                                    // Lancer l'exploration Play Button
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
                                }
                            }
                            "ranks" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Trophy Score",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "TOP EXPLORATEURS",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }

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
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(topScores) { index, scoreItem ->
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
                                }
                            }
                            "skins" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.85f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(1.5.dp, Color(0xFF1B9CFC).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "PERSONNALISATION DU POULPE",
                                        color = Color(0xFF55E6C1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
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
                                                .clickable { viewModel.setSkin(skin) }
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
                                                                colors = listOf(skin.primaryColor, skin.accentColor)
                                                            )
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = skin.skinName,
                                                    color = Color.White,
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
                                                .clickable { viewModel.setAccessory(accessory) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = accessory.displayName,
                                                    color = Color.White,
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
                                            }
                                        }
                                    }
                                }
                            }
                            "audio" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = "RÉGLAGES AUDIO",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(bottom = 16.dp)
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
                                            text = "MISES À JOUR DE L'APK",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
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
                        Triple("audio", Icons.Default.Settings, "Audio")
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                        text = "EXPLORATION TERMINÉE",
                        color = Color(0xFFFF5252),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    
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
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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

                    Spacer(modifier = Modifier.height(24.dp))

                    // Replay / Back Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Replay
                        Button(
                            onClick = { viewModel.startGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDE1057)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("restart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REESSAYER",
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
