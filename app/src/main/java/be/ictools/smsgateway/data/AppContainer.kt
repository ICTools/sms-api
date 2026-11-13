package be.ictools.smsgateway.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import be.ictools.smsgateway.BuildConfig
import be.ictools.smsgateway.data.api.GatewayApi
import be.ictools.smsgateway.data.auth.AuthRepository
import be.ictools.smsgateway.data.auth.SessionStore
import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import be.ictools.smsgateway.data.local.GatewayDatabase
import be.ictools.smsgateway.data.repository.MessageRepository
import be.ictools.smsgateway.data.sse.MercureSseClient
import be.ictools.smsgateway.sms.DeviceSmsGateway
import be.ictools.smsgateway.worker.SyncScheduler
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
    val heartbeatReporter = HeartbeatReporter(appContext, api, sessionStore)
    val syncScheduler = SyncScheduler(WorkManager.getInstance(appContext))
}
