package com.vocis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.vocis.core.data.database.AppDatabase
import com.vocis.core.data.database.VcdDatabase
import com.vocis.digitalarrest.DigitalArrestController
import com.vocis.intelligence.context.AttackContextEngine
import com.vocis.intelligence.hub.InteractionHub
import com.vocis.intelligence.identity.CallerIdentityResolver
import com.vocis.speech.asr.SpeechRecognizerBridge
import com.vocis.ui.overlay.EmergencyAlertOverlayManager
import com.vocis.ui.overlay.ProtectionOverlayManager
import com.vocis.vcd.crypto.BiometricCryptoVault
import com.vocis.vcd.crypto.KeystoreBiometricCryptoVault
import com.vocis.vcd.inference.AntiSpoofDetectorModel
import com.vocis.vcd.inference.AssetModelLoader
import com.vocis.vcd.inference.SpeakerEncoderModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VocisApplication : Application() {

    lateinit var appDatabase: AppDatabase
        private set
    lateinit var vcdDatabase: VcdDatabase
        private set

    lateinit var callerIdentityResolver: CallerIdentityResolver
        private set
    lateinit var attackContextEngine: AttackContextEngine
        private set
    lateinit var interactionHub: InteractionHub
        private set
    lateinit var digitalArrestController: DigitalArrestController
        private set
    lateinit var protectionOverlayManager: ProtectionOverlayManager
        private set
    lateinit var emergencyAlertOverlayManager: EmergencyAlertOverlayManager
        private set

    // Hardware Keystore Biometric Vault
    val biometricCryptoVault: BiometricCryptoVault = KeystoreBiometricCryptoVault()

    // Neural Model and Speech Recognizer instances
    var speakerEncoder: SpeakerEncoderModel? = null
        private set
    var antiSpoofDetector: AntiSpoofDetectorModel? = null
        private set
    var speechRecognizer: SpeechRecognizerBridge? = null
        private set
    var liveCallCoordinator: com.vocis.speech.LiveCallIntelligenceCoordinator? = null
        private set
    var isModelLoaded: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize dual databases
        appDatabase = AppDatabase.getInstance(this)
        vcdDatabase = VcdDatabase.getInstance(this)

        // 2. Initialize intelligence and security components
        callerIdentityResolver = CallerIdentityResolver(
            context = this,
            dao = appDatabase.callerIdentityDao()
        )
        attackContextEngine = AttackContextEngine(
            dao = appDatabase.attackContextDao()
        )
        interactionHub = InteractionHub(
            db = appDatabase,
            identityResolver = callerIdentityResolver,
            contextEngine = attackContextEngine
        )
        InteractionHub.setInstance(interactionHub)
        digitalArrestController = DigitalArrestController()
        protectionOverlayManager = ProtectionOverlayManager(this)
        emergencyAlertOverlayManager = EmergencyAlertOverlayManager(this)

        // 3. Register system notification channels
        createNotificationChannels()

        // 4. Start background reachability & mDNS peer discovery service
        com.vocis.webrtc.service.AvailabilityService.start(this)

        // 5. Preload on-device AI/ML models in background thread
        preloadModelsAsync()
    }

    private fun preloadModelsAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("VocisApp", "Starting background neural model initialization...")
                val loaded = AssetModelLoader.loadAllModels(this@VocisApplication)
                speakerEncoder = loaded.speakerEncoder
                antiSpoofDetector = loaded.antiSpoofDetector
                speechRecognizer = loaded.speechRecognizer
                isModelLoaded = true
                if (speechRecognizer != null) {
                    val semanticAnalyzer = com.vocis.speech.llm.LiveSemanticAnalyzer()
                    liveCallCoordinator = com.vocis.speech.LiveCallIntelligenceCoordinator(
                        asrBridge = speechRecognizer!!,
                        semanticAnalyzer = semanticAnalyzer,
                        scope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob()),
                        attackContextEngine = attackContextEngine
                    )
                }
                Log.i("VocisApp", "Neural models and LiveCallIntelligenceCoordinator loaded successfully")
            } catch (e: Exception) {
                Log.w("VocisApp", "Model preloading note: ")
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val protectionChannel = NotificationChannel(
                CHANNEL_PROTECTION,
                "VOCIS Protection Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when real-time biometric and scam protection is active"
            }
            notificationManager.createNotificationChannel(protectionChannel)

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "VOCIS Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical acoustic and biometric scam emergency alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    companion object {
        const val CHANNEL_PROTECTION = "vocis_channel_protection"
        const val CHANNEL_EMERGENCY = "vocis_channel_emergency"

        lateinit var instance: VocisApplication
            private set
    }
}


