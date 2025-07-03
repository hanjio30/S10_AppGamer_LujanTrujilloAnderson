package com.example.app_s10

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val isEmailVerified: Boolean = false,
    val isAnonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val profileImageUrl: String = "",
    // Datos específicos de la app
    val level: Int = 1,
    val experience: Int = 0,
    val achievements: List<String> = emptyList(),
    val stats: UserStats = UserStats()
) {
    // Constructor vacío requerido para Firebase
    constructor() : this("", "", "", false, false, 0L, 0L, "", 1, 0, emptyList(), UserStats())
}

data class UserStats(
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val totalScore: Long = 0L,
    val highScore: Long = 0L,
    val playtime: Long = 0L, // en millisegundos
    val favoriteCategory: String = ""
) {
    constructor() : this(0, 0, 0, 0L, 0L, 0L, "")
}