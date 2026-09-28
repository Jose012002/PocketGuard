package com.equipo.pocketguard.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Dependencias globales de la aplicación. Se irá poblando fase a fase. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
