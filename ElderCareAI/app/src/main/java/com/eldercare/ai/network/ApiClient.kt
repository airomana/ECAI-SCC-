package com.eldercare.ai.network

import android.content.Context
import com.eldercare.ai.data.SettingsManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // 替换为您的后端服务器地址，注意以 / 结尾
    // 腾讯云服务器公网 IP
    const val BASE_URL = "http://122.51.208.124:8080/"

    private var retrofit: Retrofit? = null

    fun getRetrofit(context: Context): Retrofit {
        if (retrofit != null) return retrofit!!

        val settings = SettingsManager.getInstance(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }

        val authInterceptor = okhttp3.Interceptor { chain ->
            val token = settings.getJwtToken()
            val req = if (token.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(req)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(
                com.google.gson.GsonBuilder().setLenient().create()
            ))
            .build()

        return retrofit!!
    }

    fun <T> create(context: Context, service: Class<T>): T {
        return getRetrofit(context).create(service)
    }

    /** Token 更新后重建 Retrofit 实例 */
    fun reset() {
        retrofit = null
    }
}
