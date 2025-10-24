package com.example.conversaandroid.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seudominio.conversa.domain.model.TipoConversa
import com.seudominio.conversa.domain.model.TipoConteudo

class Converters {

    @TypeConverter
    fun fromTipoConversa(tipo: TipoConversa): Int = tipo.value

    @TypeConverter
    fun toTipoConversa(value: Int): TipoConversa = TipoConversa.fromValue(value)

    @TypeConverter
    fun fromTipoConteudo(tipo: TipoConteudo): Int = tipo.value

    @TypeConverter
    fun toTipoConteudo(value: Int): TipoConteudo = TipoConteudo.fromValue(value)

    @TypeConverter
    fun fromIntList(value: String): List<Int> {
        val listType = object : TypeToken<List<Int>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromListInt(list: List<Int>): String {
        return Gson().toJson(list)
    }
}