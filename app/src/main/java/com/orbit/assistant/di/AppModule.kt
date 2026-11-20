package com.orbit.assistant.di

import com.orbit.assistant.agent.appdiscovery.AndroidAppLauncher
import com.orbit.assistant.agent.appdiscovery.AppLauncher
import com.orbit.assistant.agent.automation.AgentAutomationService
import com.orbit.assistant.agent.automation.MockAutomationAdapter
import com.orbit.assistant.data.repository.DemoAgentRepository
import com.orbit.assistant.data.repository.PreferencesRepositoryImpl
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: DemoAgentRepository): AgentRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAutomationService(impl: MockAutomationAdapter): AgentAutomationService

    @Binds
    @Singleton
    abstract fun bindAppLauncher(impl: AndroidAppLauncher): AppLauncher
}
