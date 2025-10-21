package com.samsung.health.mobile.di

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import com.google.firebase.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // This is the specific URL you provided
    private const val DATABASE_URL = "https://healthdatasync-b0458-default-rtdb.asia-southeast1.firebasedatabase.app/"

    @Provides
    @Singleton
    fun provideRealtimeDatabase(): DatabaseReference {
        // Get the instance and point to a specific "root" node for our data
        return Firebase.database(DATABASE_URL).reference.child("health_data_stream")
    }
}