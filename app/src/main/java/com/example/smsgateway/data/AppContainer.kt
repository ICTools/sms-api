package com.example.smsgateway.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.smsgateway.BuildConfig
import com.example.smsgateway.data.api.GatewayApi
import com.example.smsgateway.data.auth.AuthRepository
import com.example.smsgateway.data.auth.SessionStore
import com.example.smsgateway.data.local.GatewayDatabase
import com.example.smsgateway.data.repository.MessageRepository
import com.example.smsgateway.data.sse.MercureSseClient
import com.example.smsgateway.sms.DeviceSmsGateway
import com.example.smsgateway.worker.SyncScheduler
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionStore = SessionStore(appContext)

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    val database: GatewayDatabase = Room.databaseBuilder(
        appContext,
        GatewayDatabase::class.java,
        "sms-gateway.db",
    )
        .addMigrations(*GatewayDatabase.MIGRATIONS)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    val authRepository = AuthRepository(baseClient, sessionStore)
    val api = GatewayApi(baseClient, sessionStore, authRepository)
    val smsGateway = DeviceSmsGateway(appContext)
    val repository = MessageRepository(
        appContext = appContext,
        api = api,
        sessionStore = sessionStore,
        database = database,
        smsGateway = smsGateway,
    )
    val sseClient = MercureSseClient(
        context = appContext,
        okHttpClient = baseClient,
        sessionStore = sessionStore,
        authRepository = authRepository,
        repository = repository,
    )
    val syncScheduler = SyncScheduler(WorkManager.getInstance(appContext))
}
