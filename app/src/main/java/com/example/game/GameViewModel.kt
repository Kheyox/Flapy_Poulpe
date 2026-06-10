package com.example.game

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HighScore
import com.example.data.HighScoreRepository
import com.example.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class GameScreenState {
    MENU,
    PLAYING,
    PAUSED,
    GAME_OVER
}

/** A permanent trophy: unlocked once, pays a pearl reward. */
data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val reward: Int
)

val ALL_ACHIEVEMENTS = listOf(
    Achievement("first_dive", "🌊", "Première Plongée", "Terminer une partie", 25),
    Achievement("ten_games", "🎮", "Habitué des Abysses", "Jouer 10 parties", 50),
    Achievement("fifty_games", "🏊", "Marathonien des Mers", "Jouer 50 parties", 150),
    Achievement("score_30", "⭐", "Explorateur Confirmé", "Atteindre 30 points", 75),
    Achievement("score_60", "🌌", "Maître des Abysses", "Atteindre 60 points et survivre à l'obscurité", 200),
    Achievement("combo_5", "🔥", "Enchaînement Parfait", "Réaliser un combo de perles x5", 100),
    Achievement("pearls_500", "🦪", "Trésor Vivant", "Collecter 500 perles au total", 150),
    Achievement("level_5", "🎯", "Stratège", "Réussir le niveau 5", 100),
    Achievement("level_10", "👑", "Légende de l'Océan", "Réussir le niveau 10", 300)
)

enum class GameMode { INFINITE, LEVELS }

/** A daily challenge, picked deterministically from the date. */
enum class MissionKind { PEARLS, SCORE, NO_SHIELD, COMBO, POWERUPS }

data class DailyMission(
    val kind: MissionKind,
    val target: Int,
    val label: String
)

val ALL_MISSIONS = listOf(
    DailyMission(MissionKind.PEARLS, 8, "Collecte 8 perles en une partie"),
    DailyMission(MissionKind.SCORE, 15, "Atteins 15 points en une partie"),
    DailyMission(MissionKind.NO_SHIELD, 10, "Atteins 10 points sans ramasser de bouclier"),
    DailyMission(MissionKind.COMBO, 3, "Réalise un combo de perles x3"),
    DailyMission(MissionKind.POWERUPS, 2, "Ramasse 2 power-ups en une partie")
)

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
    val passed: Boolean = false,
    val bobSpeed: Float = 0f,
    val bobRange: Float = 0f,
    val initialGapY: Float = gapY
)

data class Pearl(
    val x: Float,
    val y: Float,
    val radius: Float = 16f,
    val collected: Boolean = false
)

enum class PowerUpType { SHIELD, MAGNET, SLOWMO }

data class PowerUp(
    val x: Float,
    val y: Float,
    val type: PowerUpType,
    val radius: Float = 24f
)

/** A jellyfish hazard drifting left while oscillating vertically. */
data class Jellyfish(
    val x: Float,
    val baseY: Float,
    val y: Float = baseY,
    val amplitude: Float,
    val speed: Float,
    val phase: Float,
    val radius: Float = 26f
)

