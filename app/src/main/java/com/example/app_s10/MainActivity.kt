package com.example.app_s10

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseManager: DatabaseManager

    // Views
    private lateinit var tvWelcome: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserLevel: TextView
    private lateinit var tvUserExperience: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var cardStats: CardView
    private lateinit var cardAchievements: CardView
    private lateinit var cardProfile: CardView
    private lateinit var cardSettings: CardView

    // Usuario actual desde Database
    private var currentUserData: User? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializar Firebase Auth y Database
        auth = FirebaseAuth.getInstance()
        databaseManager = DatabaseManager.getInstance()

        // Verificar autenticación
        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToLogin()
            return
        }

        // Configurar UI
        setupUI()
        setupWindowInsets()

        // Cargar información del usuario desde Database
        loadUserFromDatabase(currentUser)

        // Configurar listeners
        setupClickListeners()

        Log.d(TAG, "MainActivity iniciado para usuario: ${currentUser.email}")
    }

    private fun setupUI() {
        // Inicializar views
        tvWelcome = findViewById(R.id.tv_welcome)
        tvUserEmail = findViewById(R.id.tv_user_email)
        tvUserLevel = findViewById(R.id.tv_user_level)
        tvUserExperience = findViewById(R.id.tv_user_experience)
        btnLogout = findViewById(R.id.btn_logout)
        cardStats = findViewById(R.id.card_stats)
        cardAchievements = findViewById(R.id.card_achievements)
        cardProfile = findViewById(R.id.card_profile)
        cardSettings = findViewById(R.id.card_settings)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadUserFromDatabase(firebaseUser: FirebaseUser) {
        lifecycleScope.launch {
            try {
                // Crear/actualizar usuario en Database si es necesario
                val createResult = databaseManager.createOrUpdateUser(firebaseUser)
                if (createResult.isFailure) {
                    Log.e(TAG, "Error al crear usuario en DB", createResult.exceptionOrNull())
                }

                // Obtener datos del usuario desde Database
                val result = databaseManager.getCurrentUser()
                if (result.isSuccess) {
                    currentUserData = result.getOrNull()
                    updateUIWithUserData()
                    setupUserListener(firebaseUser.uid)
                } else {
                    Log.e(TAG, "Error al cargar usuario desde DB", result.exceptionOrNull())
                    // Fallback a datos básicos de Firebase Auth
                    loadBasicUserInfo(firebaseUser)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en carga de usuario", e)
                loadBasicUserInfo(firebaseUser)
            }
        }
    }

    private fun setupUserListener(userId: String) {
        // Listener para cambios en tiempo real
        databaseManager.addUserListener(userId) { user ->
            if (user != null) {
                currentUserData = user
                runOnUiThread {
                    updateUIWithUserData()
                }
            }
        }
    }

    private fun updateUIWithUserData() {
        currentUserData?.let { user ->
            // Personalizar saludo
            val welcomeMessage = if (user.isAnonymous) {
                "¡Hola, Invitado!"
            } else {
                "¡Hola, ${user.displayName.ifEmpty { "Gamer" }}!"
            }

            tvWelcome.text = welcomeMessage

            // Mostrar email o indicar usuario anónimo
            tvUserEmail.text = if (user.isAnonymous) {
                "Usuario invitado"
            } else {
                user.email.ifEmpty { "Sin email" }
            }

            // Mostrar nivel y experiencia
            tvUserLevel.text = "Nivel: ${user.level}"
            tvUserExperience.text = "EXP: ${user.experience}"

            // Verificar estado de verificación de email
            if (!user.isAnonymous && user.email.isNotEmpty() && !user.isEmailVerified) {
                showEmailVerificationDialog()
            }
        }
    }

    private fun loadBasicUserInfo(user: FirebaseUser) {
        // Fallback cuando no se puede cargar desde Database
        val welcomeMessage = if (user.isAnonymous) {
            "¡Hola, Invitado!"
        } else {
            "¡Hola, ${user.displayName ?: "Gamer"}!"
        }

        tvWelcome.text = welcomeMessage
        tvUserEmail.text = if (user.isAnonymous) {
            "Usuario invitado"
        } else {
            user.email ?: "Sin email"
        }

        tvUserLevel.text = "Nivel: 1"
        tvUserExperience.text = "EXP: 0"

        if (!user.isAnonymous && user.email != null && !user.isEmailVerified) {
            showEmailVerificationDialog()
        }
    }

    private fun setupClickListeners() {
        // Botón logout
        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Cards de navegación
        cardStats.setOnClickListener {
            showUserStats()
        }

        cardAchievements.setOnClickListener {
            showUserAchievements()
        }

        cardProfile.setOnClickListener {
            showFeatureComingSoon("Perfil")
        }

        cardSettings.setOnClickListener {
            showFeatureComingSoon("Configuración")
        }
    }

    private fun showUserStats() {
        currentUserData?.let { user ->
            val stats = user.stats
            val statsMessage = """
                🎮 Partidas jugadas: ${stats.totalGames}
                🏆 Victorias: ${stats.totalWins}
                💔 Derrotas: ${stats.totalLosses}
                ⭐ Puntuación total: ${stats.totalScore}
                🔥 Puntuación máxima: ${stats.highScore}
                ⏱️ Tiempo jugado: ${formatPlaytime(stats.playtime)}
                ${if (stats.favoriteCategory.isNotEmpty()) "❤️ Categoría favorita: ${stats.favoriteCategory}" else ""}
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("📊 Tus Estadísticas")
                .setMessage(statsMessage)
                .setPositiveButton("OK", null)
                .setNeutralButton("Actualizar Stats") { _, _ ->
                    // Ejemplo de actualización de stats
                    updateStatsExample()
                }
                .show()
        } ?: showFeatureComingSoon("Estadísticas")
    }

    private fun showUserAchievements() {
        currentUserData?.let { user ->
            val achievements = user.achievements
            val message = if (achievements.isEmpty()) {
                "🏆 Aún no tienes logros.\n¡Sigue jugando para desbloquear algunos!"
            } else {
                "🏆 Tus Logros:\n\n" + achievements.joinToString("\n") { "• $it" }
            }

            AlertDialog.Builder(this)
                .setTitle("🎖️ Logros")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNeutralButton("Agregar Logro") { _, _ ->
                    // Ejemplo de agregar logro
                    addAchievementExample()
                }
                .show()
        } ?: showFeatureComingSoon("Logros")
    }

    private fun updateStatsExample() {
        lifecycleScope.launch {
            currentUserData?.let { user ->
                val newStats = user.stats.copy(
                    totalGames = user.stats.totalGames + 1,
                    totalWins = user.stats.totalWins + 1,
                    totalScore = user.stats.totalScore + 100,
                    highScore = maxOf(user.stats.highScore, user.stats.totalScore + 100),
                    playtime = user.stats.playtime + 300000 // 5 minutos
                )

                val result = databaseManager.updateUserStats(user.uid, newStats)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "¡Estadísticas actualizadas!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error al actualizar estadísticas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addAchievementExample() {
        lifecycleScope.launch {
            currentUserData?.let { user ->
                val newAchievement = "🌟 Primer logro desbloqueado"
                val result = databaseManager.addAchievement(user.uid, newAchievement)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "¡Nuevo logro desbloqueado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error al agregar logro", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatPlaytime(playtimeMs: Long): String {
        val hours = playtimeMs / (1000 * 60 * 60)
        val minutes = (playtimeMs % (1000 * 60 * 60)) / (1000 * 60)
        return "${hours}h ${minutes}m"
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Usuario desconectado")
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showEmailVerificationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verificar Email")
            .setMessage(getString(R.string.auth_email_verification_required))
            .setPositiveButton("Enviar verificación") { _, _ ->
                sendEmailVerification()
            }
            .setNegativeButton("Más tarde", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    private fun sendEmailVerification() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, getString(R.string.auth_verification_email_sent), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error al enviar verificación", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showFeatureComingSoon(featureName: String) {
        AlertDialog.Builder(this)
            .setTitle("Próximamente")
            .setMessage("La función '$featureName' será implementada en futuras versiones.")
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "Usuario no autenticado en onStart, redirigiendo...")
            redirectToLogin()
        }
    }
}