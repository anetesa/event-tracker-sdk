package com.eventtracker.demo.di

import com.eventtracker.demo.data.SdkGateway
import com.eventtracker.demo.data.SdkGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {

    @Binds
    @Singleton
    abstract fun bindSdkGateway(impl: SdkGatewayImpl): SdkGateway
}
