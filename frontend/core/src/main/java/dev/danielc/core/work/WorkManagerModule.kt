package dev.danielc.core.work

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import dev.danielc.core.work.notification.DefaultDownloadNotificationFactory
import dev.danielc.core.work.notification.DownloadNotificationFactory
import dev.danielc.core.work.worker.DownloadQueueWorkerDependency
import org.koin.dsl.module

val workManagerModule = module {
  single { DummyWorkerDependency() }
  single {
    DownloadQueueWorkerDependency(
      taskDao = get(),
      downloadedDao = get(),
      cameraClient = get(),
      saver = get(),
      analyticsTracker = get()
    )
  }
  single<DownloadNotificationFactory> { DefaultDownloadNotificationFactory(get()) }

  single<WorkerFactory> { AppWorkerFactory(get(), get(), get()) }
  single { WorkManager.getInstance(get<Context>()) }
  single<DownloadQueueScheduler> { DownloadQueueSchedulerImpl(get()) }
}
