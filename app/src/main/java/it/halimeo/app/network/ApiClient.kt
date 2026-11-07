package it.halimeo.app.network

import android.content.Context
import it.halimeo.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private lateinit var appContext: Context
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun initialize(context: Context) { appContext = context.applicationContext }

    fun baseUrl(): String = normalize(BuildConfig.API_BASE_URL)

    private fun normalize(url: String) = url.trim().let { if (it.endsWith("/")) it else "$it/" }

    val api: HaliMeoApi
        get() = Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HaliMeoApi::class.java)
}
