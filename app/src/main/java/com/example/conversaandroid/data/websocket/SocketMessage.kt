package com.example.conversaandroid.data.websocket

sealed class SocketMessage {
    data class Erro(val message: String) : SocketMessage()
    data class Login(val token: String) : SocketMessage()
    data class NovaMensagem(val conversaId: Int, val mensagemId: Int) : SocketMessage()
    data class AtualizacaoStatus(val grupo: Int, val mensagens: List<Int>) : SocketMessage()
    data class Desconhecida(val raw: String) : SocketMessage()
}

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}