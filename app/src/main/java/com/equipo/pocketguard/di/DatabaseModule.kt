package com.equipo.pocketguard.di

import android.content.Context
import androidx.room.Room
import com.equipo.pocketguard.data.eventlog.AppDatabase
import com.equipo.pocketguard.data.eventlog.EventDao
import com.equipo.pocketguard.data.eventlog.EventRecorder
import com.equipo.pocketguard.data.eventlog.RoomEventRecorder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    abstract fun bindEventRecorder(impl: RoomEventRecorder): EventRecorder

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

        @Provides
        fun provideEventDao(database: AppDatabase): EventDao = database.eventDao()
    }
}
