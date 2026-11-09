package it.halimeo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.media.ExifInterface
import android.os.Bundle
import android.os.Build
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.prismalGlassEffects
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import androidx.compose.ui.graphics.compositeOver
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.navigation.compose.*
import it.halimeo.app.data.*
import it.halimeo.app.network.*
import it.halimeo.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private suspend fun requestGoogleCredential(activity: ComponentActivity): GoogleIdTokenCredential {
    val credentialManager = CredentialManager.create(activity)
    val googleOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()

    val result = try {
        credentialManager.getCredential(context = activity, request = request)
    } catch (_: NoCredentialException) {
        // The account may exist on the device without a prior authorization for HaliMeo.
        // Fall back to the explicit Google sign-in sheet instead of failing with
        // "No credential available".
        val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val signInRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        credentialManager.getCredential(context = activity, request = signInRequest)
    }

    val credential = result.credential
    if (credential !is CustomCredential ||
        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        throw IllegalStateException("Google non ha restituito una credenziale valida.")
    }
    return GoogleIdTokenCredential.createFrom(credential.data)
}

private suspend fun decodeMemoryPhoto(context: android.content.Context, uri: String, maxDimension: Int): android.graphics.Bitmap? {
    val decoded = decodeScaledBitmap(context, uri, maxDimension) ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = android.graphics.Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
    if (matrix.isIdentity) return decoded
    return runCatching { android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
        .getOrDefault(decoded) // decodeScaledBitmap returns a cache-owned bitmap; never recycle it here.
}

private suspend fun decodeDiaryPhoto(
    context: android.content.Context,
    entryId: String,
    uri: String?,
    maxDimension: Int,
    repo: DiaryRepository
): android.graphics.Bitmap? {
    if (uri.isNullOrBlank()) return null
    return if (uri.startsWith(REMOTE_PHOTO_PREFIX)) {
        decodeRemoteScaledBitmap(uri, maxDimension) { repo.loadRemotePhotoBytes(entryId, maxDimension) }
    } else {
        decodeMemoryPhoto(context, uri, maxDimension)
    }
}

private fun applyLauncherIcon(context: android.content.Context, variant: String) {
    // Alias class names come from the manifest namespace, not applicationId.
    // This matters for debug builds, whose applicationId has the ".debug" suffix.
    val aliases = linkedMapOf(
        "ocean" to "it.halimeo.app.LauncherOcean",
        "lavender" to "it.halimeo.app.LauncherLavender",
        "mint" to "it.halimeo.app.LauncherMint",
        "rose" to "it.halimeo.app.LauncherRose",
        "graphite" to "it.halimeo.app.LauncherGraphite",
        "aurora" to "it.halimeo.app.LauncherAurora",
        "pearl" to "it.halimeo.app.LauncherPearl",
        "orchid" to "it.halimeo.app.LauncherOrchid",
        "starlight" to "it.halimeo.app.LauncherStarlight"
    )
    val selected = if (variant in aliases) variant else "ocean"
    val packageManager = context.packageManager
    val components = aliases.mapValues { (_, className) ->
        android.content.ComponentName(context.packageName, className)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+: build API 33 settings only on supported versions and switch atomically.
        val changes = aliases.map { (key, _) ->
            android.content.pm.PackageManager.ComponentEnabledSetting(
                components.getValue(key),
                if (key == selected) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
        packageManager.setComponentEnabledSettings(changes)
    } else {
        // Older Android versions: enable the new entry before disabling the others.
        val target = components.getValue(selected)
        packageManager.setComponentEnabledSetting(
            target,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
        components.filterValues { it != target }.values.forEach { component ->
            packageManager.setComponentEnabledSetting(
                component,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }
}

private data class HaliMeoAtmosphere(
    val primary: Color,
    val secondary: Color,
    val top: Color,
    val bottom: Color
)

private fun haliMeoAtmosphere(key: String): HaliMeoAtmosphere? = when (key) {
    "aurora" -> HaliMeoAtmosphere(Color(0xFF68A9FF), Color(0xFF9B83FF), Color(0xFF101C38), Color(0xFF090D19))
    "deep_ocean" -> HaliMeoAtmosphere(Color(0xFF35B8D0), Color(0xFF477FE8), Color(0xFF082A3A), Color(0xFF07111F))
    "sunset" -> HaliMeoAtmosphere(Color(0xFFFF9B72), Color(0xFFD66C9F), Color(0xFF321A2D), Color(0xFF100E1B))
    "forest" -> HaliMeoAtmosphere(Color(0xFF62C69A), Color(0xFF8CBF79), Color(0xFF102B26), Color(0xFF091512))
    "amethyst" -> HaliMeoAtmosphere(Color(0xFFB18CFF), Color(0xFF6D8DFF), Color(0xFF241B3F), Color(0xFF0D1020))
    "graphite" -> HaliMeoAtmosphere(Color(0xFF9AAFC4), Color(0xFF6C879B), Color(0xFF202B36), Color(0xFF090D13))
    "ruby" -> HaliMeoAtmosphere(Color(0xFFFF718A), Color(0xFFB94B77), Color(0xFF351426), Color(0xFF120A14))
    "citrus" -> HaliMeoAtmosphere(Color(0xFFE5D45C), Color(0xFF65B98A), Color(0xFF24351F), Color(0xFF10170F))
    "arctic" -> HaliMeoAtmosphere(Color(0xFF78D5F5), Color(0xFF8EA9FF), Color(0xFF172E48), Color(0xFF091522))
    "copper" -> HaliMeoAtmosphere(Color(0xFFFFB16B), Color(0xFFC87553), Color(0xFF3B241D), Color(0xFF160F0D))
    "lavender_mist" -> HaliMeoAtmosphere(Color(0xFFC6A5FF), Color(0xFF83A5EA), Color(0xFF302846), Color(0xFF13101F))
    "emerald" -> HaliMeoAtmosphere(Color(0xFF4BD6B0), Color(0xFF4E9F91), Color(0xFF12352F), Color(0xFF091713))
    else -> null
}

private val Ink = Color.Black
private val Panel = Color(0xFF0D1420)
private val Mint = Color(0xFF3E91FF)
private val Muted = Color(0xFFB8C4D6)
private val Elevated = Color(0xFF111827)
// Balanced One UI-inspired glass surfaces: translucent charcoal with a cool edge highlight.
private val FrostedSurface = Color(0xB80D1420)
private val FrostedSurfaceStrong = Color(0xD90D1420)
private val OneUiMotionEasing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
@Composable private fun frostedGlassBrush(): Brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.065f), Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)))
@Composable private fun isHaliMeoDark(): Boolean = when (appThemeModeState.value) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
@Composable
private fun haliMeoVisualTokens(): HaliMeoVisualTokens = HaliMeoVisualTokens.create(
    accent = MaterialTheme.colorScheme.primary,
    dark = isHaliMeoDark(),
    reduceEffects = reduceStaticEffectsState.value
)

@Composable
private fun frostedEdgeColor(): Color = haliMeoVisualTokens().outline

@Composable
private fun authTextFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
    focusedLabelColor = accent,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = accent,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLeadingIconColor = accent,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun haliMeoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
    unfocusedBorderColor = haliMeoVisualTokens().outline.copy(alpha = 0.72f),
    focusedContainerColor = frostedSurfaceStrongColor(),
    unfocusedContainerColor = frostedSurfaceColor(),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun haliMeoPopupSurfaceColor(): Color = haliMeoVisualTokens().elevatedSurface

@Composable
private fun frostedSurfaceColor(): Color = haliMeoVisualTokens().surface

@Composable
private fun frostedSurfaceStrongColor(): Color = haliMeoVisualTokens().strongSurface
private val customBackgroundColorState = mutableStateOf("default")
private val customBackgroundUriState = mutableStateOf<String?>(null)
private val backgroundBlurState = mutableStateOf(0f)
private val backgroundOverlayState = mutableStateOf(0.48f)
private val ambientLightState = mutableFloatStateOf(0.78f)
private val backgroundBrightnessState = mutableStateOf(50f)
private fun readBackgroundBrightness(prefs: android.content.SharedPreferences): Float {
    if (prefs.contains("background_brightness_percent")) {
        return prefs.getFloat("background_brightness_percent", 50f).coerceIn(0f, 100f)
    }
    val legacy = prefs.getFloat("background_brightness", 0f)
    return when {
        legacy < 0f -> 0f
        legacy == 0f -> 50f
        legacy == 1f -> 100f
        legacy in 0f..1f -> legacy * 100f
        else -> legacy.coerceIn(0f, 100f)
    }
}
private fun parseHaliMeoDate(value: String): java.time.LocalDate? {
    val trimmed = value.trim()
    return runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrNull()
}
private data class HaliMeoSpecialDate(val label: String, val date: String)
private fun readHaliMeoSpecialDates(prefs: android.content.SharedPreferences): List<HaliMeoSpecialDate> {
    val raw = prefs.getString("special_dates_json", null)
    if (!raw.isNullOrBlank()) return runCatching { val a = org.json.JSONArray(raw); (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> val l=o.optString("label").trim(); val d=o.optString("date").trim(); if(l.isNotBlank() && parseHaliMeoDate(d)!=null) HaliMeoSpecialDate(l,d) else null } } }.getOrDefault(emptyList())
    val l=prefs.getString("special_date_label", "").orEmpty(); val d=prefs.getString("special_date", "").orEmpty()
    return if(l.isNotBlank() && parseHaliMeoDate(d)!=null) listOf(HaliMeoSpecialDate(l,d)) else emptyList()
}
private fun saveHaliMeoSpecialDates(prefs: android.content.SharedPreferences, dates: List<HaliMeoSpecialDate>) { val a=org.json.JSONArray(); dates.forEach { a.put(org.json.JSONObject().put("label",it.label).put("date",it.date)) }; prefs.edit().putString("special_dates_json",a.toString()).apply() }
private fun displayHaliMeoDate(value: String): String {
    val date = runCatching { java.time.LocalDate.parse(value) }.getOrNull() ?: return value
    return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))
}
private val lastSyncAtState = mutableStateOf(0L)
private val profilePhotoRefreshState = mutableStateOf(0)
private val reduceAnimationsState = mutableStateOf(false)
private val reduceStaticEffectsState = mutableStateOf(false)
private val appThemeModeState = mutableStateOf("dark")
@Composable
private fun haliMeoAuthBackdrop(): Brush {
    val colors = MaterialTheme.colorScheme
    val atmosphere = haliMeoAtmosphere(customBackgroundColorState.value)
    val accent = atmosphere?.primary ?: colors.primary
    val secondary = atmosphere?.secondary ?: colors.secondary
    return if (isHaliMeoDark()) {
        Brush.linearGradient(
            0f to Color(0xFF080D19),
            0.28f to accent.copy(alpha = 0.18f).compositeOver(Color(0xFF101326)),
            0.62f to secondary.copy(alpha = 0.16f).compositeOver(Color(0xFF101326)),
            0.84f to accent.copy(alpha = 0.22f).compositeOver(Color(0xFF101326)),
            1f to Color(0xFF080D17)
        )
    } else {
        Brush.linearGradient(
            0f to accent.copy(alpha = 0.13f).compositeOver(Color(0xFFF2F3F9)),
            0.52f to secondary.copy(alpha = 0.10f).compositeOver(Color(0xFFEAEDF5)),
            1f to Color(0xFFF8F8FC)
        )
    }
}
private val TextPrimary = Color(0xFFF5F7FA)


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoTheme(content: @Composable () -> Unit) {
    val themeMode = appThemeModeState.value
    val darkTheme = when (themeMode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val context = LocalContext.current
    SideEffect {
        (context as? ComponentActivity)?.window?.let { window ->
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightNavigationBars = !darkTheme
                isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    LaunchedEffect(context) {
        val prefs = context.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
        appThemeModeState.value = prefs.getString("app_theme_mode", "dark")?.takeIf { it in setOf("dark", "light", "system") } ?: "dark"
        customBackgroundColorState.value = (prefs.getString("background_color", "default") ?: "default").takeUnless { it == "photo" } ?: "default"
        customBackgroundUriState.value = null
        backgroundBlurState.value = prefs.getFloat("background_blur", 0f)
        backgroundOverlayState.value = prefs.getFloat("background_overlay", 0.48f)
        ambientLightState.floatValue = prefs.getFloat("ambient_light_intensity", 0.78f).coerceIn(0.2f, 1f)
        backgroundBrightnessState.value = readBackgroundBrightness(prefs)
        val appReduceMotion = prefs.getBoolean("reduce_animations", false)
        val systemReduceMotion = runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        reduceAnimationsState.value = appReduceMotion || systemReduceMotion
        reduceStaticEffectsState.value = prefs.getBoolean("reduce_static_effects", false)
        val preferredHz = prefs.getInt("preferred_refresh_rate", 0).coerceIn(0, 120)
        (context as? ComponentActivity)?.window?.let { window ->
            window.attributes = window.attributes.apply { preferredRefreshRate = preferredHz.toFloat() }
        }
    }
    val staticFontWidth = 100f
    val staticFontRoundness = 28f
    // Keep the same hand-tuned palette in both modes: only luminance and surface
    // hierarchy change, while the user's selected accent remains untouched.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF102A3A), secondary = Color(0xFF61D9E8), onSecondary = Color(0xFF06272D),
            background = Color(0xFF090B12), onBackground = Color(0xFFF5F3FA),
            surface = Color(0xFF171522), onSurface = Color(0xFFF5F3FA),
            surfaceVariant = Color(0xFF252236), onSurfaceVariant = Color(0xFFD0CCDE),
            outline = Color(0xFF625B78), surfaceTint = Mint
        )
    } else {
        lightColorScheme(
            primary = Mint, onPrimary = Color.White, secondary = Color(0xFF5267B8), onSecondary = Color.White,
            background = Color(0xFFF5F2FA), onBackground = Color(0xFF252235),
            surface = Color(0xFFFFFCFF), onSurface = Color(0xFF252235),
            surfaceVariant = Color(0xFFECE7F4), onSurfaceVariant = Color(0xFF625D70),
            outline = Color(0xFFB9B1C8), surfaceTint = Mint
        )
    }
    val atmosphere = haliMeoAtmosphere(customBackgroundColorState.value)
    val backgroundAccent = atmosphere?.primary ?: when (customBackgroundColorState.value) {
        "blue" -> Color(0xFF3E91FF)
        "lavender" -> Color(0xFF8878FF)
        "mint" -> Color(0xFF2F9DAB)
        "rose" -> Color(0xFFD66A9B)
        "amber" -> Color(0xFFFFB547)
        "coral" -> Color(0xFFFF786B)
        "red" -> Color(0xFFFF525F)
        "orange" -> Color(0xFFFF8A35)
        "yellow" -> Color(0xFFE3C84B)
        "green" -> Color(0xFF58C98D)
        "teal" -> Color(0xFF31C7B3)
        "cyan" -> Color(0xFF45C8E8)
        "indigo" -> Color(0xFF6575F5)
        "violet" -> Color(0xFFB17CFF)
        "magenta" -> Color(0xFFE56CCB)
        "plum" -> Color(0xFF9B59B6)
        "turquoise" -> Color(0xFF20B8AA)
        "lime" -> Color(0xFF9ACD32)
        "gold" -> Color(0xFFD4A017)
        "navy" -> Color(0xFF315A9B)
        "sky" -> Color(0xFF62B6E8)
        "peach" -> Color(0xFFFFB38A)
        "fuchsia" -> Color(0xFFE84393)
        "slate" -> Color(0xFF78909C)
        else -> scheme.primary
    }
    val adaptiveScheme = scheme.copy(
        primary = backgroundAccent,
        secondary = atmosphere?.secondary ?: backgroundAccent.copy(alpha = 0.88f),
        tertiary = atmosphere?.secondary ?: scheme.tertiary
    )
    // Material 3 Expressive supplies the expressive motion system and updated component defaults.
    MaterialExpressiveTheme(
            colorScheme = adaptiveScheme,
            typography = rememberHaliMeoTypography(staticFontWidth, staticFontRoundness),
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes(
                // More compact, precise One UI-inspired controls instead of oversized expressive pills.
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp)
            ),
            content = content
        )
}
class MainActivity:ComponentActivity() {
    private val facebookCallbackManager = CallbackManager.Factory.create()

