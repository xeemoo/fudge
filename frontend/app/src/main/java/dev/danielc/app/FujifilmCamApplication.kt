package dev.danielc.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import dev.danielc.core.di.coreModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

class FujifilmCamApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    startKoin {
      androidContext(this@FujifilmCamApplication)
      modules(coreModule, appModule)
    }

    val workerFactory: WorkerFactory = GlobalContext.get().get()
    WorkManager.initialize(
      this,
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
    )
  }
}
