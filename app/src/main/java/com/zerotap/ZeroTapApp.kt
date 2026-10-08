package com.zerotap

import android.app.Application
import android.content.Context
import com.zerotap.ai.byok.BYOKAIProvider
import com.zerotap.ai.local.LocalAIProvider
import com.zerotap.ai.server.ServerAIProvider
import com.zerotap.ai.service.AIService
import com.zerotap.ai.service.AIServiceImpl
import com.zerotap.core.security.SecureCredentialStore
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.repository.*
import com.zerotap.domain.repository.*

object ServiceLocator {
    lateinit var appContext: Context
        private set

    val database: ZeroTapDatabase by lazy {
        ZeroTapDatabase.getInstance(appContext)
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(appContext)
    }

    val secureCredentialStore: SecureCredentialStore by lazy {
        SecureCredentialStore(appContext)
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(appContext)
    }

    val safeSpaceRepository: SafeSpaceRepository by lazy {
        SafeSpaceRepositoryImpl(appContext, database.safeSpaceDao())
    }

    val trustedPlaceRepository: TrustedPlaceRepository by lazy {
        TrustedPlaceRepositoryImpl(database.trustedPlaceDao())
    }

    val navigationRepository: NavigationRepository by lazy {
        NavigationRepositoryImpl()
    }

    val mapRepository: MapRepository by lazy {
        MapRepositoryImpl(appContext)
    }

    val evidenceRepository: EvidenceRepository by lazy {
        EvidenceRepositoryImpl(database.evidenceMetadataDao(), database.evidenceItemDao())
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(evidenceRepository)
    }

    val vehiclePlateAnalyzer: com.zerotap.domain.vision.VehiclePlateAnalyzer by lazy {
        com.zerotap.domain.vision.OnDeviceMLKitPlateAnalyzer()
    }

    val localAiProvider: LocalAIProvider by lazy {
        LocalAIProvider()
    }

    val byokAiProvider: BYOKAIProvider by lazy {
        BYOKAIProvider(secureCredentialStore, localAiProvider)
    }

    val serverAiProvider: ServerAIProvider by lazy {
        ServerAIProvider(localAiProvider)
    }

    val aiService: AIService by lazy {
        AIServiceImpl(localAiProvider, byokAiProvider, serverAiProvider)
    }

    val apiClient: com.zerotap.data.remote.api.ZeroTapApiClient by lazy {
        com.zerotap.data.remote.api.ZeroTapApiClient()
    }

    val webSocketClient: com.zerotap.data.remote.websocket.ZeroTapWebSocketClient by lazy {
        com.zerotap.data.remote.websocket.ZeroTapWebSocketClient()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}

class ZeroTapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
        com.zerotap.service.EmergencyContactSyncManager.start(this)
    }
}
