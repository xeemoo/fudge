package dev.danielc.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DummyWorker(
  appContext: Context,
  workerParams: WorkerParameters,
  private val dependency: DummyWorkerDependency
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    dependency.onWorkerExecuted()
    return Result.success()
  }

  companion object {
    const val UNIQUE_WORK_NAME = "dummy_worker_bootstrap"
  }
}
