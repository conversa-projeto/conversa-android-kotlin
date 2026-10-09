package com.conversa.app.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.conversa.app.core.database.dao.AtividadeDao
import com.conversa.app.core.database.dao.ChamadaHistoricoDao
import com.conversa.app.core.database.dao.ContatoDao
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.EnvioPendenteDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.dao.RascunhoDao
import com.conversa.app.core.database.dao.SyncEstadoDao
import com.conversa.app.core.database.entidades.AtividadeEntidade
import com.conversa.app.core.database.entidades.ChamadaHistoricoEntidade
import com.conversa.app.core.database.entidades.ContatoEntidade
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.ConversaEntidade
import com.conversa.app.core.database.entidades.EnvioPendenteEntidade
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.database.entidades.RascunhoEntidade
import com.conversa.app.core.database.entidades.ReacaoEntidade
import com.conversa.app.core.database.entidades.SyncEstadoEntidade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

class Conversores {
    @TypeConverter
    fun deInstant(valor: Instant?): Long? = valor?.toEpochMilli()

    @TypeConverter
    fun paraInstant(valor: Long?): Instant? = valor?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [
        ConversaEntidade::class,
        MensagemEntidade::class,
        ConteudoEntidade::class,
        ReacaoEntidade::class,
        ContatoEntidade::class,
        AtividadeEntidade::class,
        ChamadaHistoricoEntidade::class,
        EnvioPendenteEntidade::class,
        SyncEstadoEntidade::class,
        RascunhoEntidade::class,
    ],
    version = 3,
    exportSchema = true,
    // 2: ordem das reações (a do servidor), para o "+N" esconder as mesmas que o web.
    // 3: rascunho por conversa (FC-519).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Conversores::class)
abstract class ConversaBanco : RoomDatabase() {
    abstract fun conversaDao(): ConversaDao

    abstract fun mensagemDao(): MensagemDao

    abstract fun contatoDao(): ContatoDao

    abstract fun atividadeDao(): AtividadeDao

    abstract fun chamadaHistoricoDao(): ChamadaHistoricoDao

    abstract fun envioPendenteDao(): EnvioPendenteDao

    abstract fun syncEstadoDao(): SyncEstadoDao

    abstract fun rascunhoDao(): RascunhoDao

    companion object {
        const val NOME = "conversa.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BancoModulo {
    @Provides
    @Singleton
    fun banco(@ApplicationContext context: Context): ConversaBanco =
        Room.databaseBuilder(context, ConversaBanco::class.java, ConversaBanco.NOME).build()

    @Provides fun conversaDao(banco: ConversaBanco) = banco.conversaDao()

    @Provides fun mensagemDao(banco: ConversaBanco) = banco.mensagemDao()

    @Provides fun contatoDao(banco: ConversaBanco) = banco.contatoDao()

    @Provides fun atividadeDao(banco: ConversaBanco) = banco.atividadeDao()

    @Provides fun chamadaHistoricoDao(banco: ConversaBanco) = banco.chamadaHistoricoDao()

    @Provides fun envioPendenteDao(banco: ConversaBanco) = banco.envioPendenteDao()

    @Provides fun syncEstadoDao(banco: ConversaBanco) = banco.syncEstadoDao()

    @Provides fun rascunhoDao(banco: ConversaBanco) = banco.rascunhoDao()
}
