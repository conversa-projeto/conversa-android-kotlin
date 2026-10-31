package com.conversa.app.registro

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.conversa.app.BuildConfig
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * O retrato do aparelho para o registro do app: versão, aparelho, memória, espaço, energia,
 * notificações e permissões. Só leitura: nada aqui muda configuração alguma.
 */
internal class RetratoDoAparelho(private val contexto: Context) {
    private val atividades = contexto.getSystemService(ActivityManager::class.java)
    private val energia = contexto.getSystemService(PowerManager::class.java)

    fun app(): String = "Conversa ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.APPLICATION_ID}, " +
        "build ${BuildConfig.BUILD_TYPE}"

    fun aparelho(): String = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} " +
        "(SDK ${Build.VERSION.SDK_INT}, segurança ${Build.VERSION.SECURITY_PATCH}), ${Build.SUPPORTED_ABIS.firstOrNull()}, " +
        "idioma ${Locale.getDefault().toLanguageTag()}, fuso ${TimeZone.getDefault().id}"

    fun memoria(): String {
        val info = ActivityManager.MemoryInfo().also { atividades?.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        return "aparelho com ${mb(info.availMem)} livres de ${mb(info.totalMem)}" +
            (if (info.lowMemory) " (memória baixa)" else "") +
            "; app usando ${mb(rt.totalMemory() - rt.freeMemory())} de ${mb(rt.maxMemory())} (classe ${atividades?.memoryClass} MB)"
    }

    // Só para o registro: o espaço livre como o aparelho mostra (sem contar o cache que o sistema pode limpar).
    @SuppressLint("UsableSpace")
    fun espaco(): String {
        val livre = contexto.filesDir.usableSpace
        val banco = tamanhoDaPasta(File(contexto.applicationInfo.dataDir, "databases"))
        val cache = tamanhoDaPasta(contexto.cacheDir)
        return "${mb(livre)} livres no aparelho; banco ${mb(banco)}, cache ${mb(cache)}"
    }

    /** Bateria e o que o Android faz com o app em segundo plano (Doze, balde de espera, restrições). */
    fun energia(): String {
        val bateria = contexto.getSystemService(BatteryManager::class.java)
        val nivel = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val carregando = bateria?.isCharging == true
        val isento = energia?.isIgnoringBatteryOptimizations(contexto.packageName) == true
        val balde = contexto.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
        val rede = contexto.getSystemService(ConnectivityManager::class.java)
        return "bateria $nivel%" + (if (carregando) " carregando" else "") +
            "; economia de energia: ${simOuNao(energia?.isPowerSaveMode == true)}" +
            "; Doze: ${simOuNao(energia?.isDeviceIdleMode == true)}" +
            "; isento da otimização de bateria: ${simOuNao(isento)}" +
            "; restrito em segundo plano: ${simOuNao(atividades?.isBackgroundRestricted == true)}" +
            "; balde de espera: ${nomeDoBalde(balde)}" +
            "; economia de dados: ${nomeDaEconomiaDeDados(rede?.restrictBackgroundStatus)}"
    }

    /** As notificações do app e a importância de cada canal (0 = bloqueado pela pessoa). */
    fun notificacoes(): String {
        val gerenciador = NotificationManagerCompat.from(contexto)
        val canais = gerenciador.notificationChannelsCompat.joinToString { "${it.id}=${it.importance}" }
        val telaCheia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            simOuNao(contexto.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true)
        } else {
            "sim"
        }
        return "ligadas: ${simOuNao(gerenciador.areNotificationsEnabled())}; tela cheia nas chamadas: $telaCheia; canais: $canais"
    }

    /** As permissões que o app pede em tempo de execução: nome legível → concedida. */
    fun permissoes(): Map<String, Boolean> = buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) put("notificações", concedida(Manifest.permission.POST_NOTIFICATIONS))
        put("microfone", concedida(Manifest.permission.RECORD_AUDIO))
        put("câmera", concedida(Manifest.permission.CAMERA))
        put("localização precisa", concedida(Manifest.permission.ACCESS_FINE_LOCATION))
        put("localização aproximada", concedida(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun concedida(permissao: String) = ContextCompat.checkSelfPermission(contexto, permissao) == PackageManager.PERMISSION_GRANTED

    companion object {
        fun simOuNao(valor: Boolean) = if (valor) "sim" else "não"

        fun mb(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))

        fun tamanhoDaPasta(pasta: File): Long = pasta.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        fun nomeDoBalde(balde: Int?): String = when (balde) {
            null -> "?"
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ativo"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "uso frequente"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequente"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "raro"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restrito"
            else -> "isento ($balde)"
        }

        fun nomeDaEconomiaDeDados(status: Int?): String = when (status) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "desligada"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "ligada, com o app liberado"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "ligada"
            else -> "?"
        }

        /**
         * A rede numa linha: tipo, se o Android confirmou a internet, se é medida. "Validada: não" num
         * Wi-Fi da empresa sem internet faz o Android preferir os dados móveis (e o servidor interno some).
         */
        fun resumoDaRede(rede: NetworkCapabilities): String {
            val tipos = buildList {
                if (rede.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                if (rede.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("dados móveis")
                if (rede.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("cabo")
                if (rede.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (rede.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
            }.ifEmpty { listOf("outra") }
            val validada = rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val medida = !rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val suspensa = !rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            return tipos.joinToString(" + ") + "; internet confirmada: ${simOuNao(validada)}; medida: ${simOuNao(medida)}" +
                if (suspensa) "; suspensa" else ""
        }

        /** Interface, DNS privado e proxy: o que muda o caminho até o servidor. */
        fun resumoDoEnlace(enlace: LinkProperties): String {
            val dnsPrivado = when {
                enlace.privateDnsServerName != null -> "DNS privado ${enlace.privateDnsServerName}"
                enlace.isPrivateDnsActive -> "DNS privado automático"
                else -> "sem DNS privado"
            }
            val proxy = enlace.httpProxy?.let { "proxy ${it.host}:${it.port}" } ?: "sem proxy"
            return "interface ${enlace.interfaceName}; ${enlace.dnsServers.size} servidor(es) DNS; $dnsPrivado; $proxy"
        }
    }
}
