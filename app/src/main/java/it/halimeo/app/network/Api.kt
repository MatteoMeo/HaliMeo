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

data class SocialComment(
    val id: String, val body: String, val userId: String, val authorName: String,
    val createdAt: Long, val updatedAt: Long
)
data class SocialFeedEntry(
    val id: String, val title: String, val body: String, val category: String,
    val memoryDate: String, val createdAt: Long, val updatedAt: Long,
    val authorId: String, val authorName: String, val likeCount: Int,
    val likedByMe: Boolean, val comments: List<SocialComment> = emptyList()
)
data class SocialFeedResponse(val entries: List<SocialFeedEntry>, val serverTime: Long)
data class SocialCommentRequest(val body: String)
data class SocialCommentResponse(
    val id: String, val entryId: String, val body: String, val userId: String,
    val authorName: String, val createdAt: Long, val updatedAt: Long
)
data class AlbumRequest(val title: String, val description: String = "")
data class AlbumEntryRequest(val entryId: String)
data class SocialAlbum(
    val id: String, val title: String, val description: String,
    val createdBy: String, val createdAt: Long, val updatedAt: Long, val entryCount: Int
)
data class SocialAlbumsResponse(val albums: List<SocialAlbum>)
data class SocialAlbumDetail(
    val id: String, val title: String, val description: String,
    val createdBy: String, val entries: List<SocialFeedEntry> = emptyList()
)

interface HaliMeoApi {
    @POST("v1/auth/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @POST("v1/auth/redeem-invite") suspend fun redeem(@Body request: RedeemInviteRequest): TokenResponse
    @GET("v1/auth/me") suspend fun me(@Header("Authorization") auth: String): AccountInfo
    @POST("v1/admin/invites") suspend fun createInvite(@Header("Authorization") auth: String, @Body request: InviteRequest): InviteResponse
    @POST("v1/sync") suspend fun sync(@Header("Authorization") auth: String, @Body request: SyncRequest): SyncResponse
    @GET("v1/social/feed") suspend fun socialFeed(@Header("Authorization") auth: String): SocialFeedResponse
    @POST("v1/entries/{entryId}/like") suspend fun likeEntry(@Header("Authorization") auth: String, @Path("entryId") entryId: String): Map<String, Any>
    @DELETE("v1/entries/{entryId}/like") suspend fun unlikeEntry(@Header("Authorization") auth: String, @Path("entryId") entryId: String): Map<String, Any>
    @GET("v1/entries/{entryId}/comments") suspend fun comments(@Header("Authorization") auth: String, @Path("entryId") entryId: String): Map<String, List<SocialComment>>
    @POST("v1/entries/{entryId}/comments") suspend fun addComment(@Header("Authorization") auth: String, @Path("entryId") entryId: String, @Body request: SocialCommentRequest): SocialCommentResponse
    @DELETE("v1/comments/{commentId}") suspend fun deleteComment(@Header("Authorization") auth: String, @Path("commentId") commentId: String): Map<String, Boolean>
    @GET("v1/albums") suspend fun albums(@Header("Authorization") auth: String): SocialAlbumsResponse
    @POST("v1/albums") suspend fun createAlbum(@Header("Authorization") auth: String, @Body request: AlbumRequest): SocialAlbum
    @GET("v1/albums/{albumId}") suspend fun album(@Header("Authorization") auth: String, @Path("albumId") albumId: String): SocialAlbumDetail
    @POST("v1/albums/{albumId}/entries") suspend fun addAlbumEntry(@Header("Authorization") auth: String, @Path("albumId") albumId: String, @Body request: AlbumEntryRequest): Map<String, Boolean>
    @DELETE("v1/albums/{albumId}/entries/{entryId}") suspend fun removeAlbumEntry(@Header("Authorization") auth: String, @Path("albumId") albumId: String, @Path("entryId") entryId: String): Map<String, Boolean>
    @Multipart @POST("v1/entries/{entryId}/photo") suspend fun uploadPhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String, @Query("photoUpdatedAt") photoUpdatedAt: Long, @Part photo: MultipartBody.Part): Map<String, Any>
    @Streaming @GET("v1/entries/{entryId}/photo") suspend fun downloadPhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String): ResponseBody
    @DELETE("v1/entries/{entryId}/photo") suspend fun deletePhoto(@Header("Authorization") auth: String, @Path("entryId") entryId: String, @Query("photoUpdatedAt") photoUpdatedAt: Long): Map<String, Any>
    @GET("v1/health") suspend fun health(): Map<String, Any>
}
