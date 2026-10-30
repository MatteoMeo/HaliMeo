package it.halimeo.app.network

import it.halimeo.app.data.DiaryEntryDto
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody

data class LoginRequest(val email: String, val password: String)
data class RedeemInviteRequest(val inviteCode: String, val displayName: String, val email: String, val password: String)
data class InviteRequest(val expiresInDays: Int = 7)
data class InviteResponse(val inviteCode: String, val expiresAt: Long, val uses: Int)
data class AccountInfo(val userId: String, val displayName: String, val isOwner: Boolean)
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val userId: String, val displayName: String)
data class SyncRequest(val changes: List<DiaryEntryDto>, val lastSyncAt: Long? = null)
data class SyncResponse(val changes: List<DiaryEntryDto>, val serverTime: Long)

interface HaliMeoApi {
    @POST("v1/auth/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @POST("v1/auth/redeem-invite") suspend fun redeem(@Body request: RedeemInviteRequest): TokenResponse
    @GET("v1/auth/me") suspend fun me(@Header("Authorization") auth: String): AccountInfo
    @POST("v1/admin/invites") suspend fun createInvite(@Header("Authorization") auth: String, @Body request: InviteRequest): InviteResponse
    @POST("v1/sync") suspend fun sync(@Header("Authorization") auth: String, @Body request: SyncRequest): SyncResponse
    @Multipart @POST("v1/entries/{entryId}/photo") suspend fun uploadPhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String, @Part photo: MultipartBody.Part): Map<String, Any>
    @Streaming @GET("v1/entries/{entryId}/photo") suspend fun downloadPhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String): ResponseBody
    @DELETE("v1/entries/{entryId}/photo") suspend fun deletePhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String): Map<String, Any>
    @GET("v1/health") suspend fun health(): Map<String, Any>
}
