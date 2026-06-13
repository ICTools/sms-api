package be.ictools.smsgateway

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import be.ictools.smsgateway.data.AppContainer
import be.ictools.smsgateway.service.GatewayServiceLauncher

class SmsGatewayApp : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.syncScheduler.schedulePeriodicSync()
        GatewayServiceLauncher.start(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}

