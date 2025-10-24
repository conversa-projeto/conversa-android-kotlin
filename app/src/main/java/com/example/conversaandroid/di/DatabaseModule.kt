package com.example.conversaandroid.di

import android.content.Context
import com.google.gson.Gson
import com.seudominio.conversa.data.local.ConversaDatabase
import com.seudominio.conversa.data.local.dao.ConversaDao
import com.seudominio.conversa.data.local.dao.MensagemDao
import com.seudominio.conversa.data.local.dao.UsuarioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ConversaDatabase {
        return ConversaDatabase.getInstance(context)
    }

    @Provides
    fun provideUsuarioDao(database: ConversaDatabase): UsuarioDao {
        return database.usuarioDao()
    }

    @Provides
    fun provideConversaDao(database: ConversaDatabase): ConversaDao {
        return database.conversaDao()
    }

    @Provides
    fun provideMensagemDao(database: ConversaDatabase): MensagemDao {
        return database.mensagemDao()
    }
}