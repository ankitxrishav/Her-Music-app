package com.fenrir.her.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.fenrir.her.utils.NetworkConnectivityObserver
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides
  @Singleton
  fun provideNetworkConnectivityObserver(
    @ApplicationContext context: Context
  ): NetworkConnectivityObserver {
    return NetworkConnectivityObserver(context)
  }
}
