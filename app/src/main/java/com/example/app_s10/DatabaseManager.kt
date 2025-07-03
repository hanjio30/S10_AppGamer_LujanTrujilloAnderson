package com.example.app_s10

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await

class DatabaseManager private constructor() {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef: DatabaseReference = database.getReference("users")
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: DatabaseManager? = null
        private const val TAG = "DatabaseManager"

        fun getInstance(): DatabaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseManager().also { INSTANCE = it }
            }
        }
    }

    init {
        // Habilitar persistencia offline
        try {
            database.setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w(TAG, "Persistencia ya habilitada", e)
        }
    }

    // Crear o actualizar usuario en la base de datos
    suspend fun createOrUpdateUser(firebaseUser: FirebaseUser): Result<User> {
        return try {
            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "Usuario",
                isEmailVerified = firebaseUser.isEmailVerified,
                isAnonymous = firebaseUser.isAnonymous,
                lastLogin = System.currentTimeMillis()
            )

            // Verificar si el usuario ya existe
            val existingUser = getUserById(firebaseUser.uid)
            if (existingUser.isSuccess) {
                // Usuario existe, solo actualizar algunos campos
                val updatedUser = existingUser.getOrNull()?.copy(
                    email = user.email,
                    displayName = user.displayName,
                    isEmailVerified = user.isEmailVerified,
                    lastLogin = user.lastLogin
                ) ?: user

                usersRef.child(firebaseUser.uid).setValue(updatedUser).await()
                Result.success(updatedUser)
            } else {
                // Usuario nuevo, crear completamente
                usersRef.child(firebaseUser.uid).setValue(user).await()
                Result.success(user)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear/actualizar usuario", e)
            Result.failure(e)
        }
    }

    // Obtener usuario por ID
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            if (snapshot.exists()) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Error al deserializar usuario"))
                }
            } else {
                Result.failure(Exception("Usuario no encontrado"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener usuario", e)
            Result.failure(e)
        }
    }

    // Obtener usuario actual
    suspend fun getCurrentUser(): Result<User> {
        val currentUser = auth.currentUser
        return if (currentUser != null) {
            getUserById(currentUser.uid)
        } else {
            Result.failure(Exception("No hay usuario autenticado"))
        }
    }

    // Actualizar estadísticas del usuario
    suspend fun updateUserStats(userId: String, stats: UserStats): Result<Unit> {
        return try {
            usersRef.child(userId).child("stats").setValue(stats).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar estadísticas", e)
            Result.failure(e)
        }
    }

    // Actualizar nivel y experiencia
    suspend fun updateUserLevel(userId: String, level: Int, experience: Int): Result<Unit> {
        return try {
            val updates = mapOf(
                "level" to level,
                "experience" to experience
            )
            usersRef.child(userId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar nivel", e)
            Result.failure(e)
        }
    }

    // Agregar logro
    suspend fun addAchievement(userId: String, achievement: String): Result<Unit> {
        return try {
            val user = getUserById(userId).getOrNull()
            if (user != null) {
                val newAchievements = user.achievements.toMutableList()
                if (!newAchievements.contains(achievement)) {
                    newAchievements.add(achievement)
                    usersRef.child(userId).child("achievements").setValue(newAchievements).await()
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Usuario no encontrado"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al agregar logro", e)
            Result.failure(e)
        }
    }

    // Obtener top usuarios por puntuación
    suspend fun getTopUsersByScore(limit: Int = 10): Result<List<User>> {
        return try {
            val snapshot = usersRef
                .orderByChild("stats/highScore")
                .limitToLast(limit)
                .get()
                .await()

            val users = mutableListOf<User>()
            snapshot.children.forEach { child ->
                child.getValue(User::class.java)?.let { users.add(it) }
            }

            // Ordenar de mayor a menor puntuación
            users.sortByDescending { it.stats.highScore }
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener top usuarios", e)
            Result.failure(e)
        }
    }

    // Listener para cambios en tiempo real del usuario actual
    fun addUserListener(userId: String, listener: (User?) -> Unit): DatabaseReference {
        val userRef = usersRef.child(userId)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                listener(user)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error en listener de usuario", error.toException())
                listener(null)
            }
        })
        return userRef
    }

    // Eliminar usuario (para casos especiales)
    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            usersRef.child(userId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar usuario", e)
            Result.failure(e)
        }
    }
}