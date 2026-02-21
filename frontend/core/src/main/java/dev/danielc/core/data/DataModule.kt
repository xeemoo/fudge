package dev.danielc.core.data

import android.content.Context
import dev.danielc.core.BuildConfig
import dev.danielc.core.data.sdk.FakeFujifilmCameraClient
import dev.danielc.core.data.sdk.FujifilmCameraClientImpl
import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.DefaultErrorMessageMapper
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.PreviewRepository
import dev.danielc.core.domain.usecase.EnqueueDownloadUseCase
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.FetchPreviewImageUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveDownloadStateUseCase
import dev.danielc.core.domain.usecase.ObserveQueuePhotoStatusUseCase
import dev.danielc.core.domain.usecase.ObserveQueueStatsUseCase
import dev.danielc.core.media.ContentResolverMediaUriVerifier
import dev.danielc.core.media.MediaStoreImageSaver
import dev.danielc.core.media.MediaStoreImageSaverImpl
import dev.danielc.core.media.MediaUriVerifier
import dev.danielc.sdk.legacy.FujifilmLegacySdk
import dev.danielc.sdk.legacy.LegacyNativeFujifilmSdk
import org.koin.dsl.module

val dataModule = module {
  if (BuildConfig.USE_FAKE_CAMERA) {
    single<FujifilmCameraClient> { FakeFujifilmCameraClient() }
  } else {
    single<FujifilmLegacySdk> { LegacyNativeFujifilmSdk() }
    single<FujifilmCameraClient> { FujifilmCameraClientImpl(get()) }
  }
  single<PhotoRepository> { PhotoRepositoryImpl(get()) }
  single<PreviewRepository> { PreviewRepositoryImpl(get()) }
  single<ThumbnailRepository> { ThumbnailRepositoryImpl(get()) }
  single<MediaStoreImageSaver> { MediaStoreImageSaverImpl(get<Context>().contentResolver) }
  single<MediaUriVerifier> { ContentResolverMediaUriVerifier(get<Context>().contentResolver) }
  single<QueueIdProvider> { InMemoryQueueIdProvider() }
  single<ErrorMessageMapper> { DefaultErrorMessageMapper(get()) }
  single { FetchPhotoListUseCase(get(), get()) }
  single { FetchPreviewImageUseCase(get(), get()) }
  single { IsDownloadedUseCase(get(), get()) }
  single { ObserveDownloadStateUseCase(get(), get()) }
  single { ObserveQueueStatsUseCase(get(), get()) }
  single { ObserveQueuePhotoStatusUseCase(get(), get()) }
  single { EnqueueDownloadUseCase(get(), get(), get(), get(), get(), get()) }
  single<HotspotHistoryRepository> { HotspotHistoryRepositoryImpl(get()) }
  single<CameraSessionManager> { CameraSessionManagerImpl(get(), get()) }
}