/** A marine current zone that pushes the octopus up or down while inside. */
data class MarineCurrent(
    val x: Float,
    val width: Float = 260f,
    val force: Float // px/s² applied to vertical velocity; negative pushes up
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

class GameViewModel(
    private val repository: HighScoreRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object {
        // Single source of truth for the octopus hitbox. The head is drawn with a
        // radius of 30; we use a slightly smaller value so collisions feel fair
        // rather than punishing, and stay consistent between walls and bounds.
        const val OCTOPUS_RADIUS = 28f

        // Power-up durations (seconds) and shield invulnerability window.
        const val MAGNET_DURATION = 7f
        const val SLOWMO_DURATION = 5f
        const val SHIELD_INVULN = 1.2f

        // Levels mode: 10 levels, level n is cleared at n*10 points.
        const val MAX_LEVEL = 10

        // Pearl economy: cost of resuming a crashed run, and end-of-run bonuses.
        const val REVIVE_COST = 50
        const val MISSION_PEARL_BONUS = 100
        const val LEVEL_PEARL_BONUS = 20
    }

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

    private val _powerUps = MutableStateFlow<List<PowerUp>>(emptyList())
    val powerUps: StateFlow<List<PowerUp>> = _powerUps.asStateFlow()

    // Active effects
    private val _shieldCharges = MutableStateFlow(0)
    val shieldCharges: StateFlow<Int> = _shieldCharges.asStateFlow()

    private val _magnetTime = MutableStateFlow(0f)
    val magnetTime: StateFlow<Float> = _magnetTime.asStateFlow()

    private val _slowMoTime = MutableStateFlow(0f)
    val slowMoTime: StateFlow<Float> = _slowMoTime.asStateFlow()

    // Consecutive pearls collected without missing one — multiplies pearl score.
    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    // True while the octopus is briefly invulnerable after a shield save.
    private val _invuln = MutableStateFlow(false)
    val invuln: StateFlow<Boolean> = _invuln.asStateFlow()
    private var invulnTime = 0f

    // --- Depth hazards ---
    private val _jellyfish = MutableStateFlow<List<Jellyfish>>(emptyList())
    val jellyfish: StateFlow<List<Jellyfish>> = _jellyfish.asStateFlow()

    private val _currents = MutableStateFlow<List<MarineCurrent>>(emptyList())
    val currents: StateFlow<List<MarineCurrent>> = _currents.asStateFlow()

    // --- Game modes: endless run or fixed-objective levels ---
    private val _gameMode = MutableStateFlow(GameMode.INFINITE)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _currentLevel = MutableStateFlow(1)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _maxUnlockedLevel = MutableStateFlow(1)
    val maxUnlockedLevel: StateFlow<Int> = _maxUnlockedLevel.asStateFlow()

    private val _levelCompleted = MutableStateFlow(false)
    val levelCompleted: StateFlow<Boolean> = _levelCompleted.asStateFlow()

    // --- Records & daily mission ---
    private val _newRecord = MutableStateFlow(false)
    val newRecord: StateFlow<Boolean> = _newRecord.asStateFlow()

    private val _missionCompletedToday = MutableStateFlow(false)
    val missionCompletedToday: StateFlow<Boolean> = _missionCompletedToday.asStateFlow()

    private val _missionJustCompleted = MutableStateFlow(false)
    val missionJustCompleted: StateFlow<Boolean> = _missionJustCompleted.asStateFlow()

    private val _missionsCompletedCount = MutableStateFlow(0)
    val missionsCompletedCount: StateFlow<Int> = _missionsCompletedCount.asStateFlow()

    val dailyMission: DailyMission = ALL_MISSIONS[dayKey().hashCode().mod(ALL_MISSIONS.size)]

    // --- Pearl economy ---
    private val _pearlWallet = MutableStateFlow(0)
    val pearlWallet: StateFlow<Int> = _pearlWallet.asStateFlow()

    private val _purchasedCosmetics = MutableStateFlow<Set<String>>(emptySet())
    val purchasedCosmetics: StateFlow<Set<String>> = _purchasedCosmetics.asStateFlow()

    private val _runPearlsEarned = MutableStateFlow(0)
    val runPearlsEarned: StateFlow<Int> = _runPearlsEarned.asStateFlow()

    private val _reviveAvailable = MutableStateFlow(true)
    val reviveAvailable: StateFlow<Boolean> = _reviveAvailable.asStateFlow()

    // --- Achievements ---
    private val _unlockedAchievements = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievements: StateFlow<Set<String>> = _unlockedAchievements.asStateFlow()

    // Trophies earned during the current run, shown on the game-over screen
    private val _newAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val newAchievements: StateFlow<List<Achievement>> = _newAchievements.asStateFlow()

    private var totalGames = 0
    private var totalPearlsCollected = 0
    private var gamesCountedThisRun = false
    private var pearlsStatCounted = 0

    // Per-run stats feeding the daily mission checks
    private var runPearls = 0
    private var runMaxCombo = 0
    private var runPowerUps = 0
    private var runShieldPicked = false
    private var pearlsBankedThisRun = 0

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _particles = MutableStateFlow<List<StarParticle>>(emptyList())
    val particles: StateFlow<List<StarParticle>> = _particles.asStateFlow()

    private val _playerName = MutableStateFlow("Explorateur")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _selectedSkin = MutableStateFlow(OctopusSkin.CLASSIC)
    val selectedSkin: StateFlow<OctopusSkin> = _selectedSkin.asStateFlow()

    private val _selectedAccessory = MutableStateFlow(OctopusAccessory.NONE)
    val selectedAccessory: StateFlow<OctopusAccessory> = _selectedAccessory.asStateFlow()

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
        // Restore progression (daily mission status, unlocked levels, pearl economy)
        _missionCompletedToday.value = settings.missionLastCompletedDay == dayKey()
        _missionsCompletedCount.value = settings.missionsCompletedCount
        _maxUnlockedLevel.value = settings.maxUnlockedLevel
        _pearlWallet.value = settings.pearlBalance
        _purchasedCosmetics.value = settings.purchasedCosmetics
        _unlockedAchievements.value = settings.unlockedAchievements
        totalGames = settings.totalGames
        totalPearlsCollected = settings.totalPearlsCollected

        // Restore persisted cosmetic choices and player name
        settings.skinName?.let { saved ->
            OctopusSkin.values().firstOrNull { it.name == saved }?.let { _selectedSkin.value = it }
        }
        settings.accessoryName?.let { saved ->
            OctopusAccessory.values().firstOrNull { it.name == saved }?.let { _selectedAccessory.value = it }
        }
        settings.playerName?.let { _playerName.value = it }

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

        // Ambience ticker: keeps the wave clock, bubbles and background life moving
        // in the menu and game-over screens (the game loop takes over while PLAYING).
        viewModelScope.launch {
            var last = System.nanoTime()
            while (true) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.04f)
                last = now
                if (_gameScreenState.value != GameScreenState.PLAYING) {
                    _waveTime.value = (_waveTime.value + dt) % (Math.PI.toFloat() * 2f)
                    updateParticlesAndBubbles(dt, 40f)
                }
                delay(33)
            }
        }
    }

    fun setPlayerName(name: String) {
        if (name.length <= 12) {
            _playerName.value = name
            settings.playerName = name
        }
    }

    /**
     * Cosmetics unlock with the best score, except shop items (price > 0) which
     * must be bought with pearls, and the pirate patch (daily-mission reward).
     */
    fun isSkinUnlocked(skin: OctopusSkin): Boolean =
        if (skin.price > 0) skin.name in _purchasedCosmetics.value
        else (highestScore.value ?: 0) >= skin.requiredScore

    fun isAccessoryUnlocked(accessory: OctopusAccessory): Boolean = when {
        accessory.missionReward -> _missionsCompletedCount.value > 0
        accessory.price > 0 -> accessory.name in _purchasedCosmetics.value
        else -> (highestScore.value ?: 0) >= accessory.requiredScore
    }

    private fun purchase(price: Int, enumName: String): Boolean {
        if (price <= 0 || enumName in _purchasedCosmetics.value) return false
        if (_pearlWallet.value < price) return false
        _pearlWallet.value -= price
        _purchasedCosmetics.value = _purchasedCosmetics.value + enumName
        settings.pearlBalance = _pearlWallet.value
        settings.purchasedCosmetics = _purchasedCosmetics.value
        return true
    }

    fun buySkin(skin: OctopusSkin) {
        if (purchase(skin.price, skin.name)) setSkin(skin) // auto-equip on purchase
    }

    fun buyAccessory(accessory: OctopusAccessory) {
        if (purchase(accessory.price, accessory.name)) setAccessory(accessory)
    }

    fun setSkin(skin: OctopusSkin) {
        if (!isSkinUnlocked(skin)) return
        _selectedSkin.value = skin
        settings.skinName = skin.name
    }

    fun setAccessory(accessory: OctopusAccessory) {
        if (!isAccessoryUnlocked(accessory)) return
        _selectedAccessory.value = accessory
        settings.accessoryName = accessory.name
    }

    fun selectMode(mode: GameMode) {
        _gameMode.value = mode
    }

    fun selectLevel(level: Int) {
        if (level in 1.._maxUnlockedLevel.value) _currentLevel.value = level
    }

    /** Score to reach to clear the given level in "Niveaux" mode. */
    fun levelTarget(level: Int = _currentLevel.value): Int = level * 10

    /** From the level-complete screen: jump straight into the next level. */
    fun startNextLevel() {
        val next = (_currentLevel.value + 1).coerceAtMost(MAX_LEVEL)
        if (next <= _maxUnlockedLevel.value) {
            _currentLevel.value = next
            startGame()
        }
    }

    fun swim() {
        if (_gameScreenState.value != GameScreenState.PLAYING) return
        
        // Buoyancy lift force impulse (underwater version of jump!)
        velocityY = -360f
        _velocityY.value = velocityY
        
        SoundManager.playSwim() // Play low-latency bubble bloop!
        
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
        _powerUps.value = emptyList()
        _particles.value = emptyList()
        _jellyfish.value = emptyList()
        _currents.value = emptyList()
        _shieldCharges.value = 0
        _magnetTime.value = 0f
        _slowMoTime.value = 0f
        _combo.value = 0
        invulnTime = 0f
        _invuln.value = false
        _newRecord.value = false
        _levelCompleted.value = false
        _missionJustCompleted.value = false
        runPearls = 0
        runMaxCombo = 0
        runPowerUps = 0
        runShieldPicked = false
        pearlsBankedThisRun = 0
        _runPearlsEarned.value = 0
        _reviveAvailable.value = true
        _newAchievements.value = emptyList()
        gamesCountedThisRun = false
        pearlsStatCounted = 0
        _gameScreenState.value = GameScreenState.PLAYING

        launchGameLoop()
    }

    private fun launchGameLoop() {
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
                val currentInterval = (2.8f - (_score.value * 0.012f)).coerceAtLeast(1.7f)
                if (obstacleSpawnAccumulator >= currentInterval) {
                    spawnObstacle()
                    obstacleSpawnAccumulator = 0f
                }

                delay(12) // higher precision animation tick (~80fps loop capped, smooth)
            }
        }
    }

    /**
     * Second chance: spend pearls to resume the run right where it crashed.
     * Hazards are swept away and a short invulnerability lets the player breathe.
     */
    fun revive() {
        if (_gameScreenState.value != GameScreenState.GAME_OVER) return
        if (_levelCompleted.value || !_reviveAvailable.value) return
        if (_pearlWallet.value < REVIVE_COST) return

        _pearlWallet.value -= REVIVE_COST
        settings.pearlBalance = _pearlWallet.value
        _reviveAvailable.value = false

        _obstacles.value = emptyList()
        _jellyfish.value = emptyList()
        _currents.value = emptyList()
        _octopusY.value = 500f
        velocityY = -100f
        _velocityY.value = velocityY
        invulnTime = 2f
        _invuln.value = true
        // Allow the (higher) final score of the resumed run to be recorded too
        _highScoreSaved.value = false
        _gameScreenState.value = GameScreenState.PLAYING
        launchGameLoop()
    }

    private fun updateGame(dt: Float) {
        // 0. Tick down active effect timers
        if (invulnTime > 0f) {
            invulnTime = (invulnTime - dt).coerceAtLeast(0f)
            _invuln.value = invulnTime > 0f
        }
        if (_magnetTime.value > 0f) _magnetTime.value = (_magnetTime.value - dt).coerceAtLeast(0f)
        if (_slowMoTime.value > 0f) _slowMoTime.value = (_slowMoTime.value - dt).coerceAtLeast(0f)

        // 1. Apply gravity (buoyancy is weaker than full air gravity)
        velocityY += 900f * dt

        // Marine currents push the octopus vertically while it sits inside the zone
        for (cur in _currents.value) {
            if (220f in cur.x..(cur.x + cur.width)) {
                velocityY += cur.force * dt
            }
        }
        _velocityY.value = velocityY
        val nextOctopusY = _octopusY.value + velocityY * dt
        _octopusY.value = nextOctopusY

        // 2. Bound check (crash when hitting surface or bottom depths)
        if (nextOctopusY - OCTOPUS_RADIUS < 0f || nextOctopusY + OCTOPUS_RADIUS > 1000f) {
            if (survivesCrash()) {
                // Shield/invulnerability: clamp back into the play area instead of dying
                _octopusY.value = nextOctopusY.coerceIn(OCTOPUS_RADIUS, 1000f - OCTOPUS_RADIUS)
                velocityY = 0f
                _velocityY.value = 0f
            } else {
                endGame()
                return
            }
        }

        // 3. Move obstacles & detect collisions
        val speedMultiplier = (1.0f + (_score.value * 0.012f)).coerceAtMost(1.45f)
        // Slow-motion power-up reduces scroll speed while active
        val currentSpeed = 205f * speedMultiplier * (if (_slowMoTime.value > 0f) 0.55f else 1f)

        val currentObstacles = _obstacles.value
        val nextObstacles = mutableListOf<CoralObstacle>()
        for (obs in currentObstacles) {
            val nextX = obs.x - currentSpeed * dt
            if (nextX + obs.width < 0f) {
                continue // past left side
            }

            // Animate dynamic vertical shifting (bobbing seaweed-coral columns)
            var currentGapY = obs.gapY
            if (obs.bobRange > 0f) {
                currentGapY = obs.initialGapY + kotlin.math.sin(_waveTime.value * obs.bobSpeed) * obs.bobRange
            }

            var isPassed = obs.passed
            if (!isPassed && nextX + obs.width / 2f <= 220f) {
                isPassed = true
                _score.value += 1
                SoundManager.playCollect() // Play sweet point collection sound effect
                spawnWavedBubbles(nextX + obs.width / 2f, currentGapY, count = 4)
            }

            // Precisely check circle-to-rectangle collisions with both coral columns
            val topCrash = checkCircleRectCollision(
                circleX = 220f, circleY = _octopusY.value, radius = OCTOPUS_RADIUS,
                rectX1 = nextX, rectY1 = 0f, rectX2 = nextX + obs.width, rectY2 = currentGapY - obs.gapHeight / 2f
            )
            val bottomCrash = checkCircleRectCollision(
                circleX = 220f, circleY = _octopusY.value, radius = OCTOPUS_RADIUS,
                rectX1 = nextX, rectY1 = currentGapY + obs.gapHeight / 2f, rectX2 = nextX + obs.width, rectY2 = 1000f
            )

            if (topCrash || bottomCrash) {
                if (!survivesCrash()) {
                    endGame()
                    return
                }
                // Shielded/invulnerable: swim straight through the coral
            }

            nextObstacles.add(obs.copy(x = nextX, gapY = currentGapY, passed = isPassed))
        }
        _obstacles.value = nextObstacles

        // 3b. Move jellyfish (vertical oscillation) & detect collisions
        val nextJellyfish = mutableListOf<Jellyfish>()
        for (jelly in _jellyfish.value) {
            val nextX = jelly.x - currentSpeed * dt
            if (nextX + jelly.radius < 0f) continue

            val nextY = jelly.baseY + kotlin.math.sin(_waveTime.value * jelly.speed + jelly.phase) * jelly.amplitude
            val dx = 220f - nextX
            val dy = _octopusY.value - nextY
            if (dx * dx + dy * dy < (OCTOPUS_RADIUS + jelly.radius) * (OCTOPUS_RADIUS + jelly.radius)) {
                if (!survivesCrash()) {
                    endGame()
                    return
                }
            }
            nextJellyfish.add(jelly.copy(x = nextX, y = nextY))
        }
        _jellyfish.value = nextJellyfish

        // 3c. Scroll marine current zones
        _currents.value = _currents.value
            .map { it.copy(x = it.x - currentSpeed * dt) }
            .filter { it.x + it.width > 0f }

        // 4. Move pearls and handle collection
        val magnetActive = _magnetTime.value > 0f
        val currentPearls = _pearls.value
        val nextPearls = mutableListOf<Pearl>()
        for (pearl in currentPearls) {
            var nextX = pearl.x - currentSpeed * dt
            var nextY = pearl.y

            // Magnet power-up: nearby pearls are pulled toward the octopus
            if (magnetActive) {
                val dx = 220f - nextX
                val dy = _octopusY.value - nextY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist in 1f..400f) {
                    val pull = 700f * dt
                    nextX += (dx / dist) * pull
                    nextY += (dy / dist) * pull
                }
            }

            if (nextX + 40f < 0f) {
                // Pearl escaped off-screen: the combo chain is broken
                _combo.value = 0
                continue
            }

            // Check collision with octopus
            val distSq = (220f - nextX) * (220f - nextX) + (_octopusY.value - nextY) * (_octopusY.value - nextY)
            if (distSq < (30f + 16f) * (30f + 16f)) {
                // Build the combo and award 5 points times the multiplier
                _combo.value += 1
                _score.value += 5 * _combo.value
                runPearls += 1
                runMaxCombo = maxOf(runMaxCombo, _combo.value)
                SoundManager.playCollect() // Play special collect chime!
                triggerPearlExplosion(nextX, nextY)
                continue
            }

            nextPearls.add(pearl.copy(x = nextX, y = nextY))
        }
        _pearls.value = nextPearls

        // 5. Move power-ups and handle collection
        val currentPowerUps = _powerUps.value
        val nextPowerUps = mutableListOf<PowerUp>()
        for (pu in currentPowerUps) {
            val nextX = pu.x - currentSpeed * dt
            if (nextX + pu.radius < 0f) continue

            val distSq = (220f - nextX) * (220f - nextX) + (_octopusY.value - pu.y) * (_octopusY.value - pu.y)
            if (distSq < (30f + pu.radius) * (30f + pu.radius)) {
                activatePowerUp(pu.type)
                SoundManager.playCollect()
                triggerPowerUpBurst(nextX, pu.y, pu.type)
                continue
            }
            nextPowerUps.add(pu.copy(x = nextX))
        }
        _powerUps.value = nextPowerUps

        // 6. Update active decorative systems (particles & bubbles)
        updateParticlesAndBubbles(dt, currentSpeed)

        // 7. Levels mode: reaching the target score clears the level
        if (_gameMode.value == GameMode.LEVELS && _score.value >= levelTarget()) {
            completeLevel()
        }
    }

    private fun completeLevel() {
        _levelCompleted.value = true
        if (_currentLevel.value >= _maxUnlockedLevel.value && _currentLevel.value < MAX_LEVEL) {
            _maxUnlockedLevel.value = _currentLevel.value + 1
            settings.maxUnlockedLevel = _maxUnlockedLevel.value
        }
        _gameScreenState.value = GameScreenState.GAME_OVER
        gameJob?.cancel()
        SoundManager.playCollect()
        finishRun()
    }

    /** Returns true if the octopus survives a crash (invulnerable or has a shield). */
    private fun survivesCrash(): Boolean {
        if (invulnTime > 0f) return true
        if (_shieldCharges.value > 0) {
            _shieldCharges.value -= 1
            invulnTime = SHIELD_INVULN
            _invuln.value = true
            SoundManager.playCollect()
            triggerShieldBreak(220f, _octopusY.value)
            return true
        }
        return false
    }

    private fun activatePowerUp(type: PowerUpType) {
        runPowerUps += 1
        when (type) {
            PowerUpType.SHIELD -> {
                runShieldPicked = true
                _shieldCharges.value = (_shieldCharges.value + 1).coerceAtMost(1)
            }
            PowerUpType.MAGNET -> _magnetTime.value = MAGNET_DURATION
            PowerUpType.SLOWMO -> _slowMoTime.value = SLOWMO_DURATION
        }
    }

    private fun spawnObstacle() {
        val gapY = Random.nextFloat() * 480f + 260f // center centered between 260 and 740
        val defaultGapHeight = 310f
        // Reduce gap size slightly over time for difficulty tuning
        val currentGapHeight = (defaultGapHeight - (_score.value * 1.5f)).coerceAtLeast(235f)

        // Escalate difficulty: introduce moving/bobbing corals as the score increases!
        val bobRange = if (_score.value >= 10) {
            (Random.nextFloat() * (_score.value * 1.8f)).coerceAtMost(60f) // Up to 60px bobbing oscillation
        } else {
            0f
        }
        val bobSpeed = if (bobRange > 0f) {
            Random.nextFloat() * 1.1f + 0.6f
        } else {
            0f
        }

        val newObstacle = CoralObstacle(
            x = 1100f,
            gapY = gapY,
            gapHeight = currentGapHeight,
            width = 115f,
            bobSpeed = bobSpeed,
            bobRange = bobRange,
            initialGapY = gapY
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

        // Occasionally drop a power-up inside the gap (rarer than pearls)
        if (Random.nextFloat() < 0.20f) {
            val type = PowerUpType.values().random()
            val puY = gapY + Random.nextFloat() * 60f - 30f
            _powerUps.value = _powerUps.value + PowerUp(
                x = 1100f + 57.5f,
                y = puY,
                type = type
            )
        }

        // Depth hazards appear between coral columns as the dive gets deeper.
        // Jellyfish (score >= 8): oscillating creature halfway to the next column.
        if (_score.value >= 8 && Random.nextFloat() < 0.30f) {
            _jellyfish.value = _jellyfish.value + Jellyfish(
                x = 1100f + 290f,
                baseY = Random.nextFloat() * 500f + 250f,
                amplitude = Random.nextFloat() * 100f + 70f,
                speed = Random.nextFloat() * 1.6f + 1.4f,
                phase = Random.nextFloat() * 6.28f
            )
        }

        // Marine currents (score >= 20): a zone pushing the octopus up or down.
        if (_score.value >= 20 && Random.nextFloat() < 0.22f) {
            val goesUp = Random.nextBoolean()
            _currents.value = _currents.value + MarineCurrent(
                x = 1100f + 230f,
                force = if (goesUp) -340f else 340f
            )
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
            // Compare against the best score BEFORE inserting to detect a new record
            val previousBest = repository.highestScore.first() ?: 0
            _newRecord.value = finalScore > previousBest && finalScore > 0
            repository.insertScore(HighScore(playerName = name, score = finalScore))
            _highScoreSaved.value = true
        }
    }

    /**
     * Converts the run into pearl currency: collected pearls + medal, mission and
     * level bonuses. Revives make this run twice through here, so only the delta
     * since the previous banking is credited.
     */
    private fun bankRunPearls() {
        val medalBonus = when {
            _score.value >= 50 -> 50
            _score.value >= 25 -> 25
            _score.value >= 10 -> 10
            else -> 0
        }
        val missionBonus = if (_missionJustCompleted.value) MISSION_PEARL_BONUS else 0
        val levelBonus = if (_levelCompleted.value) LEVEL_PEARL_BONUS else 0
        val earned = runPearls + medalBonus + missionBonus + levelBonus

        val delta = earned - pearlsBankedThisRun
        if (delta > 0) {
            _pearlWallet.value += delta
            settings.pearlBalance = _pearlWallet.value
            pearlsBankedThisRun = earned
        }
        _runPearlsEarned.value = earned
    }

    /** Checks the run's stats against today's mission and persists completion. */
    private fun evaluateDailyMission() {
        if (_missionCompletedToday.value) return
        val m = dailyMission
        val done = when (m.kind) {
            MissionKind.PEARLS -> runPearls >= m.target
            MissionKind.SCORE -> _score.value >= m.target
            MissionKind.NO_SHIELD -> _score.value >= m.target && !runShieldPicked
            MissionKind.COMBO -> runMaxCombo >= m.target
            MissionKind.POWERUPS -> runPowerUps >= m.target
        }
        if (done) {
            _missionCompletedToday.value = true
            _missionJustCompleted.value = true
            _missionsCompletedCount.value += 1
            settings.missionLastCompletedDay = dayKey()
            settings.missionsCompletedCount = _missionsCompletedCount.value
        }
    }

    private fun endGame() {
        _gameScreenState.value = GameScreenState.GAME_OVER
        gameJob?.cancel()
        SoundManager.playCrash() // Play impact crash synthesizer!
        finishRun()

        // Screen shake of particles on impact
        triggerCrashExplosion(220f, _octopusY.value)
    }

    /** Shared end-of-run pipeline: missions, lifetime stats, trophies, pearls, score. */
    private fun finishRun() {
        evaluateDailyMission()
        updateLifetimeStats()
        evaluateAchievements()
        bankRunPearls()
        saveHighScore()
    }

    private fun updateLifetimeStats() {
        // A revived run passes through here twice; count it once.
        if (!gamesCountedThisRun) {
            gamesCountedThisRun = true
            totalGames += 1
            settings.totalGames = totalGames
        }
        val newPearls = runPearls - pearlsStatCounted
        if (newPearls > 0) {
            totalPearlsCollected += newPearls
            pearlsStatCounted = runPearls
            settings.totalPearlsCollected = totalPearlsCollected
        }
    }

    /** Unlocks every newly satisfied achievement and credits its pearl reward. */
    private fun evaluateAchievements() {
        val unlocked = _unlockedAchievements.value
        val newly = ALL_ACHIEVEMENTS.filter { a ->
            a.id !in unlocked && when (a.id) {
                "first_dive" -> true
                "ten_games" -> totalGames >= 10
                "fifty_games" -> totalGames >= 50
                "score_30" -> _score.value >= 30
                "score_60" -> _score.value >= 60
                "combo_5" -> runMaxCombo >= 5
                "pearls_500" -> totalPearlsCollected >= 500
                "level_5" -> _levelCompleted.value && _currentLevel.value >= 5
                "level_10" -> _levelCompleted.value && _currentLevel.value >= 10
                else -> false
            }
        }
        if (newly.isNotEmpty()) {
            _unlockedAchievements.value = unlocked + newly.map { it.id }
            settings.unlockedAchievements = _unlockedAchievements.value
            _pearlWallet.value += newly.sumOf { it.reward }
            settings.pearlBalance = _pearlWallet.value
            _newAchievements.value = _newAchievements.value + newly
        }
    }

    fun pauseGame() {
        if (_gameScreenState.value != GameScreenState.PLAYING) return
        _gameScreenState.value = GameScreenState.PAUSED
        gameJob?.cancel()
    }

    fun resumeGame() {
        if (_gameScreenState.value != GameScreenState.PAUSED) return
        _gameScreenState.value = GameScreenState.PLAYING
        launchGameLoop()
    }

    /** Abandon a paused run: the score and pearls earned so far still count. */
    fun quitRun() {
        if (_gameScreenState.value != GameScreenState.PAUSED) return
        _gameScreenState.value = GameScreenState.GAME_OVER
        gameJob?.cancel()
        finishRun()
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

    private fun triggerShieldBreak(x: Float, y: Float) {
        val list = List(26) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = Random.nextFloat() * 320f + 60f
            StarParticle(
                x = x,
                y = y,
                tx = Math.cos(angle.toDouble()).toFloat() * speed,
                ty = Math.sin(angle.toDouble()).toFloat() * speed,
                color = Color(0xFF00E5FF), // Cyan shield shards
                life = 1f
            )
        }
        _particles.value = _particles.value + list
    }

    private fun triggerPowerUpBurst(x: Float, y: Float, type: PowerUpType) {
        val burstColor = when (type) {
            PowerUpType.SHIELD -> Color(0xFF00E5FF)
            PowerUpType.MAGNET -> Color(0xFFFF5252)
            PowerUpType.SLOWMO -> Color(0xFFA29BFE)
        }
        val list = List(18) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = Random.nextFloat() * 260f + 40f
            StarParticle(
                x = x,
                y = y,
                tx = Math.cos(angle.toDouble()).toFloat() * speed,
                ty = Math.sin(angle.toDouble()).toFloat() * speed,
                color = burstColor,
                life = 1f
            )
        }
        _particles.value = _particles.value + list
    }

    fun backToMenu() {
        _gameScreenState.value = GameScreenState.MENU
        _highScoreSaved.value = false
    }
}

/** Stable key for "today", used to rotate and gate the daily mission. */
private fun dayKey(): String {
    val cal = java.util.Calendar.getInstance()
    return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
}

class GameViewModelFactory(
    private val repository: HighScoreRepository,
    private val settings: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(repository, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
