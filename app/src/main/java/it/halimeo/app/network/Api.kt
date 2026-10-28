package it.halimeo.app.network

import it.halimeo.app.data.DiaryEntryDto
import retrofit2.http.*

data class LoginRequest(val email: String, val password: String)
data class RedeemInviteRequest(val inviteCode: String, val displayName: String, val email: String, val password: String)
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val userId: String, val displayName: String)
data class SyncRequest(val changes: List<DiaryEntryDto>, val lastSyncAt: Long? = null)
data class SyncResponse(val changes: List<DiaryEntryDto>, val serverTime: Long)

interface HaliMeoApi {
    @POST("v1/auth/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @POST("v1/auth/redeem-invite") suspend fun redeem(@Body request: RedeemInviteRequest): TokenResponse
    @POST("v1/sync") suspend fun sync(@Header("Authorization") auth: String, @Body request: SyncRequest): SyncResponse
    @GET("v1/health") suspend fun health(): Map<String, Any>
}
