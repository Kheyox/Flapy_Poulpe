package com.example.data

import kotlinx.coroutines.flow.Flow

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScore>> = highScoreDao.getTopScores()
    val highestScore: Flow<Int?> = highScoreDao.getHighestScore()

    suspend fun insertScore(score: HighScore) {
        highScoreDao.insertScore(score)
    }
}
