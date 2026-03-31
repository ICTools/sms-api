package com.example.smsgateway.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.smsgateway.data.api.GatewayApi
import com.example.smsgateway.data.auth.AuthRepository
import com.example.smsgateway.data.auth.SessionStore
import com.example.smsgateway.data.local.GatewayDatabase
import com.example.smsgateway.data.repository.MessageRepository
import com.example.smsgateway.data.sse.MercureSseClient
import com.example.smsgateway.sms.DeviceSmsGateway
import com.example.smsgateway.worker.SyncScheduler
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionStore = SessionStore(appContext)

    private val baseClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val database: GatewayDatabase = Room.databaseBuilder(
        appContext,
        GatewayDatabase::class.java,
        "sms-gateway.db",
    ).fallbackToDestructiveMigration().build()

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
        repository = repository,
    )
    val syncScheduler = SyncScheduler(WorkManager.getInstance(appContext))
}
