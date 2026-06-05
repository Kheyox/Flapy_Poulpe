package com.example.game

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HighScore
import com.example.data.HighScoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class GameScreenState {
    MENU,
    PLAYING,
    GAME_OVER
}

data class Octopus(
    val y: Float, // relative game space Y: 0.0f to 1000.0f
    val velocityY: Float,
    val size: Float = 32f // collision radius
)

data class CoralObstacle(
    val x: Float, // relative game space X: from right (1100.0f) to left
    val gapY: Float, // center of gap in Y
    val gapHeight: Float = 260f,
    val width: Float = 120f,
    val passed: Boolean = false
)

data class Pearl(
    val x: Float,
    val y: Float,
    val radius: Float = 16f,
    val collected: Boolean = false
)

data class Bubble(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speedX: Float,
    val speedY: Float,
    val maxLife: Float,
    val life: Float
)

data class StarParticle(
    val x: Float,
    val y: Float,
    val tx: Float,
    val ty: Float,
    val color: Color,
    val life: Float
)

class GameViewModel(private val repository: HighScoreRepository) : ViewModel() {

    private val _gameScreenState = MutableStateFlow(GameScreenState.MENU)
    val gameScreenState: StateFlow<GameScreenState> = _gameScreenState.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _highScoreSaved = MutableStateFlow(false)
    val highScoreSaved: StateFlow<Boolean> = _highScoreSaved.asStateFlow()

    private val _octopusY = MutableStateFlow(500f)
    val octopusY: StateFlow<Float> = _octopusY.asStateFlow()

    private val _obstacles = MutableStateFlow<List<CoralObstacle>>(emptyList())
    val obstacles: StateFlow<List<CoralObstacle>> = _obstacles.asStateFlow()

    private val _pearls = MutableStateFlow<List<Pearl>>(emptyList())
    val pearls: StateFlow<List<Pearl>> = _pearls.asStateFlow()

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _particles = MutableStateFlow<List<StarParticle>>(emptyList())
    val particles: StateFlow<List<StarParticle>> = _particles.asStateFlow()

    private val _playerName = MutableStateFlow("Explorateur")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _waveTime = MutableStateFlow(0f)
    val waveTime: StateFlow<Float> = _waveTime.asStateFlow()

    // Top 10 high scores reactively observed from Room DB
    val topScores: StateFlow<List<HighScore>> = repository.topScores
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val highestScore: StateFlow<Int?> = repository.highestScore
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private var velocityY = 0f
    private val _velocityY = MutableStateFlow(0f)
    val velocityYFlow: StateFlow<Float> = _velocityY.asStateFlow()

    private var gameJob: Job? = null

    init {
        // Hydrate background with ambient bubbles on start
        val initialAmbientBubbles = List(25) {
            Bubble(
                x = Random.nextFloat() * 1050f,
                y = Random.nextFloat() * 1050f,
                radius = Random.nextFloat() * 6f + 2f,
                speedX = Random.nextFloat() * 8f - 4f,
                speedY = -Random.nextFloat() * 40f - 10f,
                maxLife = Random.nextFloat() * 6f + 3f,
                life = Random.nextFloat() * 5f + 1f
            )
        }
        _bubbles.value = initialAmbientBubbles
    }

    fun setPlayerName(name: String) {
        if (name.length <= 12) {
            _playerName.value = name
        }
    }

    fun swim() {
        if (_gameScreenState.value != GameScreenState.PLAYING) return
        
        // Buoyancy lift force impulse (underwater version of jump!)
        velocityY = -390f
        _velocityY.value = velocityY
        
        // Spawn active bubbles at the bottom of the octopus!
        spawnSwimmingBubbles(220f, _octopusY.value, count = 10)
    }