    fun startFacebookLogin(onToken: (String) -> Unit, onFacebookCancel: () -> Unit, onFacebookError: (String) -> Unit) {
        LoginManager.getInstance().registerCallback(facebookCallbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                LoginManager.getInstance().unregisterCallback(facebookCallbackManager)
                onToken(result.accessToken.token)
            }
            override fun onCancel() {
                LoginManager.getInstance().unregisterCallback(facebookCallbackManager)
                onFacebookCancel()
            }
            override fun onError(error: FacebookException) {
                LoginManager.getInstance().unregisterCallback(facebookCallbackManager)
                onFacebookError(error.localizedMessage ?: "Accesso Facebook non riuscito.")
            }
        })
        LoginManager.getInstance().logInWithReadPermissions(this, listOf("email", "public_profile"))
    }

    @Deprecated("Facebook SDK result forwarding")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private var frameStats: JankStats? = null
    private var lastForegroundUpdateCheckAt: Long = 0L

    override fun onStart() {
        super.onStart()
        val now = System.currentTimeMillis()
        // Check on each meaningful app opening/resume, but avoid repeating the request
        // when Android briefly backgrounds the activity for a permission/installer dialog.
        if (now - lastForegroundUpdateCheckAt >= 5 * 60 * 1000L) {
            lastForegroundUpdateCheckAt = now
            lifecycleScope.launch { checkForUpdatesOnLaunch(applicationContext) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            trimScaledBitmapCache()
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 17 can use Haze's native same-window backdrop path.
        // Enable it before Compose attaches any hazeBlur nodes; older Android
        // versions automatically retain the source-backed fallback.
        HazeFeatureFlags.isPlatformBackdropEnabled = true
        ApiClient.initialize(applicationContext)
        // Reclaim only APKs from updater releases already installed; do not wipe
        // the app cache on every launch or disturb a pending newer download.
        cleanupInstalledUpdateApks(applicationContext)
        SyncScheduler.schedule(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // Leave refresh-rate selection to Android by default. Samsung can then
        // switch dynamically between 120/96/90/60Hz based on content and power.
        lastSyncAtState.value = getSharedPreferences("halimeo_preferences", MODE_PRIVATE).getLong("last_sync_at", 0L)
        setContent { HaliMeoTheme { HaliMeoRoot() } }

        if (BuildConfig.DEBUG) {
            // Wait until Android has created the window decor before attaching JankStats.
            // Keep diagnostics best-effort: they must never prevent the app from launching.
            window.decorView.post {
                if (!isFinishing && !isDestroyed && frameStats == null) {
                    frameStats = runCatching {
                        JankStats.createAndTrack(window) { frame ->
                            if (frame.isJank) {
                                android.util.Log.d(
                                    "HaliMeoFrames",
                                    "Janky frame: ${frame.frameDurationUiNanos / 1_000_000} ms"
                                )
                            }
                        }
                    }.onFailure { error ->
                        android.util.Log.w("HaliMeoFrames", "Unable to start frame diagnostics", error)
                    }.getOrNull()
                }
            }
        }
    }

    override fun onDestroy() {
        frameStats?.isTrackingEnabled = false
        frameStats = null
        super.onDestroy()
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoRoot() {
    val ctx= LocalContext.current; val activity=ctx as ComponentActivity
    val session=remember{SessionStore(ctx)}; val repo=remember{DiaryRepository(ctx)}
    var signed by remember{mutableStateOf<Boolean?>(null)}; var displayName by remember{mutableStateOf("")}
    var pendingActivation by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf("") }
    var focusUpdater by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val token = session.token()
        signed = !token.isNullOrBlank()
        displayName = session.name()
        currentUserId = session.userId()
        pendingActivation = !token.isNullOrBlank() && !session.activated()
    }
    if(signed==null){Box(Modifier.fillMaxSize().background(if (isHaliMeoDark()) Color.Black else Color(0xFFF3F6FA)),contentAlignment=Alignment.Center){LoadingIndicator(modifier=Modifier.size(64.dp), color=MaterialTheme.colorScheme.primary)};return}
    if(signed==false){AuthScreen(
        onLogin={e,p,fail->activity.lifecycleScope.launch{
            runCatching{ApiClient.api.login(LoginRequest(e,p))}
                .onSuccess{
                    session.save(it.accessToken,it.userId,it.displayName,it.activated)
                    displayName=it.displayName; currentUserId=it.userId
                    pendingActivation=!it.activated; signed=true
                }.onFailure{fail(it.message?: "Accesso non riuscito")}
        }},
        onRegister={n,e,p,fail->activity.lifecycleScope.launch{
            runCatching{ApiClient.api.register(RegisterRequest(e,p,n))}
                .onSuccess{
                    session.save(it.accessToken,it.userId,it.displayName,it.activated)
                    displayName=it.displayName; currentUserId=it.userId
                    pendingActivation=!it.activated; signed=true
                }.onFailure{fail(it.message?: "Registrazione non riuscita")}
        }},
        onSocialLogin={provider,token,_,name,fail->activity.lifecycleScope.launch{
            runCatching { ApiClient.api.socialLogin(provider, SocialLoginRequest(token, null, name)) }
                .onSuccess {
                    session.save(it.accessToken,it.userId,it.displayName,it.activated)
                    displayName=it.displayName; currentUserId=it.userId
                    pendingActivation=!it.activated; signed=true
                }
                .onFailure { error -> fail(error.message?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: "Accesso $provider non riuscito. Riprova.") }
        }}
    );return}
    if (pendingActivation) {
        InviteActivationScreen(
            onRedeem = { code, fail ->
                activity.lifecycleScope.launch {
                    val token = session.token()
                    if (token.isNullOrBlank()) {
                        fail("Sessione non disponibile. Accedi nuovamente.")
                    } else {
                        runCatching { ApiClient.api.redeem("Bearer $token", RedeemInviteRequest(code)) }
                            .onSuccess {
                                session.save(it.accessToken,it.userId,it.displayName,true)
                                displayName=it.displayName; currentUserId=it.userId
                                pendingActivation=false
                            }
                            .onFailure { error -> fail(error.message?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: "Codice invito non valido.") }
                    }
                }
            },
            onLogout = {
                activity.lifecycleScope.launch {
                    session.clear()
                    signed=false; pendingActivation=false; currentUserId=""; displayName=""
                }
            }
        )
        return
    }
    val entries by repo.entries.collectAsState(initial= emptyList()); var editing by remember{mutableStateOf<DiaryEntry?>(null)}
    var syncInProgress by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    val pendingSyncCount = remember(entries) { entries.count { it.syncState != "SYNCED" || it.photoSyncState != "SYNCED" } }
    fun syncDiary() {
        if (syncInProgress) return
        syncInProgress = true
        syncFailed = false
        activity.lifecycleScope.launch {
            runCatching { repo.syncNow() }
                .onSuccess {
                    val now = System.currentTimeMillis()
                    ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putLong("last_sync_at", now).apply()
                    lastSyncAtState.value = now
                    Toast.makeText(ctx, "Sincronizzazione completata", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    syncFailed = true
                    val reason = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "errore sconosciuto"
                    Toast.makeText(ctx, "Sincronizzazione non riuscita: $reason", Toast.LENGTH_LONG).show()
                }
            syncInProgress = false
        }
    }
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            syncInProgress = true
            runCatching { repo.syncNow() }.onFailure { syncFailed = true }
            syncInProgress = false
        }
    }
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val navigationHaze = rememberHazeState()
    val navigationGlassTint = frostedSurfaceColor()
    val navigationGlassBackground = if (isHaliMeoDark()) Color(0xFF101419) else Color(0xFFF3F6FA)
    val navigationGlassStyle = remember(navigationGlassTint, navigationGlassBackground) {
        HazeBlurStyle {
            // Denser glass: retain visible backdrop detail while preventing the
            // tab bar from washing out against bright diary photos and light themes.
            backgroundColor(navigationGlassBackground.copy(alpha = 0.97f))
            colorEffects(listOf(HazeColorEffect.tint(navigationGlassTint.copy(alpha = 0.54f))))
            fallbackColorEffect(HazeColorEffect.tint(navigationGlassTint.copy(alpha = 0.66f)))
        }
    }
    val navigationScope = rememberCoroutineScope()
    val navigationFeedbackView = LocalView.current
    val primaryTab = pagerState.currentPage
    fun selectPrimaryTab(index: Int) {
        if (currentRoute != "main") nav.popBackStack("main", inclusive = false)
        navigationScope.launch {
            // Ignore duplicate taps while the same destination is already being reached.
            if (pagerState.targetPage == index) return@launch
            if (reduceAnimationsState.value) {
                pagerState.scrollToPage(index)
            } else {
                // Match Morphe's navigation: keep long jumps to one visible page of motion.
                pagerState.turnToPage(index)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(if (isHaliMeoDark()) Color(0xFF090B12) else Color(0xFFF5F2FA))) {
    val useNavigationRail = maxWidth >= 600.dp
    HaliMeoAmbientBackdrop(Modifier.fillMaxSize())
    Scaffold(
        modifier = Modifier.fillMaxSize().padding(start = if (useNavigationRail) 88.dp else 0.dp),
        containerColor=Color.Transparent,topBar={TopAppBar(
        title={
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HaliMeo", fontWeight=FontWeight.ExtraBold, style=MaterialTheme.typography.headlineSmall.copy(letterSpacing=(-1.1).sp, brush=Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))))
                val statusLabel = when {
                    syncInProgress -> "Sync…"
                    syncFailed -> "Offline"
                    pendingSyncCount > 0 -> "$pendingSyncCount da inviare"
                    lastSyncAtState.value > 0L -> "Aggiornato"
                    else -> "Solo locale"
                }
                val statusIcon = when {
                    syncInProgress -> Icons.Default.Sync
                    syncFailed -> Icons.Default.CloudOff
                    pendingSyncCount > 0 -> Icons.Default.CloudUpload
                    lastSyncAtState.value > 0L -> Icons.Default.CloudDone
                    else -> Icons.Default.Cloud
                }
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !syncInProgress, onClick = { syncDiary() }),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(statusIcon, contentDescription = null, tint = if (syncFailed) Color(0xFFFFC36B) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(statusLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        },
        modifier=Modifier.heightIn(min=56.dp),
        colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent, scrolledContainerColor=Color.Transparent, titleContentColor=MaterialTheme.colorScheme.onSurface,actionIconContentColor=MaterialTheme.colorScheme.onSurface),
        actions={
            Box(
                Modifier.padding(horizontal = 8.dp).size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        activity.lifecycleScope.launch {
                            val result = runCatching {
                                if (repo.hasPendingChanges()) repo.syncNow()
                            }
                            result.onSuccess {
                                session.clear()
                                signed = false
                                pendingActivation = false
                                currentUserId = ""
                                displayName = ""
                            }.onFailure { error ->
                                Toast.makeText(
                                    ctx,
                                    "Prima di uscire sincronizza i ricordi in sospeso: ${error.localizedMessage ?: "server non raggiungibile"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Esci",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
            }
        }
    )},bottomBar={}, floatingActionButton = {}, content = { padding ->
        NavHost(
            navController = nav,
            startDestination = "main",
            modifier = Modifier.padding(padding).fillMaxSize(),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("main") {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().hazeSource(navigationHaze),
                    beyondViewportPageCount = 1,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = HaliMeoMotion.pageSpring
                    ),
                    key = { it }
                ) { page ->
                    when (page) {
                        0 -> DiaryScreen(entries, repo,
                            { entry -> nav.navigate("memory/${entry.id}") }
                        )
                        1 -> TripsScreen(onOpenMonaco = { nav.navigate("tripMonaco") })
                        else -> SettingsScreen(
                            displayName = displayName, session = session, repo = repo,
                            activity = activity, ctx = ctx,
                            focusUpdater = focusUpdater,
                            onUpdaterFocused = { focusUpdater = false }
                        )
                    }
                }
            }
            composable(
                route = "edit",
                enterTransition = {
                    if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None
                    else HaliMeoMotion.detailEnter(forward = true)
                },
                exitTransition = {
                    if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None
                    else HaliMeoMotion.detailExit(forward = true)
                },
                popEnterTransition = {
                    if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None
                    else HaliMeoMotion.detailEnter(forward = false)
                },
                popExitTransition = {
                    if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None
                    else HaliMeoMotion.detailExit(forward = false)
                }
            ) { backStack ->
                EditScreen(editing, repo, { t, b, c, d, photo ->
                activity.lifecycleScope.launch {
                    repo.save(t, b, c, d, editing?.id, photo)
                    editing = null
                    nav.popBackStack()
                    runCatching { repo.syncNow() }
                }
            }, { editing = null; nav.popBackStack() })
            }
            composable(
                route = "memory/{entryId}",
                enterTransition = { if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None else HaliMeoMotion.detailEnter(forward = true) },
                exitTransition = { if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None else HaliMeoMotion.detailExit(forward = true) },
                popEnterTransition = { if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None else HaliMeoMotion.detailEnter(forward = false) },
                popExitTransition = { if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None else HaliMeoMotion.detailExit(forward = false) }
            ) { backStack ->
                val entryId = backStack.arguments?.getString("entryId")
                val memory = entries.firstOrNull { it.id == entryId }
                if (memory != null) MemoryDetailScreen(
                    memory,
                    repo = repo,
                    canManage = memory.ownerId.isNotBlank() && memory.ownerId == currentUserId,
                    onBack = { nav.popBackStack() },
                    onEdit = { editing = memory; nav.navigate("edit") },
                    onDelete = {
                        // Leave the detail destination before the Room Flow emits the
                        // soft-deleted row. Otherwise the memory == null fallback below
                        // can pop the back stack a second time and remove "main" as well.
                        nav.popBackStack()
                        activity.lifecycleScope.launch {
                            runCatching {
                                repo.delete(memory.id)
                                repo.syncNow()
                            }.onFailure { error ->
                                Toast.makeText(
                                    ctx,
                                    "Impossibile eliminare il ricordo: ${error.localizedMessage ?: "errore"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
                else LaunchedEffect(entryId, currentRoute) {
                    // A deleted row can disappear from the Room Flow while its
                    // outgoing NavHost entry is still composed. Only pop when this
                    // detail is still the active destination; never pop the diary
                    // page a second time during the exit transition.
                    if (nav.currentDestination?.route == "memory/{entryId}") {
                        nav.popBackStack()
                    }
                }
            }
            composable(
                route = "tripMonaco",
                enterTransition = { if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None else HaliMeoMotion.detailEnter(forward = true) },
                exitTransition = { if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None else HaliMeoMotion.detailExit(forward = true) },
                popEnterTransition = { if (reduceAnimationsState.value) androidx.compose.animation.EnterTransition.None else HaliMeoMotion.detailEnter(forward = false) },
                popExitTransition = { if (reduceAnimationsState.value) androidx.compose.animation.ExitTransition.None else HaliMeoMotion.detailExit(forward = false) }
            ) { MissionScreen() }
        }
    })
    // Navigation dock and create action are independent siblings in one horizontal
    // layout. The FAB owns its own 56dp slot to the right; it can never cover the dock.
    if (useNavigationRail) {
        NavigationRail(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp, top = 72.dp, bottom = 20.dp)
                .width(76.dp).clip(RoundedCornerShape(30.dp))
                .hazeBlur(
                    input = HazeInput.Backdrop(navigationHaze),
                    style = navigationGlassStyle,
                    performanceMode = HazePerformanceMode.Performance,
                    expandLayerBounds = false
                ),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            NavigationRailItem(
                selected = primaryTab == 0,
                onClick = { selectPrimaryTab(0) },
                icon = { Icon(Icons.Default.Book, contentDescription = null) },
                label = { Text("Diario") },
                alwaysShowLabel = false
            )
            NavigationRailItem(
                selected = primaryTab == 1,
                onClick = { selectPrimaryTab(1) },
                icon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null) },
                label = { Text("Viaggi") },
                alwaysShowLabel = false
            )
            NavigationRailItem(
                selected = primaryTab == 2,
                onClick = { selectPrimaryTab(2) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Impostazioni") },
                alwaysShowLabel = false
            )
        }
        if (primaryTab == 0 && currentRoute == "main") {
            Surface(
                onClick = { editing = null; nav.navigate("edit") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)
                    .size(56.dp).clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 8.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Nuovo ricordo", modifier = Modifier.size(26.dp))
                }
            }
        }
    }
    if (!useNavigationRail && currentRoute != "edit" && currentRoute != "tripMonaco" && currentRoute != "memory/{entryId}") {
        Row(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .hazeBlur(
                        input = HazeInput.Backdrop(navigationHaze),
                        style = navigationGlassStyle,
                        performanceMode = HazePerformanceMode.Performance,
                        expandLayerBounds = false
                    ),
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                tonalElevation = 0.dp
            ) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    val tabWidth = (maxWidth - 6.dp) / 3
                    val navigationSlotWidthPx = with(LocalDensity.current) { (tabWidth + 3.dp).toPx().coerceAtLeast(1f) }
                    var isNavigationDragging by remember { mutableStateOf(false) }
                    var isNavigationSettling by remember { mutableStateOf(false) }
                    var navigationPillPosition by remember { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
                    val navigationPillAnimatable = remember(pagerState) {
                        androidx.compose.animation.core.Animatable(pagerState.currentPage.toFloat())
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(pagerState, navigationSlotWidthPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        navigationPillPosition = if (isNavigationSettling) {
                                            navigationPillAnimatable.value
                                        } else {
                                            (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                                        }
                                        isNavigationDragging = true
                                        isNavigationSettling = false
                                        navigationScope.launch { navigationPillAnimatable.stop() }
                                    },
                                    onDragEnd = {
                                        val releasedAt = navigationPillPosition
                                        val destination = releasedAt.roundToInt().coerceIn(0, 2)
                                        if (reduceAnimationsState.value) {
                                            isNavigationDragging = false
                                            isNavigationSettling = false
                                            navigationScope.launch { pagerState.scrollToPage(destination) }
                                        } else {
                                            isNavigationSettling = true
                                            navigationScope.launch {
                                                navigationPillAnimatable.snapTo(releasedAt)
                                                isNavigationDragging = false
                                                kotlinx.coroutines.coroutineScope {
                                                    launch {
                                                        navigationPillAnimatable.animateTo(
                                                            destination.toFloat(),
                                                            animationSpec = HaliMeoMotion.navigationPillSpring
                                                        )
                                                    }
                                                    launch {
                                                        pagerState.animateScrollToPage(
                                                            destination,
                                                            animationSpec = HaliMeoMotion.pageSpring
                                                        )
                                                    }
                                                }
                                                isNavigationSettling = false
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        val releasedAt = navigationPillPosition
                                        val destination = pagerState.currentPage
                                        if (reduceAnimationsState.value) {
                                            isNavigationDragging = false
                                            navigationScope.launch { pagerState.scrollToPage(destination) }
                                        } else {
                                            isNavigationSettling = true
                                            navigationScope.launch {
                                                navigationPillAnimatable.snapTo(releasedAt)
                                                isNavigationDragging = false
                                                navigationPillAnimatable.animateTo(
                                                    destination.toFloat(),
                                                    animationSpec = HaliMeoMotion.navigationPillSpring
                                                )
                                                isNavigationSettling = false
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        navigationPillPosition = (
                                            navigationPillPosition + dragAmount.x / navigationSlotWidthPx
                                        ).coerceIn(0f, 2f)
                                    }
                                )
                            }
                    ) {
                        Box(
                            Modifier.graphicsLayer {
                                val pagePosition = when {
                                    isNavigationDragging -> navigationPillPosition
                                    isNavigationSettling -> navigationPillAnimatable.value
                                    reduceAnimationsState.value -> pagerState.currentPage.toFloat()
                                    else -> (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                                }
                                translationX = (tabWidth + 3.dp).toPx() * pagePosition
                            }
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(50))
                        )
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingNavItem(Modifier.weight(1f), primaryTab == 0, "Diario", Icons.Default.Book) {
                                navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectPrimaryTab(0)
                            }
                            FloatingNavItem(Modifier.weight(1f), primaryTab == 1, "Viaggi", Icons.Default.FlightTakeoff) {
                                navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectPrimaryTab(1)
                            }
                            FloatingNavItem(Modifier.weight(1f), primaryTab == 2, "Impostazioni", Icons.Default.Settings) {
                                navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectPrimaryTab(2)
                            }
                        }
                    }
                }
            }

            // Standalone action: separate hit target, separate surface, fixed size.
            // It sits beside (never over) the navigation dock.
            if (primaryTab == 0 && currentRoute == "main") {
                val createFabInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        editing = null
                        nav.navigate("edit")
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .haliMeoPressScale(
                            createFabInteraction,
                            reduceMotion = reduceAnimationsState.value,
                            pressedScale = 0.92f,
                            label = "diaryCreateAction"
                        ),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)
                    ),
                    shadowElevation = 8.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Nuovo ricordo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
    }
}
/**
 * Subtle Monet-tinted ambient light behind translucent surfaces.
 * The actual content remains crisp; the glow is a separate, non-interactive layer.
 */
@Composable
private fun HaliMeoAmbientBackdrop(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    val context = LocalContext.current
    val backgroundChoice = customBackgroundColorState.value
    val backgroundUri = customBackgroundUriState.value
    val photoBlur = backgroundBlurState.value
    val overlayStrength = backgroundOverlayState.value
    val brightnessAdjustment = backgroundBrightnessState.value
    val ambientIntensity = ambientLightState.floatValue
    // Keep the backdrop static. A frame-by-frame animated layer underneath a live blur
    // continuously invalidates the captured surface and can look like navbar flicker.
    val driftX = 0f
    val driftY = 0f
    val accent = palette.primary
    val customBase = if (isHaliMeoDark()) Color(
        red = (accent.red * 0.12f).coerceAtMost(0.12f),
        green = (accent.green * 0.12f).coerceAtMost(0.12f),
        blue = (accent.blue * 0.12f).coerceAtMost(0.12f), alpha = 1f
    ) else Color(
        red = (0.965f - accent.red * 0.035f).coerceIn(0.91f, 0.98f),
        green = (0.955f - accent.green * 0.025f).coerceIn(0.91f, 0.98f),
        blue = (0.985f - accent.blue * 0.015f).coerceIn(0.93f, 0.99f), alpha = 1f
    )
    val backgroundBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, backgroundUri, context) {
        value = backgroundUri?.let { decodeScaledBitmap(context, it, maxDimension = 1600) }
    }
    val bitmap = backgroundBitmap
    Box(modifier = modifier.clipToBounds().background(customBase)) {
        if (bitmap != null && backgroundChoice == "photo") {
            val brightness = brightnessAdjustment.coerceIn(0f, 100f)
            val brightnessScale = if (brightness <= 50f) brightness / 50f else (100f - brightness) / 50f
            val brightnessOffset = if (brightness <= 50f) 0f else ((brightness - 50f) / 50f) * 250f
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                brightnessScale, 0f, 0f, 0f, brightnessOffset,
                0f, brightnessScale, 0f, 0f, brightnessOffset,
                0f, 0f, brightnessScale, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(photoBlur.dp),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(brightnessMatrix)
            )
            val useDarkOverlay = brightness <= 50f
            val overlayColor = if (useDarkOverlay) Color.Black else Color.White
            val effectiveOverlay = if (isHaliMeoDark()) maxOf(overlayStrength, 0.68f) else maxOf(overlayStrength, 0.58f)
            Box(Modifier.fillMaxSize().background(overlayColor.copy(alpha = effectiveOverlay)))
        } else if (haliMeoAtmosphere(backgroundChoice) != null) {
            val atmosphere = haliMeoAtmosphere(backgroundChoice)!!
            val lightTop = atmosphere.primary.copy(alpha = 0.075f).compositeOver(Color(0xFFF7F3FC))
            val lightBottom = atmosphere.secondary.copy(alpha = 0.095f).compositeOver(Color(0xFFECE8F5))
            Box(Modifier.fillMaxSize().background(
                if (isHaliMeoDark()) Brush.verticalGradient(
                    0.0f to atmosphere.top,
                    0.52f to atmosphere.top.copy(red = (atmosphere.top.red * 0.72f + atmosphere.primary.red * 0.28f).coerceIn(0f, 1f), green = (atmosphere.top.green * 0.72f + atmosphere.primary.green * 0.28f).coerceIn(0f, 1f), blue = (atmosphere.top.blue * 0.72f + atmosphere.primary.blue * 0.28f).coerceIn(0f, 1f)),
                    1.0f to atmosphere.bottom
                ) else Brush.verticalGradient(listOf(lightTop, atmosphere.primary.copy(alpha = 0.045f).compositeOver(Color(0xFFF2EEF8)), lightBottom))
            ))
        } else if (backgroundChoice == "default") {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to customBase.copy(red = (customBase.red + 0.035f).coerceAtMost(1f), green = (customBase.green + 0.035f).coerceAtMost(1f), blue = (customBase.blue + 0.035f).coerceAtMost(1f)),
                        0.48f to customBase.copy(red = (customBase.red + 0.012f).coerceAtMost(1f), green = (customBase.green + 0.012f).coerceAtMost(1f), blue = (customBase.blue + 0.012f).coerceAtMost(1f)),
                        1.0f to if (isHaliMeoDark()) Color(0xFF05070C) else Color(0xFFECE8F5)
                    )
                )
            )
        }
        // Stronger but still soft ambient bloom. The slider now has a clearly
        // perceptible full-range response instead of topping out at a faint tint.
        val glow = ambientIntensity.coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = driftX.dp, y = (-140).dp + driftY.dp).size(520.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.primary.copy(alpha = 0.72f * glow),
                        0.38f to palette.secondary.copy(alpha = 0.38f * glow),
                        0.72f to palette.primary.copy(alpha = 0.12f * glow),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-195).dp - driftX.dp * 0.55f, y = 40.dp + driftY.dp).size(440.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.tertiary.copy(alpha = 0.48f * glow),
                        0.46f to palette.primary.copy(alpha = 0.22f * glow),
                        0.78f to palette.tertiary.copy(alpha = 0.08f * glow),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 180.dp - driftX.dp * 0.45f, y = 150.dp - driftY.dp).size(460.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.secondary.copy(alpha = 0.46f * glow),
                        0.42f to palette.primary.copy(alpha = 0.24f * glow),
                        0.78f to palette.secondary.copy(alpha = 0.08f * glow),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
    }
}

@Composable
private fun FloatingNavItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val animatedIcon by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(durationMillis = 90),
        label = "navItemIcon"
    )
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else HaliMeoMotion.listSpring(dampingRatio = 0.82f),
        label = "navItemScale"
    )
    val navInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.fillMaxHeight()
            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .haliMeoPressScale(navInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.94f, label = "navItemPress")
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = navInteraction, indication = LocalIndication.current, role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp), tint = animatedIcon)
    }
}

