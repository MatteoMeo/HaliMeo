package it.halimeo.app.network

import android.content.Context
import it.halimeo.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS = "halimeo_server"
    private const val KEY_BASE_URL = "base_url"
    private lateinit var appContext: Context
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun initialize(context: Context) { appContext = context.applicationContext }

    fun baseUrl(): String {
        val saved = if (::appContext.isInitialized) appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BASE_URL, null) else null
        return normalize(saved ?: BuildConfig.API_BASE_URL)
    }

    fun setBaseUrl(url: String) {
        require(::appContext.isInitialized) { "ApiClient non inizializzato" }
        val normalized = normalize(url)
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) { "Inserisci un indirizzo http:// o https:// valido" }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BASE_URL, normalized).apply()
    }

    private fun normalize(url: String) = url.trim().let { if (it.endsWith("/")) it else "$it/" }

    val api: HaliMeoApi
        get() = Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HaliMeoApi::class.java)
}