    fun startGame() {
        _score.value = 0
        _highScoreSaved.value = false
        _octopusY.value = 500f
        velocityY = -100f // initial push
        _velocityY.value = velocityY
        _obstacles.value = emptyList()
        _pearls.value = emptyList()
        _particles.value = emptyList()
        _gameScreenState.value = GameScreenState.PLAYING

        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            var obstacleSpawnAccumulator = 0f

            while (_gameScreenState.value == GameScreenState.PLAYING) {
                val currentTime = System.nanoTime()
                val dt = (currentTime - lastTime) / 1_000_000_000f
                val cappedDt = dt.coerceAtMost(0.04f) // Safe cap at ~25fps boundary
                lastTime = currentTime

                // 1. Wave clock for seaweed animations
                _waveTime.value = (_waveTime.value + cappedDt) % (Math.PI.toFloat() * 2f)

                // 2. Physics logic and checks
                updateGame(cappedDt)

                // 3. Obstacle Spawning frequency scales with score (faster spawn limits max wait time)
                obstacleSpawnAccumulator += cappedDt
                val currentInterval = (2.4f - (_score.value * 0.015f)).coerceAtLeast(1.4f)
                if (obstacleSpawnAccumulator >= currentInterval) {
                    spawnObstacle()
                    obstacleSpawnAccumulator = 0f
                }

                delay(12) // higher precision animation tick (~80fps loop capped, smooth)
            }
        }
    }

    private fun updateGame(dt: Float) {
        // 1. Apply gravity (buoyancy is weaker than full air gravity)
        velocityY += 1050f * dt
        _velocityY.value = velocityY
        val nextOctopusY = _octopusY.value + velocityY * dt
        _octopusY.value = nextOctopusY

        // 2. Bound check (crash when hitting surface or bottom depths)
        if (nextOctopusY - 32f < 0f || nextOctopusY + 32f > 1000f) {
            endGame()
            return
        }

        // 3. Move obstacles & detect collisions
        val speedMultiplier = (1.0f + (_score.value * 0.015f)).coerceAtMost(1.5f)
        val currentSpeed = 220f * speedMultiplier

        val currentObstacles = _obstacles.value
        val nextObstacles = mutableListOf<CoralObstacle>()
        for (obs in currentObstacles) {
            val nextX = obs.x - currentSpeed * dt
            if (nextX + obs.width < 0f) {
                continue // past left side
            }

            var isPassed = obs.passed
            if (!isPassed && nextX + obs.width / 2f <= 220f) {
                isPassed = true
                _score.value += 1
                spawnWavedBubbles(nextX + obs.width / 2f, obs.gapY, count = 4)
            }

            // Precisely check circle-to-rectangle collisions with both coral columns
            val topCrash = checkCircleRectCollision(
                circleX = 220f, circleY = _octopusY.value, radius = 28f,
                rectX1 = nextX, rectY1 = 0f, rectX2 = nextX + obs.width, rectY2 = obs.gapY - obs.gapHeight / 2f
            )
            val bottomCrash = checkCircleRectCollision(
                circleX = 220f, circleY = _octopusY.value, radius = 28f,
                rectX1 = nextX, rectY1 = obs.gapY + obs.gapHeight / 2f, rectX2 = nextX + obs.width, rectY2 = 1000f
            )

            if (topCrash || bottomCrash) {
                endGame()
                return
            }

            nextObstacles.add(obs.copy(x = nextX, passed = isPassed))
        }
        _obstacles.value = nextObstacles

        // 4. Move pearls and handle collection
        val currentPearls = _pearls.value
        val nextPearls = mutableListOf<Pearl>()
        for (pearl in currentPearls) {
            val nextX = pearl.x - currentSpeed * dt
            if (nextX + 40f < 0f) {
                continue
            }

            // Check collision with octopus
            val distSq = (220f - nextX) * (220f - nextX) + (_octopusY.value - pearl.y) * (_octopusY.value - pearl.y)
            if (distSq < (30f + 16f) * (30f + 16f)) {
                // Jackpot! 5 bonus points + gold splash
                _score.value += 5
                triggerPearlExplosion(nextX, pearl.y)
                continue
            }

            nextPearls.add(pearl.copy(x = nextX))
        }
        _pearls.value = nextPearls

        // 5. Update active decorative systems (particles & bubbles)
        updateParticlesAndBubbles(dt, currentSpeed)
    }

    private fun spawnObstacle() {
        val gapY = Random.nextFloat() * 480f + 260f // center centered between 260 and 740
        val defaultGapHeight = 270f
        // Reduce gap size slightly over time for difficulty tuning
        val currentGapHeight = (defaultGapHeight - (_score.value * 2f)).coerceAtLeast(190f)

        val newObstacle = CoralObstacle(
            x = 1100f,
            gapY = gapY,
            gapHeight = currentGapHeight,
            width = 115f
        )
        _obstacles.value = _obstacles.value + newObstacle

        // Spawn a valuable gold pearl inside the gap
        if (Random.nextFloat() < 0.55f) {
            val pearlY = gapY + Random.nextFloat() * 80f - 40f
            val newPearl = Pearl(
                x = 1100f + 57.5f,
                y = pearlY
            )
            _pearls.value = _pearls.value + newPearl
        }
    }

    private fun checkCircleRectCollision(
        circleX: Float, circleY: Float, radius: Float,
        rectX1: Float, rectY1: Float, rectX2: Float, rectY2: Float
    ): Boolean {
        val closestX = circleX.coerceIn(rectX1, rectX2)
        val closestY = circleY.coerceIn(rectY1, rectY2)
        val dx = circleX - closestX
        val dy = circleY - closestY
        return (dx * dx + dy * dy) < (radius * radius)
    }

    fun saveHighScore() {
        if (_highScoreSaved.value) return
        val finalScore = _score.value
        val name = _playerName.value.trim().ifEmpty { "Explorateur" }

        viewModelScope.launch {
            repository.insertScore(HighScore(playerName = name, score = finalScore))
            _highScoreSaved.value = true
        }
    }

    private fun endGame() {
        _gameScreenState.value = GameScreenState.GAME_OVER
        gameJob?.cancel()

        // Screen shake of particles on impact
        triggerCrashExplosion(220f, _octopusY.value)
    }

    private fun updateParticlesAndBubbles(dt: Float, currentSpeed: Float) {
        // Particles
        val currentParticles = _particles.value
        val nextParticles = mutableListOf<StarParticle>()
        for (p in currentParticles) {
            val nextLife = p.life - dt * 2.2f // faded in 0.45s
            if (nextLife > 0f) {
                val nextX = p.x + p.tx * dt - currentSpeed * 0.15f * dt
                val nextY = p.y + p.ty * dt + 120f * dt // weight pulling particles down
                nextParticles.add(p.copy(x = nextX, y = nextY, life = nextLife))
            }
        }
        _particles.value = nextParticles

        // Bubbles
        val currentBubbles = _bubbles.value
        val nextBubbles = mutableListOf<Bubble>()
        for (b in currentBubbles) {
            val nextLife = b.life - dt
            if (nextLife > 0f) {
                // bubbles drift backward as screen scrolls, mimicking speed
                val nextX = b.x + b.speedX * dt - currentSpeed * 0.12f * dt
                val nextY = b.y + b.speedY * dt
                nextBubbles.add(b.copy(x = nextX, y = nextY, life = nextLife))
            }
        }

        // Keep ambient bubbling constant
        if (nextBubbles.size < 40 && Random.nextFloat() < 0.2f) {
            nextBubbles.add(
                Bubble(
                    x = Random.nextFloat() * 1100f,
                    y = 1015f,
                    radius = Random.nextFloat() * 5f + 1.5f,
                    speedX = Random.nextFloat() * 6f - 3f,
                    speedY = -Random.nextFloat() * 60f - 20f,
                    maxLife = Random.nextFloat() * 7f + 3f,
                    life = Random.nextFloat() * 6f + 2f
                )
            )
        }
        _bubbles.value = nextBubbles
    }

    private fun spawnSwimmingBubbles(x: Float, y: Float, count: Int) {
        val newBubbles = List(count) {
            Bubble(
                x = x + Random.nextFloat() * 10f - 5f,
                y = y + 25f, // spawn at the bottom of the octopus body
                radius = Random.nextFloat() * 5f + 2f,
                speedX = Random.nextFloat() * 40f - 20f,
                speedY = Random.nextFloat() * 40f + 50f, // push down initially, then float takes over!
                maxLife = Random.nextFloat() * 1.5f + 0.5f,
                life = Random.nextFloat() * 1.5f + 0.5f
            )
        }
        _bubbles.value = _bubbles.value + newBubbles
    }

    private fun spawnWavedBubbles(x: Float, y: Float, count: Int) {
        val newBubbles = List(count) {
            Bubble(
                x = x,
                y = y,
                radius = Random.nextFloat() * 7f + 2f,
                speedX = Random.nextFloat() * 60f - 30f,
                speedY = -Random.nextFloat() * 60f - 10f,
                maxLife = Random.nextFloat() * 2f + 0.5f,
                life = Random.nextFloat() * 2f + 0.5f
            )
        }
        _bubbles.value = _bubbles.value + newBubbles
    }

    private fun triggerCrashExplosion(x: Float, y: Float) {
        val crashList = List(35) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = Random.nextFloat() * 420f + 80f
            StarParticle(
                x = x,
                y = y,
                tx = Math.cos(angle.toDouble()).toFloat() * speed,
                ty = Math.sin(angle.toDouble()).toFloat() * speed,
                color = if (Random.nextBoolean()) Color(0xFFFF5252) else Color(0xFF00E5FF),
                life = 1f
            )
        }
        _particles.value = _particles.value + crashList
    }

    private fun triggerPearlExplosion(x: Float, y: Float) {
        val pearlList = List(20) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = Random.nextFloat() * 280f + 40f
            StarParticle(
                x = x,
                y = y,
                tx = Math.cos(angle.toDouble()).toFloat() * speed,
                ty = Math.sin(angle.toDouble()).toFloat() * speed,
                color = Color(0xFFFFD700), // Pure golden sparkles
                life = 1f
            )
        }
        _particles.value = _particles.value + pearlList
    }

    fun backToMenu() {
        _gameScreenState.value = GameScreenState.MENU
        _highScoreSaved.value = false
    }
}

class GameViewModelFactory(private val repository: HighScoreRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
