package com.example.conversaandroid.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.seudominio.conversa.data.local.dao.*
import com.seudominio.conversa.data.local.entities.*

@Database(
    entities = [
        UsuarioEntity::class,
        ConversaEntity::class,
        MensagemEntity::class,
        ConteudoEntity::class,
        ConversaUsuarioEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ConversaDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun conversaDao(): ConversaDao
    abstract fun mensagemDao(): MensagemDao

    companion object {
        @Volatile
        private var INSTANCE: ConversaDatabase? = null

        fun getInstance(context: Context): ConversaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConversaDatabase::class.java,
                    "conversa_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}