@Composable
private fun SettingsCategoryIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.22f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen(
    displayName: String,
    session: SessionStore,
    repo: DiaryRepository,
    activity: ComponentActivity,
    ctx: android.content.Context,
    focusUpdater: Boolean = false,
    onUpdaterFocused: () -> Unit = {},
) {
    val diaryEntries by repo.entries.collectAsState(initial = emptyList())
    val settingsFeedbackView = LocalView.current
    val pendingSyncCount = remember(diaryEntries) { diaryEntries.count { it.syncState != "SYNCED" || it.photoSyncState != "SYNCED" } }
    val lastSyncAt by lastSyncAtState
    var isOwner by remember { mutableStateOf<Boolean?>(null) }
    var accountEmail by remember { mutableStateOf("") }
    var linkedSocialIdentities by remember { mutableStateOf<List<SocialIdentity>>(emptyList()) }
    var accountSecurityBusy by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    var inviteBusy by remember { mutableStateOf(false) }
    var inviteMessage by remember { mutableStateOf("") }
    var developerUnlocked by remember { mutableStateOf(false) }
    var showDeveloperPinDialog by remember { mutableStateOf(false) }
    var developerPin by remember { mutableStateOf("") }
    var developerPinError by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf(ApiClient.baseUrl()) }
    var serverBusy by remember { mutableStateOf(false) }
    var developerStatus by remember { mutableStateOf("") }
    var profileAccountId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        profileAccountId = session.userId()
        val token = session.token()
        if (token.isNullOrBlank()) {
            isOwner = false
            inviteMessage = "Sessione non disponibile. Accedi di nuovo."
        } else {
            runCatching { ApiClient.api.me("Bearer $token") }
                .onSuccess { isOwner = it.isOwner }
                .onFailure {
                    isOwner = false
                    inviteMessage = "Impossibile verificare i permessi. Controlla la connessione e aggiorna il server."
                }
        }
        if (!token.isNullOrBlank()) {
            runCatching { ApiClient.api.socialIdentities("Bearer $token") }
                .onSuccess {
                    accountEmail = it.email
                    linkedSocialIdentities = it.identities
                }
                .onFailure {
                    accountEmail = ""
                    linkedSocialIdentities = emptyList()
                }
        }
    }

    val primaryText = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = MaterialTheme.colorScheme.primary
    var expandedSettingsSections by remember { mutableStateOf(emptySet<String>()) }
    val generalPrefs = remember { ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE) }
    val profilePrefs = remember(profileAccountId) {
        ctx.getSharedPreferences("halimeo_profile_$profileAccountId", android.content.Context.MODE_PRIVATE)
    }
    var profilePhotoUri by remember(profileAccountId) { mutableStateOf(profilePrefs.getString("profile_photo_uri", null)) }
    var profilePhotoUploadedUri by remember(profileAccountId) { mutableStateOf(profilePrefs.getString("profile_photo_uploaded_uri", null)) }
    var profileUploadBusy by remember { mutableStateOf(false) }
    var profileNickname by remember(profileAccountId) { mutableStateOf(profilePrefs.getString("profile_nickname", displayName) ?: displayName) }
    var profileAbout by remember(profileAccountId) { mutableStateOf(profilePrefs.getString("profile_about", "") ?: "") }
    var isProfileEditing by remember(profileAccountId) {
        mutableStateOf(
            !profilePrefs.getBoolean(
                "profile_saved",
                profilePrefs.contains("profile_nickname") || profilePrefs.contains("profile_about") || profilePrefs.contains("profile_photo_uri")
            )
        )
    }

    LaunchedEffect(profileAccountId) {
        if (profileAccountId.isBlank()) return@LaunchedEffect
        val token = session.token() ?: return@LaunchedEffect
        if (!profilePrefs.getBoolean("profile_migrated", false)) {
            val legacyNickname = generalPrefs.getString("profile_nickname", null)
            val legacyAbout = generalPrefs.getString("profile_about", null)
            val legacyPhoto = generalPrefs.getString("profile_photo_uri", null)
            if (legacyNickname != null || legacyAbout != null || legacyPhoto != null) {
                val migratedNickname = legacyNickname?.takeIf { it.isNotBlank() } ?: displayName
                val migratedAbout = legacyAbout.orEmpty()
                profilePrefs.edit()
                    .putString("profile_nickname", migratedNickname)
                    .putString("profile_about", migratedAbout)
                    .putString("profile_photo_uri", legacyPhoto)
                    .putBoolean("profile_saved", true)
                    .apply()
                profileNickname = migratedNickname
                profileAbout = migratedAbout
                profilePhotoUri = legacyPhoto
                runCatching {
                    ApiClient.api.updateProfile(
                        "Bearer $token",
                        ProfileUpdateRequest(migratedNickname, migratedAbout)
                    )
                }
            }
            generalPrefs.edit()
                .remove("profile_nickname")
                .remove("profile_about")
                .remove("profile_photo_uri")
                .remove("profile_photo_uploaded_uri")
                .remove("profile_saved")
                .apply()
            profilePrefs.edit().putBoolean("profile_migrated", true).apply()
        }
        runCatching { ApiClient.api.profile("Bearer $token") }
            .onSuccess { profile ->
                profileNickname = profile.nickname
                profileAbout = profile.about
                profilePrefs.edit()
                    .putString("profile_nickname", profile.nickname)
                    .putString("profile_about", profile.about)
                    .apply()
            }
    }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            profilePhotoUri = uri.toString()
            profilePrefs.edit().putString("profile_photo_uri", uri.toString()).apply()
        }
    }
    val profilePhotoBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, profilePhotoUri, ctx) {
        value = profilePhotoUri?.let { decodeScaledBitmap(ctx, it, maxDimension = 320) }
    }
    var specialDateLabel by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("special_date_label", "") ?: "") }
    var specialDate by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("special_date", "")?.let { displayHaliMeoDate(it) } ?: "") }
    var specialDates by remember { mutableStateOf(readHaliMeoSpecialDates(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE))) }
    var backgroundColor by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("background_color", "default") ?: "default") }
    var selectedAppIcon by remember { mutableStateOf(generalPrefs.getString("app_icon_variant", "ocean") ?: "ocean") }
    var backgroundPhoto by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("background_photo", null)) }
    var backgroundBlur by remember { mutableFloatStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getFloat("background_blur", 0f)) }
    var backgroundOverlay by remember { mutableFloatStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getFloat("background_overlay", 0.48f)) }
    var backgroundBrightness by remember { mutableFloatStateOf(readBackgroundBrightness(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE))) }
    fun saveBackgroundTuning() {
        ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putFloat("background_blur", backgroundBlur).putFloat("background_overlay", backgroundOverlay).putFloat("background_brightness_percent", backgroundBrightness).apply()
        backgroundBlurState.value = backgroundBlur
        backgroundOverlayState.value = backgroundOverlay
        backgroundBrightnessState.value = backgroundBrightness
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            backgroundPhoto = uri.toString()
            customBackgroundUriState.value = uri.toString()
            customBackgroundColorState.value = "photo"
            backgroundColor = "photo"
            ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putString("background_photo", uri.toString()).putString("background_color", "photo").apply()
        }
    }
    LaunchedEffect(Unit) {
        customBackgroundColorState.value = backgroundColor
        customBackgroundUriState.value = backgroundPhoto
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val parsed = parseHaliMeoDate(specialDate)
            if (parsed != null && specialDateLabel.isNotBlank()) scheduleSpecialDateReminder(ctx, specialDateLabel.trim(), parsed)
        }
    }

    val settingsHaze = rememberHazeState()
    val settingsGlassTint = frostedSurfaceColor()
    val settingsGlassStyleBackground = MaterialTheme.colorScheme.surface
    val settingsGlassStyle = remember(settingsGlassTint) {
        HazeBlurStyle {
            backgroundColor(settingsGlassStyleBackground.copy(alpha = 0.42f))
            colorEffects(listOf(HazeColorEffect.tint(settingsGlassTint.copy(alpha = 0.15f))))
            fallbackColorEffect(HazeColorEffect.tint(settingsGlassTint.copy(alpha = 0.28f)))
        }
    }
    val settingsScrollState = rememberScrollState()
    val updateCenterRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusUpdater) {
        if (focusUpdater) {
            kotlinx.coroutines.delay(80)
            updateCenterRequester.bringIntoView()
            onUpdaterFocused()
        }
    }
    var settingsHeaderCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(settingsScrollState) {
        androidx.compose.runtime.snapshotFlow { settingsScrollState.value }.collect { scrollY ->
            // Hysteresis prevents compact-card height changes from toggling the header
            // repeatedly around a single threshold while preserving the title animation.
            settingsHeaderCollapsed = if (settingsHeaderCollapsed) scrollY > 24 else scrollY > 64
        }
    }
    val settingsTitleScale by animateFloatAsState(
        targetValue = if (settingsHeaderCollapsed) 0.76f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(90, easing = OneUiMotionEasing),
        label = "settingsTitleScale"
    )
    Column(Modifier.fillMaxSize().background(Color.Transparent).hazeSource(settingsHaze)) {
        // Keep the settings title pinned while it scales down as the options list scrolls.
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "Impostazioni",
                modifier = Modifier.graphicsLayer {
                    scaleX = settingsTitleScale
                    scaleY = settingsTitleScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-0.65).sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "Personalizza il vostro spazio.",
                modifier = Modifier.height(if (settingsHeaderCollapsed) 0.dp else 26.dp).graphicsLayer {
                    alpha = if (settingsHeaderCollapsed) 0f else 1f
                    translationY = if (settingsHeaderCollapsed) -4.dp.toPx() else 0f
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(settingsScrollState).padding(start = 20.dp, end = 20.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        val profileCardInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(profileCardInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsProfilePress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = profileCardInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("profile" in expandedSettingsSections) expandedSettingsSections - "profile" else expandedSettingsSections + "profile" }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryIcon(Icons.Default.AccountCircle, Color(0xFF4389E8))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PROFILO E ACCOUNT", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("profile" !in expandedSettingsSections) Text("Il vostro account e la foto profilo", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("profile" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("profile" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(
                    visible = "profile" in expandedSettingsSections,
                    enter = HaliMeoMotion.sectionEnter(reduceAnimationsState.value),
                    exit = HaliMeoMotion.sectionExit(reduceAnimationsState.value)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account: $displayName", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                Text("I ricordi vengono salvati sul dispositivo prima della sincronizzazione.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        modifier = Modifier.size(76.dp).clickable(enabled = isProfileEditing) { profilePhotoPicker.launch(arrayOf("image/*")) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (profilePhotoBitmap != null) {
                                Image(bitmap = profilePhotoBitmap!!.asImageBitmap(), contentDescription = "Foto profilo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Aggiungi foto profilo", tint = accentText, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (profileNickname.isBlank()) displayName else profileNickname, color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (isProfileEditing) {
                    OutlinedTextField(value = profileNickname, onValueChange = { profileNickname = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), label = { Text("Nome o soprannome") }, singleLine = true,
                        colors = haliMeoTextFieldColors())
                    OutlinedTextField(value = profileAbout, onValueChange = { profileAbout = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), label = { Text("Una piccola descrizione") }, placeholder = { Text("Es. collezionisti di ricordi dal 2024") }, minLines = 2, maxLines = 3,
                        colors = haliMeoTextFieldColors())
                    Button(
                        enabled = !profileUploadBusy,
                        onClick = {
                            profileUploadBusy = true
                            activity.lifecycleScope.launch {
                                val result = runCatching {
                                    profilePrefs.edit()
                                        .putString("profile_nickname", profileNickname.trim())
                                        .putString("profile_about", profileAbout.trim())
                                        .putBoolean("profile_saved", true)
                                        .apply()
                                    val token = session.token() ?: throw IllegalStateException("Sessione non disponibile.")
                                    ApiClient.api.updateProfile(
                                        "Bearer $token",
                                        ProfileUpdateRequest(profileNickname.trim(), profileAbout.trim())
                                    )
                                    val selectedPhoto = profilePhotoUri
                                    if (!selectedPhoto.isNullOrBlank() && selectedPhoto != profilePhotoUploadedUri) {
                                        repo.uploadProfilePhoto(selectedPhoto)
                                        profilePhotoUploadedUri = selectedPhoto
                                        profilePrefs.edit().putString("profile_photo_uploaded_uri", selectedPhoto).apply()
                                        profilePhotoRefreshState.value++
                                    }
                                }
                                result.onSuccess {
                                    isProfileEditing = false
                                    Toast.makeText(ctx, "Profilo e foto sincronizzati", Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(ctx, "Impossibile sincronizzare la foto profilo: ${error.localizedMessage ?: "errore"}", Toast.LENGTH_LONG).show()
                                }
                                profileUploadBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (profileUploadBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (profileUploadBusy) "Sincronizzazione…" else "Salva profilo")
                    }
                    Text("La foto profilo viene sincronizzata e mostrata accanto ai ricordi pubblicati da questo account.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                } else {
                    if (profileAbout.isNotBlank()) {
                        Text(profileAbout, color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { isProfileEditing = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Modifica profilo")
                    }
                }
                // Account security lives with the profile, not in a separate legacy settings page.
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(frostedSurfaceColor())
                        .border(1.dp, frostedEdgeColor().copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = accentText)
                        Text("ACCESSO E SICUREZZA", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Email dell'account", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                    Text(
                        accountEmail.ifBlank { "Informazioni account non disponibili" },
                        color = primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Text("ACCOUNT COLLEGATI", color = secondaryText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    listOf("google" to "Google", "facebook" to "Facebook").forEach { (provider, label) ->
                        val identity = linkedSocialIdentities.firstOrNull { it.provider.equals(provider, ignoreCase = true) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (provider == "google") "G" else "f", color = accentText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(label, color = primaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(identity?.email ?: "Nessun account collegato", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = (if (identity != null) Color(0xFF55B98A) else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    if (identity != null) "Collegato" else "Non collegato",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    color = if (identity != null) Color(0xFF55B98A) else secondaryText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (linkedSocialIdentities.none { it.provider.equals("facebook", ignoreCase = true) }) {
                        OutlinedButton(
                            enabled = !accountSecurityBusy,
                            onClick = {
                                val hostActivity = activity as? MainActivity
                                if (hostActivity == null) {
                                    Toast.makeText(ctx, "Accesso Facebook non disponibile.", Toast.LENGTH_SHORT).show()
                                } else {
                                    accountSecurityBusy = true
                                    hostActivity.startFacebookLogin(
                                        onToken = { token ->
                                            activity.lifecycleScope.launch {
                                                runCatching {
                                                    ApiClient.api.linkSocialIdentity(
                                                        "Bearer ${session.token().orEmpty()}",
                                                        "facebook",
                                                        SocialLoginRequest(token, displayName = displayName)
                                                    )
                                                    ApiClient.api.socialIdentities("Bearer ${session.token().orEmpty()}")
                                                }.onSuccess {
                                                    accountEmail = it.email
                                                    linkedSocialIdentities = it.identities
                                                    Toast.makeText(ctx, "Account Facebook collegato", Toast.LENGTH_SHORT).show()
                                                }.onFailure { error ->
                                                    Toast.makeText(ctx, error.localizedMessage ?: "Impossibile collegare Facebook", Toast.LENGTH_LONG).show()
                                                }
                                                accountSecurityBusy = false
                                            }
                                        },
                                        onFacebookCancel = { accountSecurityBusy = false },
                                        onFacebookError = { message ->
                                            accountSecurityBusy = false
                                            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Collega account Facebook")
                        }
                    }
                    if (linkedSocialIdentities.none { it.provider.equals("google", ignoreCase = true) }) {
                        OutlinedButton(
                            enabled = !accountSecurityBusy,
                            onClick = {
                                accountSecurityBusy = true
                                activity.lifecycleScope.launch {
                                    runCatching {
                                        val googleCredential = requestGoogleCredential(activity)
                                        ApiClient.api.linkSocialIdentity(
                                            "Bearer ${session.token().orEmpty()}",
                                            "google",
                                            SocialLoginRequest(googleCredential.idToken, displayName = googleCredential.displayName)
                                        )
                                        ApiClient.api.socialIdentities("Bearer ${session.token().orEmpty()}")
                                    }.onSuccess {
                                        accountEmail = it.email
                                        linkedSocialIdentities = it.identities
                                        Toast.makeText(ctx, "Account Google collegato", Toast.LENGTH_SHORT).show()
                                    }.onFailure { error ->
                                        Toast.makeText(ctx, error.localizedMessage ?: "Impossibile collegare Google", Toast.LENGTH_LONG).show()
                                    }
                                    accountSecurityBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Collega account Google")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            accountSecurityBusy = true
                            activity.lifecycleScope.launch {
                                runCatching { ApiClient.api.socialIdentities("Bearer ${session.token().orEmpty()}") }
                                    .onSuccess {
                                        accountEmail = it.email
                                        linkedSocialIdentities = it.identities
                                    }
                                    .onFailure {
                                        Toast.makeText(ctx, "Impossibile aggiornare lo stato degli account collegati", Toast.LENGTH_SHORT).show()
                                    }
                                accountSecurityBusy = false
                            }
                        },
                        enabled = !accountSecurityBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (accountSecurityBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Aggiorna stato account")
                    }
                    Text(
                        "Il recupero password avviene dalla schermata di accesso. I collegamenti social saranno gestibili da qui dopo l'attivazione dei provider nell'app.",
                        color = secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                    }
                }
            }
        }


        val invitesCardInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(invitesCardInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsInvitesPress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = invitesCardInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("invites" in expandedSettingsSections) expandedSettingsSections - "invites" else expandedSettingsSections + "invites" }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryIcon(Icons.Default.MarkEmailRead, Color(0xFFEA9842))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INVITI", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if ("invites" !in expandedSettingsSections) Text("Crea e condividi un invito privato", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if ("invites" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("invites" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "invites" in expandedSettingsSections, enter = HaliMeoMotion.sectionEnter(reduceAnimationsState.value), exit = HaliMeoMotion.sectionExit(reduceAnimationsState.value)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Crea un codice monouso valido 7 giorni e condividilo direttamente dal telefono.", color = secondaryText)

                when (isOwner) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Verifica dei permessi…", color = secondaryText)
                    }
                    true -> {
                        Button(
                            enabled = !inviteBusy,
                            onClick = {
                                inviteBusy = true
                                inviteMessage = ""
                                inviteCode = ""
                                activity.lifecycleScope.launch {
                                    val token = session.token()
                                    if (token.isNullOrBlank()) {
                                        inviteMessage = "Sessione scaduta. Accedi di nuovo."
                                        inviteBusy = false
                                    } else {
                                        runCatching { ApiClient.api.createInvite("Bearer $token", InviteRequest()) }
                                            .onSuccess {
                                                inviteCode = it.inviteCode
                                                inviteMessage = "Invito creato. Valido per 7 giorni e utilizzabile una sola volta."
                                            }
                                            .onFailure {
                                                inviteMessage = "Non sono riuscito a creare l'invito. Controlla la connessione e riprova."
                                            }
                                        inviteBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (inviteBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (inviteBusy) "Creo l'invito…" else "Genera codice d'invito")
                        }

                        if (inviteCode.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle).clip(RoundedCornerShape(20.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("IL TUO CODICE", color = accentText, style = MaterialTheme.typography.labelLarge)
                                    SelectionContainer { Text(inviteCode, color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Codice invito HaliMeo", inviteCode))
                                                Toast.makeText(ctx, "Codice copiato", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Copia")
                                        }
                                        Button(
                                            onClick = {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Ti invito su HaliMeo! Inserisci questo codice nell'app per registrarti: $inviteCode")
                                                }
                                                ctx.startActivity(Intent.createChooser(sendIntent, "Condividi invito HaliMeo"))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Condividi")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    false -> Text(
                        if (inviteMessage.isNotBlank()) inviteMessage else "La creazione degli inviti è riservata al proprietario.",
                        color = secondaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                    }
                }

                if (inviteMessage.isNotBlank() && isOwner != false) {
                    Text(inviteMessage, color = if (inviteCode.isNotBlank()) accentText else secondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val appearanceCardInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(appearanceCardInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsAppearancePress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp))
                .clickable(interactionSource = appearanceCardInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("appearance" in expandedSettingsSections) expandedSettingsSections - "appearance" else expandedSettingsSections + "appearance" },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryIcon(Icons.Default.Palette, Color(0xFF9A78E8))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("THEME STUDIO", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("appearance" !in expandedSettingsSections) Text("Palette, atmosfere, luce e prestazioni", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("appearance" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("appearance" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "appearance" in expandedSettingsSections, enter = fadeIn(tween(90)), exit = fadeOut(tween(70))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aspetto dell'app", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                var selectedThemeMode by remember { mutableStateOf(appThemeModeState.value) }
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(frostedSurfaceColor(), RoundedCornerShape(22.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(22.dp))
                        .padding(4.dp),
                    space = 3.dp
                ) {
                    listOf("dark" to "Scuro", "light" to "Chiaro", "system" to "Sistema").forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = selectedThemeMode == item.first,
                            onClick = {
                                selectedThemeMode = item.first
                                appThemeModeState.value = item.first
                                ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("app_theme_mode", item.first).apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 3, RoundedCornerShape(18.dp)),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                activeContentColor = MaterialTheme.colorScheme.onSurface,
                                activeBorderColor = Color.Transparent,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = Color.Transparent
                            )
                        ) { Text(item.second, maxLines = 1, fontWeight = if (selectedThemeMode == item.first) FontWeight.SemiBold else FontWeight.Medium) }
                    }
                }
                Text("Il tema chiaro mantiene gli stessi accenti, sfondi e personalizzazioni del tema scuro.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                val activeAtmosphere = haliMeoAtmosphere(backgroundColor)
                Box(
                    Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    activeAtmosphere?.top ?: MaterialTheme.colorScheme.background,
                                    activeAtmosphere?.primary?.copy(alpha = 0.52f) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    activeAtmosphere?.bottom ?: MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
                ) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp).size(92.dp)
                            .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f), Color.Transparent)), CircleShape)
                    )
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ANTEPRIMA DEL VOSTRO SPAZIO", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(19.dp))
                                .background(Color.White.copy(alpha = if (isHaliMeoDark()) 0.13f else 0.52f))
                                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(19.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("HaliMeo", color = if (isHaliMeoDark()) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Un posto solo vostro.", color = if (isHaliMeoDark()) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isHaliMeoDark()) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Text("Luce ambientale", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Regola quanto risaltano i riflessi e le sfumature dello sfondo.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                var ambientIntensity by remember { mutableFloatStateOf(ambientLightState.floatValue) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Discreta", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = ambientIntensity,
                        onValueChange = {
                            ambientIntensity = it
                            ambientLightState.floatValue = it
                            ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                                .edit().putFloat("ambient_light_intensity", it).apply()
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(34.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = secondaryText.copy(alpha = 0.28f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Text("Intensa", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                }
                Text("Preset rapidi", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Atmosfere complete con sfondo, luce e accenti coordinati.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                val atmosphereOptions = listOf(
                    "aurora" to "Aurora",
                    "deep_ocean" to "Oceano",
                    "sunset" to "Tramonto",
                    "forest" to "Foresta",
                    "amethyst" to "Ametista",
                    "graphite" to "Grafite",
                    "ruby" to "Rubino",
                    "citrus" to "Agrumi",
                    "arctic" to "Artico",
                    "copper" to "Rame",
                    "lavender_mist" to "Lavanda",
                    "emerald" to "Smeraldo"
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    atmosphereOptions.forEach { option ->
                        val colors = haliMeoAtmosphere(option.first)!!
                        Column(
                            modifier = Modifier.width(86.dp).clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    settingsFeedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    backgroundColor = option.first
                                    backgroundPhoto = null
                                    customBackgroundColorState.value = option.first
                                    customBackgroundUriState.value = null
                                    ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("background_color", option.first).remove("background_photo").apply()
                                }.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(colors.top, colors.primary, colors.secondary, colors.bottom)))
                                    .border(
                                        if (backgroundColor == option.first) 2.dp else 1.dp,
                                        if (backgroundColor == option.first) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.22f),
                                        RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(42.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent)), CircleShape))
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.82f).padding(bottom = 7.dp).height(13.dp)
                                        .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.24f))
                                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
                                )
                                if (backgroundColor == option.first) {
                                    Icon(Icons.Default.Check, contentDescription = "Selezionato", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                            }
                            Text(option.second, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (backgroundColor == option.first) primaryText else secondaryText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text("Palette e colore d’accento", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val backgroundOptions = listOf(
                    "default" to "Predefinito", "blue" to "Blu", "lavender" to "Lavanda",
                    "mint" to "Menta", "rose" to "Rosa", "amber" to "Ambra",
                    "coral" to "Corallo", "red" to "Rosso", "orange" to "Arancione",
                    "yellow" to "Giallo", "green" to "Verde", "teal" to "Ottanio",
                    "cyan" to "Ciano", "indigo" to "Indaco", "violet" to "Viola",
                    "magenta" to "Magenta", "plum" to "Prugna", "turquoise" to "Turchese",
                    "lime" to "Lime", "gold" to "Oro", "navy" to "Blu notte",
                    "sky" to "Azzurro", "peach" to "Pesca", "fuchsia" to "Fucsia", "slate" to "Ardesia"
                )
                val accentSwatches = mapOf(
                    "blue" to Color(0xFF3E91FF), "lavender" to Color(0xFF8878FF),
                    "mint" to Color(0xFF2F9DAB), "rose" to Color(0xFFD66A9B),
                    "amber" to Color(0xFFFFB547), "coral" to Color(0xFFFF786B),
                    "red" to Color(0xFFFF525F), "orange" to Color(0xFFFF8A35),
                    "yellow" to Color(0xFFE3C84B), "green" to Color(0xFF58C98D),
                    "teal" to Color(0xFF31C7B3), "cyan" to Color(0xFF45C8E8),
                    "indigo" to Color(0xFF6575F5), "violet" to Color(0xFFB17CFF),
                    "magenta" to Color(0xFFE56CCB), "plum" to Color(0xFF9B59B6),
                    "turquoise" to Color(0xFF20B8AA), "lime" to Color(0xFF9ACD32),
                    "gold" to Color(0xFFD4A017), "navy" to Color(0xFF315A9B),
                    "sky" to Color(0xFF62B6E8), "peach" to Color(0xFFFFB38A),
                    "fuchsia" to Color(0xFFE84393), "slate" to Color(0xFF78909C)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    backgroundOptions.forEach { option ->
                        val swatch = accentSwatches[option.first] ?: MaterialTheme.colorScheme.primary
                        Column(
                            modifier = Modifier.width(64.dp).clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    backgroundColor = option.first
                                    backgroundPhoto = null
                                    customBackgroundColorState.value = option.first
                                    customBackgroundUriState.value = null
                                    ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("background_color", option.first).remove("background_photo").apply()
                                }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(swatch)
                                    .border(
                                        width = if (backgroundColor == option.first) 2.dp else 1.dp,
                                        color = if (backgroundColor == option.first) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (backgroundColor == option.first) {
                                    Icon(Icons.Default.Check, contentDescription = "Selezionato", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                            }
                            Text(option.second, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (backgroundColor == option.first) primaryText else secondaryText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Scegli il colore d'accento dell'app.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                val iconChoices = listOf(
                    Triple("ocean", "Ametista", R.mipmap.ic_launcher),
                    Triple("pearl", "Perla", R.mipmap.ic_launcher_pearl),
                    Triple("graphite", "Grafite", R.mipmap.ic_launcher_graphite),
                    Triple("aurora", "Blu", R.mipmap.ic_launcher_aurora),
                    Triple("lavender", "Viola", R.mipmap.ic_launcher_lavender),
                    Triple("mint", "Laguna", R.mipmap.ic_launcher_mint),
                    Triple("rose", "Tramonto", R.mipmap.ic_launcher_rose),
                    Triple("orchid", "Orchidea", R.mipmap.ic_launcher_orchid),
                    Triple("starlight", "Stellata", R.mipmap.ic_launcher_starlight)
                )
                val selectedIconLabel = iconChoices.firstOrNull { it.first == selectedAppIcon }?.second ?: "Ametista"
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(frostedSurfaceColor())
                        .border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp))
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Icona dell’app", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Personalizza il simbolo di HaliMeo.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                selectedIconLabel,
                                Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    iconChoices.chunked(3).forEach { choicesRow ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            choicesRow.forEach { (key, label, iconRes) ->
                                val selected = selectedAppIcon == key
                                Column(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                        .border(
                                            if (selected) 1.5.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            runCatching { applyLauncherIcon(ctx, key) }
                                                .onSuccess {
                                                    selectedAppIcon = key
                                                    generalPrefs.edit().putString("app_icon_variant", key).apply()
                                                    Toast.makeText(ctx, "Icona $label selezionata", Toast.LENGTH_SHORT).show()
                                                }
                                                .onFailure { error ->
                                                    android.util.Log.e("HaliMeoLauncher", "Cambio icona non riuscito per la variante $key", error)
                                                    Toast.makeText(ctx, "Non è stato possibile cambiare icona. Riprova.", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    val iconPreview = remember(iconRes, ctx) {
                                        val drawable = requireNotNull(ctx.getDrawable(iconRes)) {
                                            "Risorsa anteprima icona non disponibile"
                                        }
                                        android.graphics.Bitmap.createBitmap(
                                            192, 192, android.graphics.Bitmap.Config.ARGB_8888
                                        ).also { bitmap ->
                                            val canvas = android.graphics.Canvas(bitmap)
                                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                                            drawable.draw(canvas)
                                        }.asImageBitmap()
                                    }
                                    Box(
                                        Modifier.size(66.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = iconPreview,
                                            contentDescription = "Icona HaliMeo $label",
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                                        )
                                        if (selected) {
                                            Box(
                                                Modifier.align(Alignment.TopEnd)
                                                    .size(21.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Selezionata", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        label,
                                        color = if (selected) primaryText else secondaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                            repeat(3 - choicesRow.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Text("Il launcher potrebbe aggiornare l’icona dopo qualche secondo.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text("Prestazioni e movimento", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val prefs = ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                var selectedRefreshRate by remember { mutableIntStateOf(prefs.getInt("preferred_refresh_rate", 0)) }
                var reduceAnimations by remember { mutableStateOf(prefs.getBoolean("reduce_animations", false)) }
                var reduceStaticEffects by remember { mutableStateOf(prefs.getBoolean("reduce_static_effects", false)) }
                Text("Frequenza schermo", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(frostedSurfaceColor(), RoundedCornerShape(22.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f), RoundedCornerShape(22.dp))
                        .padding(4.dp),
                    space = 3.dp
                ) {
                    listOf(0, 48, 60, 90, 120).forEachIndexed { index, hz ->
                        SegmentedButton(
                            selected = selectedRefreshRate == hz,
                            onClick = {
                                selectedRefreshRate = hz
                                prefs.edit().putInt("preferred_refresh_rate", hz).apply()
                                activity.window.attributes = activity.window.attributes.apply { preferredRefreshRate = hz.toFloat() }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 5, RoundedCornerShape(14.dp)),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                activeContentColor = MaterialTheme.colorScheme.onSurface,
                                activeBorderColor = Color.Transparent,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = secondaryText,
                                inactiveBorderColor = Color.Transparent
                            )
                        ) { Text(if (hz == 0) "Auto" else "$hz", maxLines = 1, fontWeight = if (selectedRefreshRate == hz) FontWeight.SemiBold else FontWeight.Medium) }
                    }
                }
                Text("Auto lascia a One UI la scelta dinamica della frequenza; i valori manuali possono aumentare i consumi.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Riduci le animazioni", color = primaryText, style = MaterialTheme.typography.bodyLarge)
                        Text("Transizioni più rapide e meno effetti di movimento.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = reduceAnimations, onCheckedChange = {
                        reduceAnimations = it
                        prefs.edit().putBoolean("reduce_animations", it).apply()
                        reduceAnimationsState.value = it
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                        checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isHaliMeoDark()) Color(0xFF555A63) else Color(0xFFD1D5DC),
                        uncheckedBorderColor = if (isHaliMeoDark()) Color(0xFF666C76) else Color(0xFFB8BEC8),
                        uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Riduci sfocatura e trasparenze", color = primaryText, style = MaterialTheme.typography.bodyLarge)
                        Text("Usa superfici più opache e leggere per migliorare la fluidità.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = reduceStaticEffects, onCheckedChange = {
                        reduceStaticEffects = it
                        prefs.edit().putBoolean("reduce_static_effects", it).apply()
                        reduceStaticEffectsState.value = it
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                        checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isHaliMeoDark()) Color(0xFF555A63) else Color(0xFFD1D5DC),
                        uncheckedBorderColor = if (isHaliMeoDark()) Color(0xFF666C76) else Color(0xFFB8BEC8),
                        uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
                Text("Riduce gli effetti di sfocatura per privilegiare leggibilità e prestazioni.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }



        val dateCardInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(dateCardInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsDatePress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = dateCardInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("date" in expandedSettingsSections) expandedSettingsSections - "date" else expandedSettingsSections + "date" }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryIcon(Icons.Default.Event, Color(0xFFE4779C))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DATA SPECIALE", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("date" !in expandedSettingsSections) Text("Date importanti e promemoria", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("date" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("date" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "date" in expandedSettingsSections, enter = fadeIn(tween(90)), exit = fadeOut(tween(70))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tenete a portata di mano una data importante", color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = specialDateLabel, onValueChange = { specialDateLabel = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), singleLine = true, label = { Text("Nome (es. compleanno)") },
                        colors = haliMeoTextFieldColors())
                OutlinedTextField(value = specialDate, onValueChange = { specialDate = it.take(10) }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(frostedSurfaceColor(), RoundedCornerShape(18.dp)), singleLine = true, shape = RoundedCornerShape(22.dp), label = { Text("Data (gg/mm/aa)") }, placeholder = { Text("26/12/26") },
                        colors = haliMeoTextFieldColors())
                Button(onClick = {
                    val parsed = parseHaliMeoDate(specialDate)
                    if (specialDateLabel.isNotBlank() && parsed != null) {
                        val item = HaliMeoSpecialDate(specialDateLabel.trim(), parsed.toString())
                        specialDates = (specialDates.filterNot { it.label.equals(item.label, true) && it.date == item.date } + item).sortedBy { it.date }
                        val prefs = ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                        saveHaliMeoSpecialDates(prefs, specialDates); prefs.edit().putString("special_date_label", item.label).putString("special_date", item.date).apply()
                        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else scheduleSpecialDateReminder(ctx, item.label, parsed)
                        specialDateLabel = ""; specialDate = ""; Toast.makeText(ctx, "Data speciale aggiunta", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(ctx, "Inserisci un nome e una data valida", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Aggiungi data") }
                if (specialDates.isNotEmpty()) {
                    Text("Le tue date", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    specialDates.forEach { item -> Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = frostedSurfaceStrongColor(), border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor())) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(item.label, color = primaryText, fontWeight = FontWeight.SemiBold); Text(displayHaliMeoDate(item.date), color = secondaryText, style = MaterialTheme.typography.bodySmall) }
                            IconButton(onClick = { specialDates = specialDates.filterNot { it == item }; val prefs = ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE); saveHaliMeoSpecialDates(prefs, specialDates); val next = specialDates.firstOrNull(); prefs.edit().putString("special_date_label", next?.label.orEmpty()).putString("special_date", next?.date.orEmpty()).apply(); Toast.makeText(ctx, "Data speciale eliminata", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina ${item.label}", tint = MaterialTheme.colorScheme.error) }
                        }
                    } }
                }
                Text("Promemoria locale alle 09:00 nel giorno scelto, se le notifiche sono abilitate.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        val syncCardInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(syncCardInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsSyncPress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = syncCardInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("sync" in expandedSettingsSections) expandedSettingsSections - "sync" else expandedSettingsSections + "sync" }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryIcon(Icons.Default.CloudSync, Color(0xFF27A99A))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SINCRONIZZAZIONE", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("sync" !in expandedSettingsSections) Text("Backup e stato dei tuoi ricordi", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("sync" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("sync" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "sync" in expandedSettingsSections, enter = fadeIn(tween(90)), exit = fadeOut(tween(70))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF27A99A), modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (pendingSyncCount == 0) "Tutto aggiornato" else "$pendingSyncCount ricordi in attesa", color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (pendingSyncCount == 0) "Non ci sono modifiche da inviare." else "I dati restano sul dispositivo finché la sincronizzazione non riesce.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                        Text(if (lastSyncAt > 0L) "Ultimo tentativo riuscito: " + java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.ITALIAN).format(java.util.Date(lastSyncAt)) else "Nessuna sincronizzazione riuscita in questa installazione.", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = {
                        activity.lifecycleScope.launch {
                            runCatching { repo.syncNow() }
                                .onSuccess {
                                    val now = System.currentTimeMillis()
                                    ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putLong("last_sync_at", now).apply()
                                    lastSyncAtState.value = now
                                    Toast.makeText(ctx, "Sincronizzazione completata", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { Toast.makeText(ctx, "Server non raggiungibile: dati conservati sul dispositivo", Toast.LENGTH_LONG).show() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sincronizza ora") }

                OutlinedButton(
                    onClick = {
                        activity.lifecycleScope.launch {
                            runCatching {
                                val rows = repo.allForBackup().filter { it.deletedAt == null }
                                fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                                val export = buildString {
                                    appendLine("Titolo,Data,Categoria,Ricordo")
                                    rows.forEach { row ->
                                        appendLine(listOf(row.title, row.memoryDate, row.category, row.body).joinToString(",") { csv(it) })
                                    }
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "Backup ricordi HaliMeo")
                                    putExtra(Intent.EXTRA_TEXT, export)
                                }
                                ctx.startActivity(Intent.createChooser(sendIntent, "Esporta i tuoi ricordi"))
                            }.onFailure {
                                Toast.makeText(ctx, "Non sono riuscito a preparare il backup", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Esporta backup dei ricordi", color = accentText)
                }
                    }
                }
            }
        }

        val appInfoInteraction = remember { MutableInteractionSource() }
        Card(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(appInfoInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.985f, label = "settingsAppInfoPress")
                .hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = appInfoInteraction, indication = LocalIndication.current) { expandedSettingsSections = if ("appInfo" in expandedSettingsSections) expandedSettingsSections - "appInfo" else expandedSettingsSections + "appInfo" }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        SettingsCategoryIcon(Icons.Default.Info, Color(0xFF399FC5))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INFORMAZIONI APP", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Dettagli di HaliMeo e della build installata", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("v${BuildConfig.VERSION_NAME}", color = accentText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Icon(if ("appInfo" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = accentText)
                }
                AnimatedVisibility(visible = "appInfo" in expandedSettingsSections, enter = HaliMeoMotion.sectionEnter(reduceAnimationsState.value), exit = HaliMeoMotion.sectionExit(reduceAnimationsState.value)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Developer", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                            Text("MatteoMeo", color = primaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Versione build", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                            Text("${BuildConfig.VERSION_CODE}", color = primaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Canale", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                            Text(if (BuildConfig.DEBUG) "Debug" else "Alpha", color = primaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Package: ${BuildConfig.APPLICATION_ID}", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                        Text("Diario privato condiviso · Android API 37", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth().bringIntoViewRequester(updateCenterRequester)) {
                            UpdateCenterCard(activity)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable { if (developerUnlocked) developerUnlocked = false else { showDeveloperPinDialog = true; developerPin = ""; developerPinError = false } }
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        SettingsCategoryIcon(if (developerUnlocked) Icons.Default.Code else Icons.Default.Lock, Color(0xFF4DA879))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("OPZIONI SVILUPPATORE", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (developerUnlocked) "Pannello sbloccato · tocca per bloccare" else "Strumenti avanzati e configurazione server", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if (developerUnlocked) Icons.Default.ExpandLess else Icons.Default.Lock, contentDescription = null, tint = accentText)
                }
                AnimatedVisibility(visible = developerUnlocked, enter = HaliMeoMotion.sectionEnter(reduceAnimationsState.value), exit = HaliMeoMotion.sectionExit(reduceAnimationsState.value)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SERVER API", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Server predefinito: ${ApiClient.baseUrl()}", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text("STRUMENTI", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(
                            onClick = {
                                serverBusy = true
                                activity.lifecycleScope.launch {
                                    runCatching { repo.syncNow() }
                                        .onSuccess { developerStatus = "Richiesta di sincronizzazione completata." }
                                        .onFailure { developerStatus = "Sincronizzazione non riuscita: ${it.message ?: "errore"}" }
                                    serverBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !serverBusy,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Forza sincronizzazione")
                        }
                        OutlinedButton(
                            onClick = {
                                val report = "HaliMeo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nPackage: ${BuildConfig.APPLICATION_ID}\nServer: ${ApiClient.baseUrl()}\nRicordi locali: ${diaryEntries.size}\nIn attesa di sync: $pendingSyncCount"
                                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Diagnostica HaliMeo", report))
                                Toast.makeText(ctx, "Diagnostica copiata", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copia diagnostica")
                        }
                    }
                }
            }
        }

        if (showDeveloperPinDialog) {
            AlertDialog(
                                shape = RoundedCornerShape(30.dp),
                containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onDismissRequest = { showDeveloperPinDialog = false; developerPin = "" },
                title = { Text("Opzioni sviluppatore") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Inserisci il PIN per sbloccare gli strumenti avanzati.")
                        OutlinedTextField(
                            value = developerPin,
                            onValueChange = { developerPin = it.filter(Char::isDigit).take(4); developerPinError = false },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("PIN") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            isError = developerPinError,
                            shape = RoundedCornerShape(22.dp)
                        ,
                        colors = haliMeoTextFieldColors())
                        if (developerPinError) Text("PIN non corretto.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (developerPin == "0802") {
                            developerUnlocked = true
                            showDeveloperPinDialog = false
                            developerPin = ""
                        } else developerPinError = true
                    }) { Text("Sblocca") }
                },
                dismissButton = { TextButton(onClick = { showDeveloperPinDialog = false; developerPin = "" }) { Text("Annulla") } }
            )
        }

    }
}

}
@Composable
private fun InviteActivationScreen(
    onRedeem: (String, (String) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to Color(0xFF080B19),
                    0.34f to Color(0xFF191A3B),
                    0.68f to Color(0xFF202443),
                    1f to Color(0xFF090D1B)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)
                .border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Text("Account creato!", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Per attivare HaliMeo e accedere al diario, inserisci il codice d'invito che hai ricevuto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.trim() },
                    label = { Text("Codice d'invito") },
                    leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    enabled = !busy,
                    colors = haliMeoTextFieldColors()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = !busy && inviteCode.length >= 16,
                    onClick = {
                        busy = true
                        error = ""
                        onRedeem(inviteCode) { message -> error = message; busy = false }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.VerifiedUser, null)
                    Spacer(Modifier.width(9.dp))
                    Text("ATTIVA ACCOUNT")
                }
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) { Text("Esci da questo account") }
            }
        }
    }
}

@Composable
private fun SocialAuthButton(
    provider: String,
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val isGoogle = provider == "google"
    val background = if (isGoogle) Color.White else Color(0xFF1877F2)
    val foreground = if (isGoogle) Color(0xFF202124) else Color.White
    Row(
        Modifier.fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .graphicsLayer { alpha = if (enabled) 1f else 0.58f }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(29.dp)
                .clip(CircleShape)
                .background(if (isGoogle) Color.Transparent else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(17.dp),
                    color = if (isGoogle) Color(0xFF4285F4) else Color(0xFF1877F2),
                    strokeWidth = 2.dp
                )
            } else if (isGoogle) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_google_logo),
                    contentDescription = "Google",
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    "f",
                    color = Color(0xFF1877F2),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            label,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(29.dp))
    }
}

@Composable
private fun AuthScreen(
    onLogin: (String, String, (String) -> Unit) -> Unit,
    onRegister: (String, String, String, (String) -> Unit) -> Unit,
    onSocialLogin: (String, String, String?, String?, (String) -> Unit) -> Unit
) {
    val authHaze = rememberHazeState()
    val prismalBackdrop = com.styropyr0.prismal.sources.rememberPrismalGlassLayer()
    val prismalDensity = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val fieldShape = RoundedCornerShape(50.dp)

    // Shared flat-satin glass treatment: tinted body, defined rim and restrained top sheen.
    val authFieldModifier = Modifier.fillMaxWidth()
        .shadow(10.dp, fieldShape, ambientColor = accent.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.30f))
        .clip(fieldShape)
        .background(Brush.verticalGradient(listOf(Color(0xFF7C88A5).copy(alpha = 0.25f), Color(0xFF46536F).copy(alpha = 0.34f), Color(0xFF222D45).copy(alpha = 0.46f))), fieldShape)
        .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.24f), fieldShape)
        .drawPrismalGlass(
            backdrop = prismalBackdrop,
            shape = { com.styropyr0.prismal.shapes.PrismalRoundedRectangle(28.dp) },
            effects = prismalGlassEffects(prismalDensity, adaptiveLuminance = true, luminance = 0.34f),
            onDrawSurface = {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF9AA8C5).copy(alpha = 0.13f), accent.copy(alpha = 0.045f), Color(0xFF202B43).copy(alpha = 0.13f), Color(0xFF101827).copy(alpha = 0.19f))))
            }
        )
    val authCardModifier = Modifier.fillMaxWidth()
        .shadow(26.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black.copy(alpha = 0.24f), spotColor = Color.Black.copy(alpha = 0.34f))
        .clip(RoundedCornerShape(36.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF53617E).copy(alpha = 0.25f), Color(0xFF293650).copy(alpha = 0.42f), Color(0xFF111A2B).copy(alpha = 0.52f))))
        .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.26f), RoundedCornerShape(36.dp))
        .drawPrismalGlass(
            backdrop = prismalBackdrop,
            shape = { com.styropyr0.prismal.shapes.PrismalRoundedRectangle(36.dp) },
            effects = prismalGlassEffects(prismalDensity, adaptiveLuminance = true, luminance = 0.32f),
            onDrawSurface = {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF7785A2).copy(alpha = 0.10f), accent.copy(alpha = 0.055f), Color(0xFF111A2B).copy(alpha = 0.22f))))
            }
        )
    val glassStyle = remember {
        GlassStyle {
            backgroundColor(Color(0xFF34415D).copy(alpha = 0.42f))
            tint(Color(0xFF687895).copy(alpha = 0.16f))
            optics(
                GlassOptics.regular.copy(
                    blurRadius = OpticalSizeValue.Fixed(30.dp),
                    refractionStrength = 0.92f,
                    refractionHeightFraction = 0.38f,
                    depth = OpticalSizeValue.Fixed(0.72f)
                )
            )
            edgeSoftness(15.dp)
            shape(RoundedCornerShape(36.dp))
            surfaceProfile(SurfaceProfile.Squircle)
            lightPosition(Alignment.TopStart)
            edgeShadow(Color.Black.copy(alpha = 0.30f))
            specularIntensity(0.82f)
            ambientResponse(0.68f)
        }
    }
    val fieldGlassStyle = remember {
        GlassStyle {
            backgroundColor(Color(0xFF43516D).copy(alpha = 0.34f))
            tint(Color(0xFF7786A4).copy(alpha = 0.13f))
            optics(
                GlassOptics.regular.copy(
                    blurRadius = OpticalSizeValue.Fixed(24.dp),
                    refractionStrength = 0.90f,
                    refractionHeightFraction = 0.36f,
                    depth = OpticalSizeValue.Fixed(0.68f)
                )
            )
            specularIntensity(0.82f)
            ambientResponse(0.78f)
            edgeSoftness(13.dp)
            shape(fieldShape)
            surfaceProfile(SurfaceProfile.Squircle)
            lightPosition(Alignment.TopStart)
            edgeShadow(Color.Black.copy(alpha = 0.28f))
        }
    }
    var redeem by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf(false) }
    var resetCodeSent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val authActivity = LocalContext.current as ComponentActivity
    var socialBusy by remember { mutableStateOf(false) }
    var serverOnline by remember { mutableStateOf<Boolean?>(null) }
    var serverMessage by remember { mutableStateOf("Verifica connessione…") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                ApiClient.api.health()
                serverOnline = true
                serverMessage = "HaliMeo è online"
            } catch (_: Exception) {
                serverOnline = false
                serverMessage = "Server non raggiungibile"
            }
            delay(15000)
        }
    }

    val dark = isHaliMeoDark()

    Box(
        Modifier.fillMaxSize().hazeSource(authHaze)
    ) {
        Box(Modifier.fillMaxSize().background(haliMeoAuthBackdrop()).prismalGlassLayer(prismalBackdrop)) {
        // Layered, theme-aware ambient waves inspired by the concept artwork.
        val atmosphere = haliMeoAtmosphere(customBackgroundColorState.value)
        val waveA = accent
        val waveB = MaterialTheme.colorScheme.secondary
        val waveC = MaterialTheme.colorScheme.tertiary
        if (dark) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset(x = (-175).dp, y = 42.dp)
                    .size(520.dp, 285.dp)
                    .graphicsLayer { rotationZ = -24f }
                    .background(
                        Brush.linearGradient(
                            listOf(waveA.copy(alpha = 0.48f), waveB.copy(alpha = 0.34f), waveC.copy(alpha = 0.20f), Color.Transparent)
                        ),
                        RoundedCornerShape(topStart = 150.dp, topEnd = 120.dp, bottomEnd = 220.dp, bottomStart = 80.dp)
                    )
                    .blur(34.dp)
            )
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .offset(x = 205.dp, y = 35.dp)
                    .size(390.dp, 260.dp)
                    .graphicsLayer { rotationZ = -31f }
                    .background(
                        Brush.linearGradient(
                            listOf(Color.Transparent, waveB.copy(alpha = 0.32f), waveC.copy(alpha = 0.26f), waveA.copy(alpha = 0.38f))
                        ),
                        RoundedCornerShape(topStart = 190.dp, topEnd = 80.dp, bottomEnd = 170.dp, bottomStart = 120.dp)
                    )
                    .blur(38.dp)
            )
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset(x = (-150).dp, y = (-105).dp)
                    .size(470.dp, 230.dp)
                    .graphicsLayer { rotationZ = -19f }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, waveA.copy(alpha = 0.34f), waveB.copy(alpha = 0.28f), waveC.copy(alpha = 0.22f))
                        ),
                        RoundedCornerShape(topStart = 180.dp, topEnd = 110.dp, bottomEnd = 150.dp, bottomStart = 210.dp)
                    )
                    .blur(36.dp)
            )
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 8.dp)
                    .size(230.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.13f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
        }
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(78.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.34f), Color(0xFF171B34).copy(alpha = 0.88f), accent.copy(alpha = 0.18f))
                                )
                            )
                            .border(1.dp, accent.copy(alpha = 0.48f), RoundedCornerShape(27.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "Logo HaliMeo",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent)
                        )
                    }
                    Text(
                        "HaliMeo",
                        style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-0.7).sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Il vostro diario privato",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.padding(top = 2.dp).clip(CircleShape)
                            .background(frostedSurfaceColor().copy(alpha = 0.72f))
                            .border(1.dp, frostedEdgeColor().copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(6.dp).background(
                                when (serverOnline) {
                                    true -> Color(0xFF75D6A2)
                                    false -> Color(0xFFFF8996)
                                    null -> Color(0xFFFFC66D)
                                },
                                CircleShape
                            )
                        )
                        Text(
                            if (serverOnline == true) "ONLINE" else if (serverOnline == false) "OFFLINE" else "CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(112.dp))

            if (recovery) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ritroviamo il tuo accesso.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ti aiutiamo a rientrare nel tuo spazio condiviso.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Card(
                modifier = authCardModifier,
                shape = RoundedCornerShape(36.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF26324B).copy(alpha = 0.62f))
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (recovery || redeem) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (recovery) Icons.Default.Key else if (redeem) Icons.Default.PersonAdd else Icons.Default.Lock,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                if (recovery) "RECUPERO ACCOUNT" else if (redeem) "NUOVO ACCOUNT" else "ACCESSO RISERVATO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.25.sp,
                                color = accent
                            )
                        }
                    }
                    if (recovery) {
                        Text(
                            "Recupera il tuo accesso",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (resetCodeSent)
                                "Inserisci il codice ricevuto via email e scegli una nuova password."
                            else
                                "Ti invieremo un codice temporaneo all'indirizzo associato al tuo account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim() },
                            label = { Text("Indirizzo email") },
                            leadingIcon = { Icon(Icons.Default.MailOutline, null) },
                            modifier = authFieldModifier,
                            shape = fieldShape,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                            ),
                            colors = authTextFieldColors(accent)
                        )
                        if (resetCodeSent) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.filter(Char::isDigit).take(8) },
                                label = { Text("Codice di verifica") },
                                leadingIcon = { Icon(Icons.Default.Pin, null) },
                                modifier = authFieldModifier,
                                shape = fieldShape,
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                ),
                                colors = authTextFieldColors(accent)
                            )
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("Nuova password") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                modifier = authFieldModifier,
                                shape = fieldShape,
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                                ),
                                colors = authTextFieldColors(accent)
                            )
                        }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        if (info.isNotBlank()) Text(info, color = accent)
                        Button(
                            enabled = !busy && email.contains("@") &&
                                (!resetCodeSent || (code.length == 8 && newPassword.length >= 10)),
                            onClick = {
                                busy = true
                                error = ""
                                info = ""
                                scope.launch {
                                    try {
                                        if (!resetCodeSent) {
                                            val result = ApiClient.api.requestPasswordReset(PasswordResetRequest(email))
                                            resetCodeSent = true
                                            info = result.message
                                        } else {
                                            val result = ApiClient.api.confirmPasswordReset(
                                                PasswordResetConfirmRequest(email, code, newPassword)
                                            )
                                            info = result.message
                                            recovery = false
                                            resetCodeSent = false
                                            code = ""
                                            newPassword = ""
                                            pass = ""
                                        }
                                    } catch (e: Exception) {
                                        error = e.message?.substringAfter(": ").orEmpty()
                                            .ifBlank { "Operazione non riuscita. Riprova." }
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                                .shadow(8.dp, RoundedCornerShape(50.dp), ambientColor = accent.copy(alpha = 0.10f))
                                .clip(RoundedCornerShape(50.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF66738F).copy(alpha = 0.30f), Color(0xFF2B3852).copy(alpha = 0.58f))))
                                .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.22f), RoundedCornerShape(50.dp)),
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            else Icon(if (resetCodeSent) Icons.Default.LockReset else Icons.Default.MarkEmailRead, null)
                            Spacer(Modifier.width(9.dp))
                            Text(if (resetCodeSent) "REIMPOSTA PASSWORD" else "INVIA CODICE")
                        }
                        TextButton(
                            onClick = {
                                recovery = false
                                resetCodeSent = false
                                error = ""
                                info = ""
                            },
                            modifier = Modifier.clip(RoundedCornerShape(50.dp))
                                .background(Color(0xFF53617E).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.16f), RoundedCornerShape(50.dp)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Torna all'accesso", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        if (redeem) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = { Text("Come vuoi essere chiamato?") },
                                leadingIcon = { Icon(Icons.Default.PersonOutline, null) },
                                modifier = authFieldModifier,
                                shape = fieldShape,
                                singleLine = true,
                                colors = authTextFieldColors(accent)
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim() },
                            placeholder = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.MailOutline, null) },
                            modifier = authFieldModifier,
                            shape = fieldShape,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                            ),
                            colors = authTextFieldColors(accent)
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            placeholder = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            modifier = authFieldModifier,
                            shape = fieldShape,
                            singleLine = true,
                            visualTransformation = if (passwordVisible)
                                androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.clip(CircleShape)
                                        .background(Color(0xFF53617E).copy(alpha = 0.20f))
                                        .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.16f), CircleShape)
                                ) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Nascondi password" else "Mostra password"
                                    )
                                }
                            },
                            colors = authTextFieldColors(accent)
                        )
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        if (info.isNotBlank()) Text(info, color = accent)

                        if (!redeem) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { recovery = true; error = ""; info = "" },
                                    modifier = Modifier.clip(RoundedCornerShape(50.dp))
                                        .background(Color(0xFF53617E).copy(alpha = 0.22f))
                                        .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.16f), RoundedCornerShape(50.dp)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                                ) { Text("Hai dimenticato la password?", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }

                        val canSubmit = !busy && email.contains("@") && pass.length >= 10 &&
                            (!redeem || name.isNotBlank())
                        Box(
                            Modifier.fillMaxWidth()
                                .height(56.dp)
                                .shadow(16.dp, RoundedCornerShape(50), ambientColor = accent.copy(alpha = 0.34f), spotColor = Color.Black.copy(alpha = 0.30f))
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            accent.copy(alpha = 0.98f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f),
                                            accent.copy(alpha = 0.86f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(50))

                                .clickable(enabled = canSubmit) {
                                    busy = true
                                    val fail: (String) -> Unit = { error = it; busy = false }
                                    if (redeem) onRegister(name, email, pass, fail)
                                    else onLogin(email, pass, fail)
                                }
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    if (redeem) "Crea account" else "Accedi",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }

                        if (!recovery) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Divider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                Text(
                                    "oppure continua con",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Divider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            }
                            SocialAuthButton(
                                provider = "google",
                                label = if (redeem) "Continua con Google" else "Accedi con Google",
                                enabled = !busy && !socialBusy,
                                loading = socialBusy,
                                onClick = {
                                    socialBusy = true
                                    error = ""
                                    info = ""
                                    scope.launch {
                                        try {
                                            val googleCredential = requestGoogleCredential(authActivity)
                                            onSocialLogin(
                                                "google",
                                                googleCredential.idToken,
                                                null,
                                                if (redeem) name.trim().takeIf { it.isNotBlank() } else googleCredential.displayName?.takeIf { it.isNotBlank() }
                                            ) { message -> error = message; socialBusy = false }
                                        } catch (e: GetCredentialException) {
                                            error = e.message?.takeIf { it.isNotBlank() } ?: "Impossibile accedere con Google."
                                            socialBusy = false
                                        } catch (e: Exception) {
                                            error = e.message?.takeIf { it.isNotBlank() } ?: "Impossibile accedere con Google."
                                            socialBusy = false
                                        }
                                    }
                                }
                            )
                            SocialAuthButton(
                                provider = "facebook",
                                label = if (redeem) "Continua con Facebook" else "Accedi con Facebook",
                                enabled = !busy && !socialBusy,
                                loading = socialBusy,
                                onClick = {
                                    socialBusy = true
                                    error = ""
                                    info = ""
                                    val hostActivity = authActivity as? MainActivity
                                    if (hostActivity == null) {
                                        error = "Accesso Facebook non disponibile."
                                        socialBusy = false
                                    } else {
                                        hostActivity.startFacebookLogin(
                                            onToken = { token ->
                                                onSocialLogin(
                                                    "facebook",
                                                    token,
                                                    null,
                                                    if (redeem) name.trim().takeIf { it.isNotBlank() } else null
                                                ) { message -> error = message; socialBusy = false }
                                            },
                                            onFacebookCancel = { socialBusy = false },
                                            onFacebookError = { message -> error = message; socialBusy = false }
                                        )
                                    }
                                }
                            )

                        }

                        if (redeem) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(alpha = 0.08f)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Shield, null, tint = accent)
                                Text(
                                    "Dopo la creazione dell'account, inserirai il codice d'invito per attivarlo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF65728F).copy(alpha = 0.26f), Color(0xFF26324B).copy(alpha = 0.48f))))
                                .border(1.dp, Color(0xFFB9C8E2).copy(alpha = 0.20f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (redeem) "Hai già un account? " else "Non hai un account? ",
                                color = Color(0xFFD6D2E8),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (redeem) "Accedi" else "Registrati",
                                color = accent,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    redeem = !redeem
                                    error = ""
                                    info = ""
                                    pass = ""
                                }.padding(horizontal = 4.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun categoryAccent(category: String, memorySeed: String = category): Color {
    val dark = isHaliMeoDark()
    // Stable per-memory palette: visually varied, but never changes during recomposition.
    val memoryPalette = if (dark) listOf(
        Color(0xFF83C9FF), Color(0xFFFF9FBD), Color(0xFFFFC878),
        Color(0xFFB9A5FF), Color(0xFF8DE0C1), Color(0xFF7FE1E8),
        Color(0xFFFFA98F), Color(0xFFD5A6FF), Color(0xFFA9D77D),
        Color(0xFFCE93D8), Color(0xFF80CBC4), Color(0xFFDCE775),
        Color(0xFFFFD54F), Color(0xFF90CAF9), Color(0xFFFFAB91),
        Color(0xFFF48FB1), Color(0xFFB0BEC5), Color(0xFF80DEEA),
        Color(0xFFC5E1A5), Color(0xFFB39DDB), Color(0xFFFFCC80)
    ) else listOf(
        Color(0xFF175E91), Color(0xFF9C3155), Color(0xFF855000),
        Color(0xFF5D42A3), Color(0xFF17694F), Color(0xFF126B73),
        Color(0xFF9A4933), Color(0xFF7545A0), Color(0xFF4B6F24),
        Color(0xFF7B3F8C), Color(0xFF087F75), Color(0xFF617D16),
        Color(0xFF8A6500), Color(0xFF315A9B), Color(0xFFB85D35),
        Color(0xFFAD456B), Color(0xFF546E7A), Color(0xFF007C91),
        Color(0xFF527A32), Color(0xFF6650A4), Color(0xFF9A5A00)
    )
    return memoryPalette[(memorySeed.hashCode() and Int.MAX_VALUE) % memoryPalette.size]
}

private fun categoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
    val normalized = category.trim().lowercase()
    return when {
        listOf("viagg", "vacanz", "gita", "monaco", "avventura").any(normalized::contains) -> Icons.Default.FlightTakeoff
        listOf("compleanno", "anniversario", "festa", "regalo").any(normalized::contains) -> Icons.Default.Celebration
        listOf("cena", "ristorante", "cibo", "pranzo", "aperitivo").any(normalized::contains) -> Icons.Default.Restaurant
        listOf("foto", "fotografia", "immagine").any(normalized::contains) -> Icons.Default.PhotoCamera
        else -> Icons.Default.AutoAwesome
    }
}

@Composable
private fun DiaryScreen(
    entries: List<DiaryEntry>,
    repo: DiaryRepository,
    onOpenMemory: (DiaryEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    val calendarState = rememberDatePickerState()
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var socialEntries by remember { mutableStateOf<Map<String, it.halimeo.app.network.SocialFeedEntry>>(emptyMap()) }
    var commentsEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var comments by remember { mutableStateOf<List<it.halimeo.app.network.SocialComment>>(emptyList()) }
    var commentDraft by remember { mutableStateOf("") }
    var commentsBusy by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf("") }
    var albumEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var albums by remember { mutableStateOf<List<it.halimeo.app.network.SocialAlbum>>(emptyList()) }
    var albumTitle by remember { mutableStateOf("") }
    var albumBusy by remember { mutableStateOf(false) }
    var albumsBrowserOpen by remember { mutableStateOf(false) }
    var albumBrowserTitle by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf<it.halimeo.app.network.SocialAlbum?>(null) }
    var albumDetail by remember { mutableStateOf<it.halimeo.app.network.SocialAlbumDetail?>(null) }
    var albumDetailBusy by remember { mutableStateOf(false) }
    var storyIndex by remember { mutableIntStateOf(-1) }
    var coverPickerTarget by remember { mutableStateOf<String?>(null) }
    val coverPrefsContext = LocalContext.current
    val highlightCoverOverrides = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            coverPrefsContext.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                .getStringSet("halimeo_highlight_covers", emptySet()).orEmpty().forEach { value ->
                    val parts = value.split("|", limit = 2)
                    if (parts.size == 2) put(parts[0], parts[1])
                }
        }
    }
    val socialScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { currentUserId = repo.currentUserId() }
    LaunchedEffect(entries.map { it.updatedAt }) {
        val localFeed = runCatching { repo.loadLocalSocialFeed().associateBy { it.id } }.getOrDefault(emptyMap())
        val remoteFeed = runCatching { repo.loadSocialFeed().entries.associateBy { it.id } }.getOrNull()
        socialEntries = if (remoteFeed != null) localFeed + remoteFeed else localFeed
    }
    val diaryHaze = rememberHazeState()
    val diaryGlassTint = frostedSurfaceColor()
    val diaryGlassStyleBackground = MaterialTheme.colorScheme.surface
    val diaryGlassStyle = remember(diaryGlassTint) {
        HazeBlurStyle {
            backgroundColor(diaryGlassStyleBackground.copy(alpha = 0.68f))
            colorEffects(listOf(HazeColorEffect.tint(diaryGlassTint.copy(alpha = 0.24f))))
            fallbackColorEffect(HazeColorEffect.tint(diaryGlassTint.copy(alpha = 0.46f)))
        }
    }
    val diaryContext = LocalContext.current
    val highlightsTitle = "Highlights"
    val feedbackView = LocalView.current
    val listState = rememberLazyListState()

    // Keep the complete photo set outside LazyColumn item composition. Lazy items are
    // disposed when scrolled off-screen; decoding inside each item caused repeated
    // downloads and visible image/card height changes on every scroll.
    var diaryPhotoSet by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    val photoBatchKey = remember(entries) { entries.map { it.id to it.photoUri } }
    LaunchedEffect(photoBatchKey) {
        val wanted = entries.filter { !it.photoUri.isNullOrBlank() }
        val wantedIds = wanted.mapTo(mutableSetOf()) { it.id }
        val nextPhotos = diaryPhotoSet.filterKeys { it in wantedIds }.toMutableMap()
        val missing = wanted.filter { entry ->
            val cached = nextPhotos[entry.id]
            cached == null
        }
        if (missing.isNotEmpty()) {
            val semaphore = kotlinx.coroutines.sync.Semaphore(permits = 2)
            val decoded = kotlinx.coroutines.coroutineScope {
                missing.map { entry ->
                    async {
                        semaphore.withPermit {
                            entry.id to decodeDiaryPhoto(
                                diaryContext,
                                entry.id,
                                entry.photoUri,
                                maxDimension = 1080,
                                repo = repo
                            )
                        }
                    }
                }.awaitAll()
            }
            decoded.forEach { (id, bitmap) -> if (bitmap != null) nextPhotos[id] = bitmap }
        }
        // Publish one completed batch, not one card at a time. Existing images stay
        // visible while newly added/changed photos are being fetched.
        diaryPhotoSet = nextPhotos
    }
    // Cache diary header data and parsed special dates instead of recomputing them
    // whenever Compose recomposes the LazyColumn.
    val memoriesToday = remember(entries) {
        val today = java.time.LocalDate.now()
        entries.mapNotNull { entry ->
            parseHaliMeoDate(entry.memoryDate)?.takeIf { it.year < today.year && it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth }?.let { entry to it }
        }.sortedByDescending { it.second }.map { it.first }
    }
    val photoHighlights = remember(entries) {
        entries.filter { !it.photoUri.isNullOrBlank() }
            .sortedByDescending { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }
            .take(12)
    }
    val upcomingSpecial = remember(entries) {
        val prefs = diaryContext.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
        readHaliMeoSpecialDates(prefs).mapNotNull { item ->
            parseHaliMeoDate(item.date)?.let { date ->
                val today = java.time.LocalDate.now()
                var next = date.withYear(today.year)
                if (next.isBefore(today)) next = next.plusYears(1)
                Triple(item, next, java.time.temporal.ChronoUnit.DAYS.between(today, next))
            }
        }.minByOrNull { it.third }
    }
    var headerCollapsed by remember { mutableStateOf(false) }
    // Collapse only after the list has actually moved; expand only when returning
    // close to the top. This hysteresis prevents tiny finger movements from toggling
    // the large header repeatedly and keeps layout/visibility animations off the hot path.
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(listState, density) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (firstIndex, firstOffset) ->
            val offsetDp = with(density) { firstOffset.toDp().value }
            val shouldCollapse = firstIndex > 0 || offsetDp > 52f
            val shouldExpand = firstIndex == 0 && offsetDp < 10f
            if (shouldCollapse && !headerCollapsed) headerCollapsed = true
            else if (shouldExpand && headerCollapsed) headerCollapsed = false
        }
    }
    // Scale the title in the draw phase rather than animating fontSize, which
    // repeatedly measures the header while the diary is scrolling.
    val headerTitleScale by animateFloatAsState(
        targetValue = if (headerCollapsed) 0.76f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(90, easing = OneUiMotionEasing),
        label = "diaryHeaderTitleScale"
    )
    val filtered = remember(entries, query, selectedDate) {
        entries.asSequence().filter { entry ->
            (entry.title.contains(query, true) || entry.body.contains(query, true)) &&
                (selectedDate == null || entry.memoryDate == selectedDate)
        }.sortedByDescending { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }.toList()
    }

    if (showCalendar) {
        DatePickerDialog(
                        onDismissRequest = { showCalendar = false },
            confirmButton = { TextButton(onClick = { selectedDate = calendarState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }; showCalendar = false }) { Text("Filtra") } },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Annulla") } },
            colors = DatePickerDefaults.colors(containerColor = haliMeoPopupSurfaceColor())
        ) {
            DatePicker(
                state = calendarState,
                colors = DatePickerDefaults.colors(
                    containerColor = haliMeoPopupSurfaceColor(),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    currentYearContentColor = MaterialTheme.colorScheme.primary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    disabledSelectedDayContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
                    dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(if (headerCollapsed) 2.dp else 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Il vostro diario",
                    modifier = Modifier.graphicsLayer {
                        scaleX = headerTitleScale
                        scaleY = headerTitleScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 33.sp, letterSpacing = (-0.9).sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "I momenti che volete tenere vicini.",
                    modifier = Modifier.height(if (headerCollapsed) 0.dp else 22.dp).graphicsLayer {
                        alpha = if (headerCollapsed) 0f else 1f
                        translationY = if (headerCollapsed) -4.dp.toPx() else 0f
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            calendarState.selectedDateMillis = selectedDate?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
                            showCalendar = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Filtra per data", modifier = Modifier.size(19.dp), tint = if (selectedDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary)
                    }
                }
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            albumsBrowserOpen = true
                            albumBusy = true
                            socialScope.launch {
                                albums = runCatching { repo.loadAlbums().albums }.getOrDefault(emptyList())
                                albumBusy = false
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.CollectionsBookmark, contentDescription = "Apri album", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) query = ""
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchExpanded) "Chiudi ricerca" else "Cerca ricordi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth()
                    .hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle)
                    .graphicsLayer { shape = RoundedCornerShape(22.dp); clip = true }
                    .clip(RoundedCornerShape(22.dp)),
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                placeholder = { Text("Cerca un ricordo…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella ricerca")
                        }
                    }
                },
                colors = haliMeoTextFieldColors()
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = selectedDate != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { selectedDate = null }, label = { Text("Data: ${selectedDate?.let(::displayHaliMeoDate)} · Rimuovi filtro") }, leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) })
            }
        }
        when {
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(18.dp).size(32.dp)
                                )
                            }
                            Text(
                                "La vostra storia comincia qui",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Salvate il primo momento speciale. I ricordi vengono conservati sul dispositivo e sincronizzati quando possibile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Text("Nessun ricordo trovato", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text("Prova con un'altra parola.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    if (query.isBlank()) {
                        if (photoHighlights.isNotEmpty()) {
                            item(key = "memory_highlights") {
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text("Highlights", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    androidx.compose.foundation.lazy.LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(photoHighlights, key = { _, item -> item.id }) { index, highlight ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                                val coverId = highlightCoverOverrides[highlight.id] ?: highlight.id
                                                val storyBitmap = diaryPhotoSet[coverId] ?: diaryPhotoSet[highlight.id]
                                                Box(
                                                    Modifier.size(66.dp).pointerInput(highlight.id) {
                                                        detectTapGestures(
                                                            onTap = { storyIndex = index },
                                                            onLongPress = { coverPickerTarget = highlight.id }
                                                        )
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        Modifier.fillMaxSize().clip(CircleShape)
                                                            .background(Brush.sweepGradient(listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.tertiary,
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                                MaterialTheme.colorScheme.primary
                                                            )))
                                                            .padding(3.dp)
                                                    ) {
                                                        Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                                                            if (storyBitmap != null) Image(
                                                                bitmap = storyBitmap.asImageBitmap(),
                                                                contentDescription = "Apri ${highlight.title}",
                                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            ) else Icon(Icons.Default.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp).align(Alignment.Center))
                                                        }
                                                    }
                                                    if (highlightCoverOverrides.containsKey(highlight.id)) {
                                                        Box(Modifier.align(Alignment.BottomEnd).size(19.dp).clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary).border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                                            contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Copertina personalizzata", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp))
                                                        }
                                                    }
                                                }
                                                Text(highlight.title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }                                   }
                                }
                            }
                        }
                        if (memoriesToday.isNotEmpty()) {
                            item(key = "today_in_history") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text("Oggi nella vostra storia", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    memoriesToday.forEach { memory ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpenMemory(memory) },
                                            shape = RoundedCornerShape(18.dp), color = frostedSurfaceStrongColor(),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor())
                                        ) {
                                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(memory.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(displayHaliMeoDate(memory.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    itemsIndexed(filtered, key = { _, entry -> entry.id }, contentType = { _, _ -> "diary_entry" }) { _, entry ->
                        val expanded = entry.id in expandedEntryIds
                        val accent = MaterialTheme.colorScheme.primary
                        val entryPhoto = diaryPhotoSet[entry.id]
                        val social = socialEntries[entry.id]
                        val authorId = social?.authorId?.takeIf { it.isNotBlank() } ?: currentUserId
                        val authorAvatar by produceState<android.graphics.Bitmap?>(
                            initialValue = null, authorId, repo, profilePhotoRefreshState.value
                        ) {
                            value = if (authorId.isBlank()) null else runCatching {
                                repo.loadProfilePhotoBytes(authorId)?.let { bytes ->
                                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                            }.getOrNull()
                        }
                        SocialMemoryPost(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(150, easing = OneUiMotionEasing),
                                fadeOutSpec = tween(110, easing = OneUiMotionEasing),
                                placementSpec = HaliMeoMotion.listSpring()
                            ),
                            hazeState = diaryHaze,
                            glassStyle = diaryGlassStyle,
                            entry = entry,
                            authorName = social?.authorName ?: "Diario condiviso",
                            authorAvatar = authorAvatar,
                            photo = entryPhoto,
                            expanded = expanded,
                            likeCount = social?.likeCount ?: 0,
                            likedByMe = social?.likedByMe ?: false,
                            commentCount = social?.commentCount ?: 0,
                            latestComments = social?.comments.orEmpty(),
                            socialEnabled = entry.syncState == "SYNCED",
                            onToggleLike = {
                                val currentSocial = socialEntries[entry.id] ?: it.halimeo.app.network.SocialFeedEntry(
                                    id = entry.id, title = entry.title, body = entry.body, category = entry.category,
                                    memoryDate = entry.memoryDate, createdAt = entry.createdAt, updatedAt = entry.updatedAt,
                                    authorId = currentUserId, authorName = "Tu", likeCount = 0, likedByMe = false
                                )
                                val wasLiked = currentSocial.likedByMe
                                val optimistic = currentSocial.copy(
                                    likeCount = (currentSocial.likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
                                    likedByMe = !wasLiked
                                )
                                socialEntries = socialEntries + (entry.id to optimistic)
                                socialScope.launch {
                                    runCatching { repo.setEntryLiked(entry.id, !wasLiked) }
                                        .onFailure { socialEntries = socialEntries + (entry.id to currentSocial) }
                                        .onSuccess { runCatching { repo.loadSocialFeed().entries.associateBy { it.id } }.onSuccess { socialEntries = it } }
                                }
                            },
                            onComments = {
                                commentsEntry = entry
                                commentsBusy = true
                                socialScope.launch {
                                    comments = runCatching { repo.loadEntryComments(entry.id) }.getOrDefault(emptyList())
                                    commentsBusy = false
                                }
                            },
                            onAddToAlbum = {
                                albumEntry = entry
                                albumBusy = true
                                socialScope.launch {
                                    albums = runCatching { repo.loadAlbums().albums }.getOrDefault(emptyList())
                                    albumBusy = false
                                }
                            },
                            onOpen = { onOpenMemory(entry) },
                            onToggleCaption = {
                                expandedEntryIds = if (expanded) expandedEntryIds - entry.id else expandedEntryIds + entry.id
                            },
                        )
                    }
                }
            }
        }
    }
    }
    if (albumEntry != null) {
        AlertDialog(
                        shape = RoundedCornerShape(30.dp),
            containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { albumEntry = null },
            title = { Text("Aggiungi a un album") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (albumBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    else if (albums.isEmpty()) Text("Non avete ancora creato album.")
                    else albums.forEach { album ->
                        TextButton(onClick = {
                            val target = albumEntry ?: return@TextButton
                            albumBusy = true
                            socialScope.launch {
                                runCatching { repo.addEntryToAlbum(album.id, target.id) }
                                albumBusy = false
                                albumEntry = null
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CollectionsBookmark, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(album.title, fontWeight = FontWeight.SemiBold)
                                    Text("${album.entryCount} ricordi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(value = albumTitle, onValueChange = { albumTitle = it.take(100) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Nome del nuovo album") }, singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        colors = haliMeoTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(enabled = albumTitle.isNotBlank() && !albumBusy, onClick = {
                    val target = albumEntry ?: return@TextButton
                    albumBusy = true
                    socialScope.launch {
                        runCatching { repo.createAlbum(albumTitle.trim()) }
                            .onSuccess { repo.addEntryToAlbum(it.id, target.id); albumTitle = "" }
                        albumBusy = false
                        albumEntry = null
                    }
                }) { Text("Crea e aggiungi") }
            },
            dismissButton = { TextButton(onClick = { albumEntry = null }) { Text("Chiudi") } }
        )
    }
    if (albumsBrowserOpen) {
        AlertDialog(
                        shape = RoundedCornerShape(30.dp),
            containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { albumsBrowserOpen = false },
            title = { Text("I vostri album") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = albumBrowserTitle,
                        onValueChange = { albumBrowserTitle = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Crea un album") },
                        singleLine = true
                    ,
                        shape = RoundedCornerShape(22.dp),
                        colors = haliMeoTextFieldColors())
                    if (albumBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    else if (albums.isEmpty()) Text("Ancora nessun album. Create il primo per raccogliere i vostri momenti.")
                    else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        albums.forEach { album ->
                            TextButton(onClick = {
                                selectedAlbum = album
                                albumsBrowserOpen = false
                                albumDetailBusy = true
                                socialScope.launch {
                                    albumDetail = runCatching { repo.loadAlbum(album.id) }.getOrNull()
                                    albumDetailBusy = false
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CollectionsBookmark, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(album.title, fontWeight = FontWeight.SemiBold)
                                        Text("${album.entryCount} ricordi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = albumBrowserTitle.isNotBlank() && !albumBusy, onClick = {
                    albumBusy = true
                    socialScope.launch {
                        runCatching { repo.createAlbum(albumBrowserTitle.trim()) }
                            .onSuccess { created ->
                                albumBrowserTitle = ""
                                albums = runCatching { repo.loadAlbums().albums }.getOrDefault(albums + created)
                            }
                        albumBusy = false
                    }
                }) { Text("Crea album") }
            },
            dismissButton = { TextButton(onClick = { albumsBrowserOpen = false }) { Text("Chiudi") } }
        )
    }
    if (selectedAlbum != null) {
        AlertDialog(
                        shape = RoundedCornerShape(30.dp),
            containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { selectedAlbum = null; albumDetail = null },
            title = { Text(albumDetail?.title ?: selectedAlbum?.title ?: "Album") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (albumDetailBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    else if (albumDetail == null) Text("Impossibile caricare questo album. Controllate la connessione e riprovate.")
                    else if (albumDetail!!.entries.isEmpty()) Text("Questo album è ancora vuoto.")
                    else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        albumDetail!!.entries.forEach { item ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(frostedSurfaceColor()).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {
                                    entries.firstOrNull { it.id == item.id }?.let(onOpenMemory)
                                    selectedAlbum = null
                                    albumDetail = null
                                }) {
                                    Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("${item.authorName} · ${displayHaliMeoDate(item.memoryDate)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = {
                                    val target = selectedAlbum ?: return@IconButton
                                    albumDetailBusy = true
                                    socialScope.launch {
                                        runCatching { repo.removeEntryFromAlbum(target.id, item.id) }
                                        albumDetail = runCatching { repo.loadAlbum(target.id) }.getOrNull()
                                        albums = runCatching { repo.loadAlbums().albums }.getOrDefault(albums)
                                        albumDetailBusy = false
                                    }
                                }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Rimuovi dall'album", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedAlbum = null; albumDetail = null }) { Text("Fatto") } }
        )
    }
    if (commentsEntry != null) {
        AlertDialog(
                        shape = RoundedCornerShape(30.dp),
            containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { commentsEntry = null },
            title = { Text("Commenti") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (commentsBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    else if (comments.isEmpty()) Text("Ancora nessun commento. Inizia tu!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        comments.forEach { comment ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(frostedSurfaceColor()).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(comment.authorName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (comment.userId == currentUserId) {
                                    IconButton(onClick = {
                                        val target = commentsEntry ?: return@IconButton
                                        comments = comments.filterNot { it.id == comment.id }
                                        socialScope.launch {
                                            runCatching { repo.deleteEntryComment(target.id, comment.id) }
                                            val existing = socialEntries[target.id]
                                            if (existing != null) socialEntries = socialEntries + (target.id to existing.copy(
                                                commentCount = (existing.commentCount - 1).coerceAtLeast(0),
                                                comments = existing.comments.filterNot { it.id == comment.id }
                                            ))
                                        }
                                    }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina commento", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = commentDraft, onValueChange = { commentDraft = it.take(4000) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Scrivi un commento") }, maxLines = 3,
                        shape = RoundedCornerShape(22.dp),
                        colors = haliMeoTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(enabled = commentDraft.isNotBlank() && !commentsBusy, onClick = {
                    val target = commentsEntry ?: return@TextButton
                    val draft = commentDraft.trim()
                    commentsBusy = true
                    socialScope.launch {
                        runCatching { repo.addEntryComment(target.id, draft) }
                            .onSuccess { response ->
                                comments = comments + it.halimeo.app.network.SocialComment(
                                    id = response.id,
                                    body = response.body,
                                    userId = response.userId,
                                    authorName = response.authorName,
                                    createdAt = response.createdAt,
                                    updatedAt = response.updatedAt
                                )
                                commentDraft = ""
                                val existing = socialEntries[target.id]
                                if (existing != null) {
                                    val appended = it.halimeo.app.network.SocialComment(
                                        id = response.id, body = response.body, userId = response.userId,
                                        authorName = response.authorName, createdAt = response.createdAt, updatedAt = response.updatedAt
                                    )
                                    socialEntries = socialEntries + (target.id to existing.copy(
                                        commentCount = existing.commentCount + 1,
                                        comments = existing.comments + appended
                                    ))
                                }
                            }
                        commentsBusy = false
                    }
                }) { Text("Invia") }
            },
            dismissButton = { TextButton(onClick = { commentsEntry = null }) { Text("Chiudi") } }
        )
    }

    // Full-screen private story viewer. Tap either side of the photo to move between memories.
    if (storyIndex in photoHighlights.indices) {
        val story = photoHighlights[storyIndex]
        val storyBitmap = diaryPhotoSet[highlightCoverOverrides[story.id] ?: story.id] ?: diaryPhotoSet[story.id]
        Dialog(
            onDismissRequest = { storyIndex = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
                if (storyBitmap != null) {
                    Image(
                        bitmap = storyBitmap.asImageBitmap(),
                        contentDescription = story.title,
                        modifier = Modifier.fillMaxSize().pointerInput(storyIndex, photoHighlights.size) {
                            detectTapGestures { tap ->
                                if (tap.x < size.width * 0.34f) storyIndex = (storyIndex - 1).coerceAtLeast(0)
                                else storyIndex = (storyIndex + 1).coerceAtMost(photoHighlights.lastIndex)
                            }
                        },
                        contentScale = ContentScale.Fit
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.58f),
                                0.18f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
                )
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        photoHighlights.forEachIndexed { index, _ ->
                            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (index <= storyIndex) 0.96f else 0.34f)))
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("I vostri Highlights", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(displayHaliMeoDate(story.memoryDate), color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { storyIndex = -1 }) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi Highlights", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(story.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (story.body.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(story.body, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { storyIndex = -1; onOpenMemory(story) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Apri ricordo")
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }

    // Long-press a Highlight to choose a different cover from the photos already in the diary.
    if (coverPickerTarget != null) {
        val targetId = coverPickerTarget!!
        val target = photoHighlights.firstOrNull { it.id == targetId }
        AlertDialog(
            onDismissRequest = { coverPickerTarget = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = haliMeoPopupSurfaceColor(),
            title = { Text("Copertina Highlight") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Scegli una foto del vostro diario come copertina. La fotografia originale del ricordo non verrà modificata.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        highlightCoverOverrides.remove(targetId)
                        val prefs = coverPrefsContext.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("halimeo_highlight_covers", highlightCoverOverrides.entries.map { "${it.key}|${it.value}" }.toSet()).apply()
                        coverPickerTarget = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ripristina copertina originale", modifier = Modifier.fillMaxWidth())
                    }
                    entries.filter { !it.photoUri.isNullOrBlank() && it.id != targetId }.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                highlightCoverOverrides[targetId] = candidate.id
                                val prefs = coverPrefsContext.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putStringSet("halimeo_highlight_covers", highlightCoverOverrides.entries.map { "${it.key}|${it.value}" }.toSet()).apply()
                                coverPickerTarget = null
                            }.padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val coverBitmap = diaryPhotoSet[candidate.id]
                            if (coverBitmap != null) Image(coverBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                            else Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Photo, contentDescription = null)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(candidate.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(displayHaliMeoDate(candidate.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { coverPickerTarget = null }) { Text("Fine") } }
        )
    }
}


@Composable
private fun SocialMemoryPost(
    entry: DiaryEntry, authorName: String, authorAvatar: android.graphics.Bitmap? = null, photo: android.graphics.Bitmap?, expanded: Boolean,
    likeCount: Int = 0, likedByMe: Boolean = false, commentCount: Int = 0,
    latestComments: List<it.halimeo.app.network.SocialComment> = emptyList(), socialEnabled: Boolean = true,
    onToggleLike: () -> Unit = {}, onComments: () -> Unit = {}, onAddToAlbum: () -> Unit = {}, onOpen: () -> Unit, onToggleCaption: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    glassStyle: HazeBlurStyle
) {
    val accent = MaterialTheme.colorScheme.primary
    // Convert cache-owned bitmaps once per photo change, not on every recomposition.
    val photoImage = remember(photo) { photo?.asImageBitmap() }
    val avatarImage = remember(authorAvatar) { authorAvatar?.asImageBitmap() }
    val initials = authorName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "H" }
    val dateLabel = displayHaliMeoDate(entry.memoryDate)
    val synced = entry.syncState == "SYNCED" && entry.photoSyncState == "SYNCED"
    val likeScale by animateFloatAsState(
        targetValue = if (likedByMe) 1.12f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.68f, stiffness = 620f),
        label = "postLikeScale"
    )
    var showHeartBurst by remember(entry.id) { mutableStateOf(false) }
    val heartBurstScale by animateFloatAsState(
        targetValue = if (showHeartBurst) 1f else 0.42f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.55f, stiffness = 340f),
        label = "socialHeartBurstScale"
    )
    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650)
            showHeartBurst = false
        }
    }
    val cardShape = RoundedCornerShape(30.dp)
    val actionSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isHaliMeoDark()) 0.48f else 0.72f)

    Column(
        modifier.fillMaxWidth()
            // Keep the feed card's measured height stable while likes/comments update.
            .shadow(if (isHaliMeoDark()) 9.dp else 4.dp, cardShape)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        frostedSurfaceStrongColor().copy(alpha = 0.98f),
                        frostedSurfaceColor().copy(alpha = 0.96f),
                        frostedSurfaceStrongColor().copy(alpha = 0.99f)
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = if (isHaliMeoDark()) 0.22f else 0.14f), cardShape)
    ) {
        if (photo != null && photoImage != null) {
            // Immersive editorial cover: original photo stays fully visible in front,
            // while a softened version fills the stage behind it.
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .pointerInput(entry.id) {
                        detectTapGestures(
                            onTap = { onOpen() },
                            onDoubleTap = {
                                if (socialEnabled) {
                                    showHeartBurst = true
                                    if (!likedByMe) onToggleLike()
                                }
                            }
                        )
                    }
            ) {
                val naturalHeight = maxWidth * (photo.height.toFloat() / photo.width.toFloat())
                val frameHeight = naturalHeight.coerceIn(270.dp, 520.dp)
                Box(
                    Modifier.fillMaxWidth().height(frameHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    // A low-cost ambient photo layer avoids a full-resolution GPU blur
                    // on every visible feed card while preserving the immersive backdrop.
                    Image(
                        bitmap = photoImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = 0.22f
                            scaleX = 1.045f
                            scaleY = 1.045f
                        },
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
                    Image(
                        bitmap = photoImage,
                        contentDescription = "Foto del ricordo: \${entry.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.34f),
                                0.22f to Color.Transparent,
                                0.58f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
                    Row(
                        Modifier.align(Alignment.TopStart).fillMaxWidth()
                            .padding(start = 13.dp, top = 13.dp, end = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f, fill = false)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.36f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(start = 5.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(31.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(accent, MaterialTheme.colorScheme.tertiary))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarImage != null) {
                                    Image(avatarImage, contentDescription = "Foto profilo di $authorName",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    Text(initials, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(authorName.ifBlank { "HaliMeo" }, color = Color.White,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(dateLabel, color = Color.White.copy(alpha = 0.76f),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.36f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                if (synced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                contentDescription = if (synced) "Ricordo sincronizzato" else "In attesa di sincronizzazione",
                                tint = if (synced) Color.White else Color(0xFFFFD18A),
                                modifier = Modifier.padding(9.dp).size(17.dp)
                            )
                        }
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.width(22.dp).height(2.dp).clip(CircleShape).background(accent))
                            Text(
                                entry.category.ifBlank { "UN MOMENTO DA RICORDARE" }.uppercase(),
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.25.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            entry.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.65).sp, lineHeight = 31.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.body.isNotBlank()) {
                            Text(
                                entry.body,
                                modifier = Modifier.animateContentSize(animationSpec = HaliMeoMotion.contentSizeSpring),
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                maxLines = if (expanded) 5 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (showHeartBurst) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Mi piace",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                                scaleX = heartBurstScale
                                scaleY = heartBurstScale
                                alpha = (1f - ((heartBurstScale - 0.42f) / 0.58f)).coerceIn(0.25f, 1f)
                                shadowElevation = 22.dp.toPx()
                            }.size(88.dp)
                        )
                    }
                    if (likedByMe) {
                        Surface(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 13.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.40f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Ti piace", tint = Color(0xFFFF8CA6),
                                modifier = Modifier.padding(9.dp).size(18.dp))
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 230.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.42f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.26f), frostedSurfaceStrongColor())
                        )
                    ).clickable(onClick = onOpen)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = accent.copy(alpha = 0.16f), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(140.dp))
                Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("UNA STORIA DA RICORDARE", color = accent, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(entry.title, color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.7).sp),
                        fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (entry.body.isNotBlank()) Text(entry.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("$dateLabel  ·  ${entry.category.ifBlank { "Ricordo" }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (likedByMe) Color(0xFFE66F8B).copy(alpha = 0.15f) else actionSurface)
                    .clickable(enabled = socialEnabled) { showHeartBurst = true; onToggleLike() }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(if (likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (likedByMe) "Rimuovi mi piace" else "Mi piace",
                    tint = if (likedByMe) Color(0xFFE66F8B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale })
                Text(likeCount.toString(), color = if (likedByMe) Color(0xFFE66F8B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(actionSurface)
                    .clickable(enabled = socialEnabled, onClick = onComments)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.ModeComment, contentDescription = "Commenti",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(commentCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.13f))
                    .border(1.dp, accent.copy(alpha = 0.22f), CircleShape)
                    .clickable(enabled = socialEnabled, onClick = onAddToAlbum),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Aggiungi a un album",
                    tint = accent, modifier = Modifier.size(19.dp))
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(actionSurface)
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Apri ricordo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        if (latestComments.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 17.dp, end = 17.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                latestComments.takeLast(2).forEach { comment ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(comment.authorName, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(comment.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                Text("Visualizza tutti i commenti", color = accent, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onComments))
            }
        }
    }
}
@Composable
private fun MemoryDetailScreen(
    entry: DiaryEntry,
    repo: DiaryRepository,
    canManage: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember(entry.id) { mutableStateOf(false) }
    var detailComments by remember(entry.id) { mutableStateOf<List<it.halimeo.app.network.SocialComment>>(emptyList()) }
    var commentsLoading by remember(entry.id) { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(entry.id) {
        commentsLoading = true
        detailComments = runCatching { repo.loadEntryComments(entry.id) }.getOrDefault(emptyList())
        commentsLoading = false
    }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, entry.photoUri) {
        value = decodeDiaryPhoto(context, entry.id, entry.photoUri, maxDimension = 1800, repo = repo)
    }
    val shape = RoundedCornerShape(32.dp)
    val photoAlpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(100, easing = OneUiMotionEasing),
        label = "detailPhotoReveal"
    )
    val detailPhotoScale by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0.965f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.84f, stiffness = 420f),
        label = "detailPhotoSharedMotion"
    )

    Box(Modifier.fillMaxSize()) {
    HaliMeoAmbientBackdrop(Modifier.fillMaxSize())
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "IL VOSTRO DIARIO",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            if (canManage) {
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica ricordo",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina ricordo",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        ) {
            val detailFrameHeight = if (bitmap != null) {
                (maxWidth * (bitmap!!.height.toFloat() / bitmap!!.width.toFloat())).coerceAtMost(560.dp)
            } else 260.dp
            Box(Modifier.fillMaxWidth().height(detailFrameHeight), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Foto del ricordo",
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = photoAlpha },
                    contentScale = ContentScale.Fit
                )
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(150.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))))
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Photo, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(42.dp))
                        Text("Nessuna foto per questo ricordo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (bitmap != null) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("UN MOMENTO DA RICORDARE",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp)
                    Text(displayHaliMeoDate(entry.memoryDate),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                entry.title.ifBlank { "Senza titolo" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Text(displayHaliMeoDate(entry.memoryDate),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (entry.body.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, frostedEdgeColor(), RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("IL RICORDO", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp)
                    Text(entry.body, color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    Text("COMMENTI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(detailComments.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                when {
                    commentsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    detailComments.isEmpty() -> Text("Ancora nessun commento. Questo ricordo aspetta il primo.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    else -> detailComments.take(3).forEach { comment ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(comment.authorName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(comment.body, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
    if (showDeleteConfirmation && canManage) {
        AlertDialog(
                        shape = RoundedCornerShape(30.dp),
            containerColor = haliMeoPopupSurfaceColor(), titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminare questo ricordo?") },
            text = { Text("Il ricordo verrà rimosso dal diario su questo dispositivo e la cancellazione verrà sincronizzata con l'altro telefono appena possibile.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; onDelete() }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun EditScreen(
    existing: DiaryEntry?,
    repo: DiaryRepository,
    onSave: (String, String, String, String, String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    // Categories are intentionally hidden: memories belong to one shared private diary.
    val category = "Ricordo"
    var date by remember(existing?.id) {
        mutableStateOf(existing?.memoryDate?.let { displayHaliMeoDate(it) }
            ?: displayHaliMeoDate(java.time.LocalDate.now().toString()))
    }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri) }
    val context = LocalContext.current
    val editHaze = rememberHazeState()
    val editGlassTint = frostedSurfaceColor()
    val editGlassStyleBackground = MaterialTheme.colorScheme.surface
    val editGlassStyle = remember(editGlassTint) {
        HazeBlurStyle {
            backgroundColor(editGlassStyleBackground.copy(alpha = 0.42f))
            colorEffects(listOf(HazeColorEffect.tint(editGlassTint.copy(alpha = 0.15f))))
            fallbackColorEffect(HazeColorEffect.tint(editGlassTint.copy(alpha = 0.28f)))
        }
    }
    val scrollState = rememberScrollState()
    val collapsed by remember { derivedStateOf { scrollState.value > 56 } }
    val titleScale by animateFloatAsState(
        targetValue = if (collapsed) 0.78f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(100, easing = OneUiMotionEasing),
        label = "memoryEditorTitleScale"
    )

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photoUri = uri.toString()
        }
    }
    var pendingCameraUri by remember(existing?.id) { mutableStateOf<Uri?>(null) }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingCameraUri?.let { photoUri = it.toString() }
        pendingCameraUri = null
    }
    fun launchFullResolutionCamera() {
        runCatching {
            val target = java.io.File(context.cacheDir, "halimeo-camera-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            pendingCameraUri = uri
            cameraCapture.launch(uri)
        }.onFailure {
            Toast.makeText(context, "Fotocamera non disponibile: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
    val photoBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUri, context) {
        value = decodeDiaryPhoto(context, existing?.id.orEmpty(), photoUri, maxDimension = 1400, repo = repo)
    }
    val cardShape = RoundedCornerShape(32.dp)

    Box(Modifier.fillMaxSize()) {
    HaliMeoAmbientBackdrop(Modifier.fillMaxSize())
    Column(
        Modifier.fillMaxSize()
            .hazeSource(editHaze)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (existing == null) "Nuovo ricordo" else "Modifica ricordo",
                    modifier = Modifier.graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = (-0.8).sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Un momento vostro, da custodire insieme.",
                    modifier = Modifier.height(if (collapsed) 0.dp else 22.dp)
                        .graphicsLayer { alpha = if (collapsed) 0f else 1f },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(44.dp)
                    .background(frostedSurfaceColor(), CircleShape)
                    .border(1.dp, frostedEdgeColor(), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Photo-first composition: the memory image is the visual anchor, like a shared social post.
        Card(
            modifier = Modifier.fillMaxWidth()
                .shadow(7.dp, cardShape)
                .clip(cardShape)
                .border(1.dp, frostedEdgeColor(), cardShape),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ANTEPRIMA DEL POST", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.05.sp)
                        Text("Così apparirà nel vostro diario", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text("BOZZA", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(238.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { photoPicker.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "Anteprima della foto del ricordo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(84.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                        )
                        Column(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(15.dp))
                                Text("IL VOSTRO RICORDO", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                                Spacer(Modifier.weight(1f))
                                Text(date, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                title.ifBlank { "Il titolo del vostro momento" },
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (body.isNotBlank()) Text(
                                body,
                                color = Color.White.copy(alpha = 0.84f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(29.dp))
                            }
                            Text("Aggiungete una foto", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Il ricordo prende vita con un'immagine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (photoUri.isNullOrBlank()) "Galleria" else "Cambia foto", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { launchFullResolutionCamera() },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Fotocamera", maxLines = 1)
                    }
                }
                if (!photoUri.isNullOrBlank()) {
                    TextButton(
                        onClick = { photoUri = "" },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Rimuovi foto")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RACCONTATELO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.1.sp)
            Card(
                modifier = Modifier.fillMaxWidth().clip(cardShape)
                    .border(1.dp, frostedEdgeColor(), cardShape),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dategli un titolo") },
                        placeholder = { Text("Es. Quel weekend a Monaco") },
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        colors = haliMeoTextFieldColors()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        label = { Text("Cosa è successo?") },
                        placeholder = { Text("Scrivete qui quello che volete ricordare…") },
                        shape = RoundedCornerShape(22.dp),
                        minLines = 5,
                        colors = haliMeoTextFieldColors()
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("QUANDO È SUCCESSO?", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.1.sp)
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .border(1.dp, frostedEdgeColor(), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it.take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Data (gg/mm/aa)") },
                        placeholder = { Text("30/10/26") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        trailingIcon = {
                            TextButton(onClick = { date = displayHaliMeoDate(java.time.LocalDate.now().toString()) }) {
                                Text("Oggi")
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        colors = haliMeoTextFieldColors()
                    )
                    Text("La data apparirà sul ricordo nel vostro diario.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val parsedDate = parseHaliMeoDate(date)
                    if (title.isNotBlank() && body.isNotBlank() && parsedDate != null) {
                        onSave(title.trim(), body.trim(), category, parsedDate.toString(), photoUri)
                    } else if (parsedDate == null) {
                        Toast.makeText(context, "Inserisci una data valida nel formato gg/mm/aa", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Aggiungi un titolo e racconta il vostro ricordo", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = title.isNotBlank() && body.isNotBlank(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text(if (existing == null) "Salva ricordo" else "Salva modifiche", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("Annulla")
            }
        }
    }
    }
}
@Composable
private fun AnimatedPageTitle(
    title: String,
    style: androidx.compose.ui.text.TextStyle
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered || reduceAnimationsState.value) 1f else 0.94f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "pageTitleEntrance"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered || reduceAnimationsState.value) 1f else 0.55f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(90),
        label = "pageTitleAlpha"
    )
    Text(
        title,
        modifier = Modifier.graphicsLayer {
            scaleX = progress
            scaleY = progress
            this.alpha = alpha
            translationY = (1f - progress) * 12.dp.toPx()
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
        color = MaterialTheme.colorScheme.onSurface,
        style = style,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp
    )
}

@Composable
private fun TripsScreen(onOpenMonaco: () -> Unit) {
    val tripsHaze = rememberHazeState()
    val tripsGlassTint = frostedSurfaceColor()
    val tripsGlassStyleBackground = MaterialTheme.colorScheme.surface
    val tripsGlassStyle = remember(tripsGlassTint) {
        HazeBlurStyle {
            backgroundColor(tripsGlassStyleBackground.copy(alpha = 0.42f))
            colorEffects(listOf(HazeColorEffect.tint(tripsGlassTint.copy(alpha = 0.15f))))
            fallbackColorEffect(HazeColorEffect.tint(tripsGlassTint.copy(alpha = 0.28f)))
        }
    }
    val tripsScrollState = rememberScrollState()
    val tripsHeaderCollapsed by remember { derivedStateOf { tripsScrollState.value > 48 } }
    val tripsTitleScale by animateFloatAsState(
        targetValue = if (tripsHeaderCollapsed) 0.76f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(90, easing = OneUiMotionEasing),
        label = "tripsTitleScale"
    )
    Column(
        Modifier.fillMaxSize().background(Color.Transparent).hazeSource(tripsHaze).verticalScroll(tripsScrollState).padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "I vostri viaggi",
            modifier = Modifier.graphicsLayer {
                scaleX = tripsTitleScale
                scaleY = tripsTitleScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 33.sp, letterSpacing = (-0.9).sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            "Apri un viaggio per entrare nella sua pagina dedicata.",
            modifier = Modifier.height(if (tripsHeaderCollapsed) 0.dp else 24.dp).graphicsLayer { alpha = if (tripsHeaderCollapsed) 0f else 1f },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        val tripInteraction = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .haliMeoPressScale(tripInteraction, reduceMotion = reduceAnimationsState.value, pressedScale = 0.975f, label = "tripCardPress")
                .hazeBlur(HazeInput.Sources(tripsHaze), tripsGlassStyle)
                .clip(RoundedCornerShape(30.dp))
                .clickable(interactionSource = tripInteraction, indication = LocalIndication.current, onClick = onOpenMonaco),
            shape = RoundedCornerShape(30.dp),
            color = frostedSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor()),
            tonalElevation = 0.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Monaco di Baviera", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Il vostro prossimo capitolo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Apri viaggio", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private const val MONACO_TRIP_URL = "https://matteomeo.github.io/compleanno-alex/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MissionScreen() {
    val pageBackground = Color(0xFF0A0E16)
    var pageReady by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(Modifier.fillMaxSize().background(pageBackground)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.rgb(10, 14, 22))
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            pageReady = false
                            pageError = false
                            view.setBackgroundColor(android.graphics.Color.rgb(10, 14, 22))
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            // The page's boot overlay must be opaque: otherwise its animation
                            // can reveal the already-rendered Monaco page underneath it.
                            val backgroundFix = """
                                (() => {
                                  const css = 'html,body{background-color:#0A0E16!important;} #boot,#boot-screen,.boot-screen,.boot-animation,.boot-overlay,.bootloader{background-color:#0A0E16!important;isolation:isolate;}';
                                  let style = document.getElementById('halimeo-native-webview-background');
                                  if (!style) {
                                    style = document.createElement('style');
                                    style.id = 'halimeo-native-webview-background';
                                    (document.head || document.documentElement).appendChild(style);
                                  }
                                  style.textContent = css;
                                  document.documentElement.style.backgroundColor = '#0A0E16';
                                  if (document.body) document.body.style.backgroundColor = '#0A0E16';
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(backgroundFix) {
                                pageReady = true
                            }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                pageError = true
                                pageReady = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            if (uri.scheme == "file" || uri.scheme == "about" || uri.scheme == "data") return false
                            if ((uri.scheme == "https" || uri.scheme == "http") && request.isForMainFrame) {
                                if (uri.host.equals("matteomeo.github.io", ignoreCase = true)) return false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                return true
                            }
                            return false
                        }
                    }
                    loadUrl(MONACO_TRIP_URL)
                }
            },
            update = { view ->
                webViewRef = view
                if (view.url.isNullOrBlank()) view.loadUrl(MONACO_TRIP_URL)
            }
        )

        if (!pageReady || pageError) {
            Box(
                Modifier.fillMaxSize().background(pageBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (pageError) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFB7A0FF), modifier = Modifier.size(34.dp))
                        Text("Impossibile caricare il viaggio", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Controlla il contenuto e riprova.", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { pageReady = false; pageError = false; webViewRef?.reload() }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Riprova")
                        }
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                        Text("Preparazione del viaggio…", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
