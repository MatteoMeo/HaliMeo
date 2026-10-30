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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.compositeOver
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.navigation.compose.*
import it.halimeo.app.data.*
import it.halimeo.app.network.*
import it.halimeo.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

private fun applyLauncherIcon(context: android.content.Context, variant: String) {
    // Alias class names come from the manifest namespace, not applicationId.
    // This matters for debug builds, whose applicationId has the ".debug" suffix.
    val aliases = linkedMapOf(
        "ocean" to "it.halimeo.app.LauncherOcean",
        "lavender" to "it.halimeo.app.LauncherLavender",
        "mint" to "it.halimeo.app.LauncherMint",
        "rose" to "it.halimeo.app.LauncherRose",
        "graphite" to "it.halimeo.app.LauncherGraphite",
        "aurora" to "it.halimeo.app.LauncherAurora"
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
@Composable private fun frostedEdgeColor(): Color = Color.White.copy(alpha = 0.20f)
@Composable private fun frostedGlassBrush(): Brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.065f), Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)))
@Composable private fun isHaliMeoDark(): Boolean = true
@Composable private fun frostedSurfaceColor(): Color = if (isHaliMeoDark()) MaterialTheme.colorScheme.primary.copy(alpha = if (reduceStaticEffectsState.value) 0.08f else 0.14f).compositeOver(Color(0xD9101419)) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f).compositeOver(Color(0xE6FFFFFF))
@Composable private fun frostedSurfaceStrongColor(): Color = if (isHaliMeoDark()) MaterialTheme.colorScheme.primary.copy(alpha = if (reduceStaticEffectsState.value) 0.10f else 0.18f).compositeOver(Color(0xE60C0F12)) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(Color(0xF0FFFFFF))
private val customBackgroundColorState = mutableStateOf("default")
private val customBackgroundUriState = mutableStateOf<String?>(null)
private val backgroundBlurState = mutableStateOf(0f)
private val backgroundOverlayState = mutableStateOf(0.48f)
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
private val reduceAnimationsState = mutableStateOf(false)
private val reduceStaticEffectsState = mutableStateOf(false)
@Composable private fun haliMeoAuthBackdrop(): Brush = if (isHaliMeoDark()) Brush.verticalGradient(listOf(Color(0xFF0A1426), Color(0xFF0B1220), Color(0xFF080D16))) else Brush.verticalGradient(listOf(Color(0xFFF4F8FC), Color(0xFFE7F0F8), Color(0xFFF5F0FA)))
private val TextPrimary = Color(0xFFF5F7FA)



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoTheme(content: @Composable () -> Unit) {
    val darkTheme = true
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
        customBackgroundColorState.value = (prefs.getString("background_color", "default") ?: "default").takeUnless { it == "photo" } ?: "default"
        customBackgroundUriState.value = null
        backgroundBlurState.value = prefs.getFloat("background_blur", 0f)
        backgroundOverlayState.value = prefs.getFloat("background_overlay", 0.48f)
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
    // Android 12+ supplies Material You / Monet colors derived from the device wallpaper.
    // Keep the app dark with a true-black canvas for AMOLED displays.
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color(0xFF080D16),
            surfaceVariant = Color(0xFF121B2A),
            onBackground = Color(0xFFF5F7FA),
            onSurface = Color(0xFFF5F7FA),
            onSurfaceVariant = Color(0xFFD0D4DC),
            outline = Color(0xFF334155)
        )
    } else {
        darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF102A3A), secondary = Color(0xFF61D9E8), onSecondary = Color(0xFF06272D),
            background = Ink, onBackground = TextPrimary, surface = Panel, onSurface = TextPrimary,
            surfaceVariant = Elevated, onSurfaceVariant = Muted, outline = Color(0xFF334155)
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
    private var frameStats: JankStats? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            trimScaledBitmapCache()
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
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
    LaunchedEffect(Unit){signed=!session.token().isNullOrBlank();displayName=session.name()}
    if(signed==null){Box(Modifier.fillMaxSize().background(if (isHaliMeoDark()) Color.Black else Color(0xFFF3F6FA)),contentAlignment=Alignment.Center){LoadingIndicator(modifier=Modifier.size(64.dp), color=MaterialTheme.colorScheme.primary)};return}
    if(signed==false){AuthScreen(
        onLogin={e,p,fail->activity.lifecycleScope.launch{runCatching{ApiClient.api.login(LoginRequest(e,p))}.onSuccess{session.save(it.accessToken,it.userId,it.displayName);displayName=it.displayName;signed=true}.onFailure{fail(it.message?: "Accesso non riuscito")}}},
        onRedeem={code,n,e,p,fail->activity.lifecycleScope.launch{runCatching{ApiClient.api.redeem(RedeemInviteRequest(code,n,e,p))}.onSuccess{session.save(it.accessToken,it.userId,it.displayName);displayName=it.displayName;signed=true}.onFailure{fail(it.message?: "Invito non valido")}}}
    );return}
    val entries by repo.entries.collectAsState(initial= emptyList()); var editing by remember{mutableStateOf<DiaryEntry?>(null)}
    var syncInProgress by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    val pendingSyncCount = remember(entries) { entries.count { it.syncState != "SYNCED" } }
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
                .onFailure {
                    syncFailed = true
                    Toast.makeText(ctx, "Offline: i ricordi restano salvati sul dispositivo", Toast.LENGTH_LONG).show()
                }
            syncInProgress = false
        }
    }
    // Favorite state lives in Room, so the diary Flow is the single source of truth.
    val legacyFavoritesPrefs = remember { ctx.getSharedPreferences("halimeo_favorites", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        if (!legacyFavoritesPrefs.getBoolean("room_migrated", false)) {
            // Wait for the first real database emission before migrating the old set.
            repo.entries.first()
            val legacyIds = legacyFavoritesPrefs.getStringSet("ids", emptySet())?.toSet().orEmpty()
            legacyIds.forEach { repo.setFavorite(it, true) }
            legacyFavoritesPrefs.edit().putBoolean("room_migrated", true).remove("ids").apply()
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
            backgroundColor(navigationGlassBackground)
            colorEffects(listOf(HazeColorEffect.tint(navigationGlassTint.copy(alpha = 0.55f))))
            fallbackColorEffect(HazeColorEffect.tint(navigationGlassTint))
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
                // A firmer spring shortens the perceived response to navbar taps while
                // keeping the indicator and page content on the exact same pager timeline.
                pagerState.animateScrollToPage(
                    page = index,
                    animationSpec = spring(dampingRatio = 0.96f, stiffness = 1800f)
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().background(if (isHaliMeoDark()) Color.Black else Color(0xFFF3F6FA))) {
    HaliMeoAmbientBackdrop(Modifier.fillMaxSize())
    Scaffold(containerColor=Color.Transparent,topBar={TopAppBar(
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
            IconButton(
                onClick = { activity.lifecycleScope.launch { session.clear(); signed = false } },
                modifier = Modifier.padding(horizontal = 8.dp).size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Esci", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
        }
    )},bottomBar={}, floatingActionButton = {}, content = { padding ->
        NavHost(
            navController = nav,
            startDestination = "main",
            modifier = Modifier.padding(padding),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("main") {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().hazeSource(navigationHaze),
                    beyondViewportPageCount = 0,
                    key = { it }
                ) { page ->
                    when (page) {
                        0 -> DiaryScreen(entries,
                            { editing = it; nav.navigate("edit") },
                            { entry -> activity.lifecycleScope.launch { repo.setFavorite(entry.id, !entry.isFavorite) } },
                            { entry -> activity.lifecycleScope.launch { repo.delete(entry.id) } },
                            { entry -> nav.navigate("memory/${entry.id}") }
                        )
                        1 -> TripsScreen(onOpenMonaco = { nav.navigate("tripMonaco") })
                        else -> SettingsScreen(displayName = displayName, session = session, repo = repo, activity = activity, ctx = ctx)
                    }
                }
            }
            composable(
                route = "edit",
                enterTransition = {
                    if (reduceAnimationsState.value) fadeIn(tween(90))
                    else fadeIn(tween(90, easing = OneUiMotionEasing)) +
                        scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(0.92f, 0.90f), animationSpec = tween(220, easing = OneUiMotionEasing))
                },
                exitTransition = {
                    if (reduceAnimationsState.value) fadeOut(tween(80))
                    else fadeOut(tween(130, easing = OneUiMotionEasing)) +
                        scaleOut(targetScale = 1.03f, transformOrigin = TransformOrigin(0.92f, 0.90f), animationSpec = tween(130, easing = OneUiMotionEasing))
                },
                popEnterTransition = {
                    if (reduceAnimationsState.value) fadeIn(tween(80))
                    else fadeIn(tween(130, easing = OneUiMotionEasing)) +
                        scaleIn(initialScale = 1.03f, transformOrigin = TransformOrigin(0.92f, 0.90f), animationSpec = tween(130, easing = OneUiMotionEasing))
                },
                popExitTransition = {
                    if (reduceAnimationsState.value) fadeOut(tween(80))
                    else fadeOut(tween(90, easing = OneUiMotionEasing)) +
                        scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0.92f, 0.90f), animationSpec = tween(220, easing = OneUiMotionEasing))
                }
            ) { EditScreen(editing, { t, b, c, d, photo ->
                activity.lifecycleScope.launch {
                    repo.save(t, b, c, d, editing?.id, photo)
                    editing = null
                    nav.popBackStack()
                    runCatching { repo.syncNow() }
                }
            }, { editing = null; nav.popBackStack() }) }
            composable(
                route = "memory/{entryId}",
                enterTransition = { if (reduceAnimationsState.value) fadeIn(tween(90)) else fadeIn(tween(130, easing = OneUiMotionEasing)) + slideInHorizontally(initialOffsetX = { it / 12 }, animationSpec = tween(220, easing = OneUiMotionEasing)) },
                exitTransition = { if (reduceAnimationsState.value) fadeOut(tween(80)) else fadeOut(tween(110, easing = OneUiMotionEasing)) + slideOutHorizontally(targetOffsetX = { -it / 18 }, animationSpec = tween(140, easing = OneUiMotionEasing)) },
                popEnterTransition = { if (reduceAnimationsState.value) fadeIn(tween(80)) else fadeIn(tween(110, easing = OneUiMotionEasing)) + slideInHorizontally(initialOffsetX = { -it / 18 }, animationSpec = tween(150, easing = OneUiMotionEasing)) },
                popExitTransition = { if (reduceAnimationsState.value) fadeOut(tween(80)) else fadeOut(tween(130, easing = OneUiMotionEasing)) + slideOutHorizontally(targetOffsetX = { it / 12 }, animationSpec = tween(210, easing = OneUiMotionEasing)) }
            ) { backStack ->
                val entryId = backStack.arguments?.getString("entryId")
                val memory = entries.firstOrNull { it.id == entryId }
                if (memory != null) MemoryDetailScreen(memory, onBack = { nav.popBackStack() }, onEdit = {
                    editing = memory
                    nav.navigate("edit")
                })
                else LaunchedEffect(entryId) { nav.popBackStack() }
            }
            composable("tripMonaco") { MissionScreen() }
        }
    })
    // Read the continuously changing pager offset in the graphics layer, not the root composition.
    if (currentRoute != "edit" && currentRoute != "tripMonaco") Surface(
        modifier = Modifier.align(Alignment.BottomStart)
            .padding(start = 12.dp, end = 78.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 4.dp)
            .height(68.dp)
            .then(
                if (reduceStaticEffectsState.value) Modifier.background(navigationGlassTint, RoundedCornerShape(50))
                else Modifier.clip(RoundedCornerShape(50)).hazeBlur(HazeInput.Sources(navigationHaze), navigationGlassStyle)
            ),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor()),
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)
                .pointerInput(pagerState, navigationScope) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            navigationScope.launch {
                                val destination = pagerState.targetPage.coerceIn(0, 2)
                                if (reduceAnimationsState.value) pagerState.scrollToPage(destination)
                                else pagerState.animateScrollToPage(destination, animationSpec = spring(dampingRatio = 0.94f, stiffness = 1500f))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val pageWidth = pagerState.layoutInfo.pageSize.coerceAtLeast(size.width)
                            pagerState.dispatchRawDelta(-dragAmount * pageWidth.toFloat() / size.width.coerceAtLeast(1).toFloat())
                        }
                    )
                }
        ) {
            val tabWidth = (maxWidth - 6.dp) / 3
            // The indicator is purely visual; only each tab handles taps, so it cannot steal or
            // reinterpret a click while moving between destinations.
            Box(
                Modifier.graphicsLayer {
                    val pagePosition = if (reduceAnimationsState.value) {
                        pagerState.currentPage.toFloat()
                    } else {
                        (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
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
                FloatingNavItem(
                    Modifier.weight(1f), primaryTab == 0, "Diario", Icons.Default.Book
                ) {
                    navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    selectPrimaryTab(0)
                }
                FloatingNavItem(
                    Modifier.weight(1f), primaryTab == 1, "Viaggi", Icons.Default.FlightTakeoff
                ) {
                    navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    selectPrimaryTab(1)
                }
                FloatingNavItem(
                    Modifier.weight(1f), primaryTab == 2, "Impostazioni", Icons.Default.Settings
                ) {
                    navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    selectPrimaryTab(2)
                }
            }
        }
    }

    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.92f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.72f, stiffness = 850f),
        label = "newMemoryFabScale"
    )
    Surface(
        modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = 11.dp)
            .size(58.dp)
            .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
            .clip(RoundedCornerShape(24.dp))
            .then(if (reduceStaticEffectsState.value) Modifier else Modifier.hazeBlur(HazeInput.Sources(navigationHaze), navigationGlassStyle)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (reduceStaticEffectsState.value) 0.96f else 0.62f),
        border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor()),
        shadowElevation = 7.dp
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable(interactionSource = fabInteraction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Nuovo ricordo") { navigationFeedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); editing = null; nav.navigate("edit") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Nuovo ricordo", modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onPrimary)
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
    // Keep the backdrop static. A frame-by-frame animated layer underneath a live blur
    // continuously invalidates the captured surface and can look like navbar flicker.
    val driftX = 0f
    val driftY = 0f
    val accent = palette.primary
    val customBase = Color(
        red = (accent.red * 0.12f).coerceAtMost(0.12f),
        green = (accent.green * 0.12f).coerceAtMost(0.12f),
        blue = (accent.blue * 0.12f).coerceAtMost(0.12f),
        alpha = 1f
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to atmosphere.top,
                        0.52f to atmosphere.top.copy(red = (atmosphere.top.red * 0.72f + atmosphere.primary.red * 0.28f).coerceIn(0f, 1f), green = (atmosphere.top.green * 0.72f + atmosphere.primary.green * 0.28f).coerceIn(0f, 1f), blue = (atmosphere.top.blue * 0.72f + atmosphere.primary.blue * 0.28f).coerceIn(0f, 1f)),
                        1.0f to atmosphere.bottom
                    )
                )
            )
        } else if (backgroundChoice == "default") {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to customBase.copy(red = (customBase.red + 0.035f).coerceAtMost(1f), green = (customBase.green + 0.035f).coerceAtMost(1f), blue = (customBase.blue + 0.035f).coerceAtMost(1f)),
                        0.48f to customBase.copy(red = (customBase.red + 0.012f).coerceAtMost(1f), green = (customBase.green + 0.012f).coerceAtMost(1f), blue = (customBase.blue + 0.012f).coerceAtMost(1f)),
                        1.0f to Color(0xFF05070C)
                    )
                )
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = driftX.dp, y = (-140).dp + driftY.dp).size(460.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.primary.copy(alpha = 0.28f),
                        0.42f to palette.secondary.copy(alpha = 0.11f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-210).dp - driftX.dp * 0.55f, y = 40.dp + driftY.dp).size(380.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.tertiary.copy(alpha = 0.14f),
                        0.48f to palette.tertiary.copy(alpha = 0.055f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 190.dp - driftX.dp * 0.45f, y = 150.dp - driftY.dp).size(400.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.secondary.copy(alpha = 0.14f),
                        0.45f to palette.primary.copy(alpha = 0.05f),
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
        targetValue = if (selected) 1.04f else 0.96f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.78f, stiffness = 900f),
        label = "navItemScale"
    )
    Box(
        modifier = modifier.fillMaxHeight()
            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .clip(RoundedCornerShape(50))
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp), tint = animatedIcon)
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
) {
    val diaryEntries by repo.entries.collectAsState(initial = emptyList())
    val settingsFeedbackView = LocalView.current
    val pendingSyncCount = remember(diaryEntries) { diaryEntries.count { it.syncState != "SYNCED" } }
    val lastSyncAt by lastSyncAtState
    var isOwner by remember { mutableStateOf<Boolean?>(null) }
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

    LaunchedEffect(Unit) {
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
    }

    val primaryText = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val accentText = MaterialTheme.colorScheme.primary
    var expandedSettingsSections by remember { mutableStateOf(setOf("profile", "appearance")) }
    val profilePrefs = remember { ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE) }
    var profilePhotoUri by remember { mutableStateOf(profilePrefs.getString("profile_photo_uri", null)) }
    var profileNickname by remember { mutableStateOf(profilePrefs.getString("profile_nickname", displayName) ?: displayName) }
    var profileAbout by remember { mutableStateOf(profilePrefs.getString("profile_about", "") ?: "") }
    var isProfileEditing by remember {
        mutableStateOf(
            !profilePrefs.getBoolean(
                "profile_saved",
                profilePrefs.contains("profile_nickname") || profilePrefs.contains("profile_about") || profilePrefs.contains("profile_photo_uri")
            )
        )
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
    var selectedAppIcon by remember { mutableStateOf(profilePrefs.getString("app_icon_variant", "ocean") ?: "ocean") }
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
            backgroundColor(settingsGlassStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(settingsGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(settingsGlassTint))
        }
    }
    val settingsScrollState = rememberScrollState()
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
        Card(
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)).hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp))
                .clickable { expandedSettingsSections = if ("profile" in expandedSettingsSections) expandedSettingsSections - "profile" else expandedSettingsSections + "profile" },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PROFILO E ACCOUNT", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("profile" !in expandedSettingsSections) Text("Il vostro account e la foto profilo", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("profile" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("profile" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(
                    visible = "profile" in expandedSettingsSections,
                    enter = fadeIn(tween(150, easing = OneUiMotionEasing)) + expandVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)),
                    exit = fadeOut(tween(100)) + shrinkVertically(animationSpec = tween(150, easing = OneUiMotionEasing))
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
                    OutlinedTextField(value = profileNickname, onValueChange = { profileNickname = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome o soprannome") }, singleLine = true)
                    OutlinedTextField(value = profileAbout, onValueChange = { profileAbout = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Una piccola descrizione") }, placeholder = { Text("Es. collezionisti di ricordi dal 2024") }, minLines = 2, maxLines = 3)
                    Button(onClick = {
                        profilePrefs.edit()
                            .putString("profile_nickname", profileNickname.trim())
                            .putString("profile_about", profileAbout.trim())
                            .putBoolean("profile_saved", true)
                            .apply()
                        isProfileEditing = false
                        Toast.makeText(ctx, "Profilo salvato su questo dispositivo", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Salva profilo")
                    }
                    Text("Foto e dettagli sono salvati su questo dispositivo; non modificano ancora il profilo online.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
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
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp))
                .clickable { expandedSettingsSections = if ("appearance" in expandedSettingsSections) expandedSettingsSections - "appearance" else expandedSettingsSections + "appearance" },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PERSONALIZZAZIONE", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("appearance" !in expandedSettingsSections) Text("Tema, colori, sfondo e prestazioni", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("appearance" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("appearance" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "appearance" in expandedSettingsSections, enter = fadeIn(tween(150, easing = OneUiMotionEasing)) + expandVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)), exit = fadeOut(tween(100)) + shrinkVertically(animationSpec = tween(150, easing = OneUiMotionEasing))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tema scuro AMOLED sempre attivo, con accento dinamico.", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                Text("Anteprima in tempo reale", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle).clip(RoundedCornerShape(22.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp), color = frostedSurfaceStrongColor()
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("Un ricordo importante", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("29/10/26 · Ricordo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Questa è la leggibilità prevista per testi, icone e schede con le impostazioni correnti.", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        Text("Accento e gradienti seguono lo sfondo selezionato.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Atmosfere One UI 9", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Scegli una profondità di colore completa: sfondo sfumato, luce ambientale e accenti coordinati.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                val atmosphereOptions = listOf(
                    "aurora" to "Aurora",
                    "deep_ocean" to "Oceano",
                    "sunset" to "Tramonto",
                    "forest" to "Foresta",
                    "amethyst" to "Ametista",
                    "graphite" to "Grafite"
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    atmosphereOptions.forEach { option ->
                        val colors = haliMeoAtmosphere(option.first)!!
                        Column(
                            modifier = Modifier.width(82.dp).clip(RoundedCornerShape(18.dp))
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
                                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(15.dp))
                                    .background(Brush.linearGradient(listOf(colors.top, colors.primary, colors.secondary, colors.bottom)))
                                    .border(
                                        if (backgroundColor == option.first) 2.dp else 1.dp,
                                        if (backgroundColor == option.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        RoundedCornerShape(15.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).background(colors.secondary, CircleShape))
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
                Text("Icona dell’app", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Scegli l’aspetto di HaliMeo nella schermata Home.", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val iconChoices = listOf(
                    Triple("ocean", "Oceano", R.mipmap.ic_launcher),
                    Triple("rose", "Tramonto", R.mipmap.ic_launcher_rose),
                    Triple("mint", "Foresta", R.mipmap.ic_launcher_mint),
                    Triple("lavender", "Ametista", R.mipmap.ic_launcher_lavender),
                    Triple("graphite", "Grafite", R.mipmap.ic_launcher_graphite),
                    Triple("aurora", "Aurora", R.mipmap.ic_launcher_aurora)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    iconChoices.forEach { (key, label, iconRes) ->
                        Column(
                            modifier = Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                runCatching { applyLauncherIcon(ctx, key) }
                                    .onSuccess {
                                        selectedAppIcon = key
                                        profilePrefs.edit().putString("app_icon_variant", key).apply()
                                        Toast.makeText(ctx, "Icona $label selezionata", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        android.util.Log.e("HaliMeoLauncher", "Cambio icona non riuscito per la variante $key", error)
                                        Toast.makeText(ctx, "Non è stato possibile cambiare icona. Riprova.", Toast.LENGTH_LONG).show()
                                    }
                            }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                Modifier.size(62.dp).clip(RoundedCornerShape(18.dp))
                                    .border(
                                        width = if (selectedAppIcon == key) 2.dp else 1.dp,
                                        color = if (selectedAppIcon == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(18.dp)
                                    ).padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconPreview = remember(iconRes, ctx) {
                                    val drawable = requireNotNull(ctx.getDrawable(iconRes)) {
                                        "Risorsa anteprima icona non disponibile"
                                    }
                                    android.graphics.Bitmap.createBitmap(
                                        128, 128, android.graphics.Bitmap.Config.ARGB_8888
                                    ).also { bitmap ->
                                        val canvas = android.graphics.Canvas(bitmap)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                    }.asImageBitmap()
                                }
                                Image(
                                    bitmap = iconPreview,
                                    contentDescription = "Icona HaliMeo $label",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                )
                                if (selectedAppIcon == key) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selezionata", tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).size(19.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                            Text(label, color = if (selectedAppIcon == key) primaryText else secondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(0, 48, 60, 90, 120).forEachIndexed { index, hz ->
                        SegmentedButton(
                            selected = selectedRefreshRate == hz,
                            onClick = {
                                selectedRefreshRate = hz
                                prefs.edit().putInt("preferred_refresh_rate", hz).apply()
                                activity.window.attributes = activity.window.attributes.apply { preferredRefreshRate = hz.toFloat() }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 4)
                        ) { Text(if (hz == 0) "Auto" else "$hz", maxLines = 1) }
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
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color(0xFFE2E8F0),
                        uncheckedTrackColor = Color(0xFF374151),
                        uncheckedBorderColor = Color(0xFF94A3B8)
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
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color(0xFFE2E8F0),
                        uncheckedTrackColor = Color(0xFF374151),
                        uncheckedBorderColor = Color(0xFF94A3B8)
                    ))
                }
                Text("Riduce gli effetti di sfocatura per privilegiare leggibilità e prestazioni.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }



        Card(
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle).clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { expandedSettingsSections = if ("date" in expandedSettingsSections) expandedSettingsSections - "date" else expandedSettingsSections + "date" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DATA SPECIALE", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("date" !in expandedSettingsSections) Text("Date importanti e promemoria", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("date" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("date" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "date" in expandedSettingsSections, enter = fadeIn(tween(75)) + expandVertically(tween(110)), exit = fadeOut(tween(45)) + shrinkVertically(tween(85))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tenete a portata di mano una data importante", color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = specialDateLabel, onValueChange = { specialDateLabel = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nome (es. compleanno)") })
                OutlinedTextField(value = specialDate, onValueChange = { specialDate = it.take(10) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp), label = { Text("Data (gg/mm/aa)") }, placeholder = { Text("26/12/26") })
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
                    specialDates.forEach { item -> Surface(modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle).clip(RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = frostedSurfaceStrongColor(), border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor())) {
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

        Card(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { expandedSettingsSections = if ("sync" in expandedSettingsSections) expandedSettingsSections - "sync" else expandedSettingsSections + "sync" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = accentText, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SINCRONIZZAZIONE", color = accentText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if ("sync" !in expandedSettingsSections) Text("Backup e stato dei tuoi ricordi", color = secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if ("sync" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("sync" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "sync" in expandedSettingsSections, enter = fadeIn(tween(75)) + expandVertically(tween(110)), exit = fadeOut(tween(45)) + shrinkVertically(tween(85))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.CloudUpload, contentDescription = null, tint = accentText, modifier = Modifier.size(26.dp))
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

        Card(
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = accentText, modifier = Modifier.padding(9.dp).size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INFORMAZIONI APP", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Dettagli di HaliMeo e della build installata", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("v${BuildConfig.VERSION_NAME}", color = accentText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle)
                .clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { if (developerUnlocked) developerUnlocked = false else { showDeveloperPinDialog = true; developerPin = ""; developerPinError = false } }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        Icon(if (developerUnlocked) Icons.Default.Code else Icons.Default.Lock, contentDescription = null, tint = accentText, modifier = Modifier.padding(9.dp).size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("OPZIONI SVILUPPATORE", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (developerUnlocked) "Pannello sbloccato · tocca per bloccare" else "Strumenti avanzati e configurazione server", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if (developerUnlocked) Icons.Default.ExpandLess else Icons.Default.Lock, contentDescription = null, tint = accentText)
                }
                AnimatedVisibility(visible = developerUnlocked, enter = fadeIn(tween(130)) + expandVertically(tween(180)), exit = fadeOut(tween(90)) + shrinkVertically(tween(130))) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SERVER API", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = serverDraft,
                            onValueChange = { serverDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL base del server") },
                            placeholder = { Text("https://server.example/") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Text("Il cambio server non esegue il logout e non cancella i dati locali. Il server di destinazione deve però riconoscere il token di sessione attuale.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !serverBusy && serverDraft.isNotBlank(),
                                onClick = {
                                    runCatching { ApiClient.setBaseUrl(serverDraft) }
                                        .onSuccess {
                                            serverDraft = ApiClient.baseUrl()
                                            serverBusy = true
                                            developerStatus = "Indirizzo salvato. Verifico la sessione…"
                                            activity.lifecycleScope.launch {
                                                val token = session.token()
                                                if (token.isNullOrBlank()) {
                                                    developerStatus = "Server salvato. Nessuna sessione attiva da verificare."
                                                } else {
                                                    runCatching { ApiClient.api.me("Bearer $token") }
                                                        .onSuccess { developerStatus = "Connessione e sessione verificate." }
                                                        .onFailure { developerStatus = "URL salvato, verifica non riuscita: ${it.message ?: "errore di rete"}" }
                                                }
                                                serverBusy = false
                                            }
                                        }
                                        .onFailure { developerStatus = it.message ?: "URL non valido." }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (serverBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Applica")
                            }
                            OutlinedButton(
                                onClick = {
                                    ApiClient.setBaseUrl(BuildConfig.API_BASE_URL)
                                    serverDraft = ApiClient.baseUrl()
                                    developerStatus = "Ripristinato il server predefinito."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Predefinito") }
                        }
                        Text("Attivo: ${ApiClient.baseUrl()}", color = secondaryText, style = MaterialTheme.typography.labelSmall)
                        if (developerStatus.isNotBlank()) Text(developerStatus, color = accentText, style = MaterialTheme.typography.bodySmall)
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
                            shape = RoundedCornerShape(16.dp)
                        )
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

        Card(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { expandedSettingsSections = if ("invites" in expandedSettingsSections) expandedSettingsSections - "invites" else expandedSettingsSections + "invites" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = accentText, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INVITI", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if ("invites" !in expandedSettingsSections) Text("Crea e condividi un invito privato", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(if ("invites" in expandedSettingsSections) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if ("invites" in expandedSettingsSections) "Comprimi" else "Espandi", tint = accentText)
                }
                AnimatedVisibility(visible = "invites" in expandedSettingsSections, enter = fadeIn(tween(75)) + expandVertically(tween(110)), exit = fadeOut(tween(45)) + shrinkVertically(tween(85))) {
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

                if (inviteMessage.isNotBlank() && isOwner != false) {
                    Text(inviteMessage, color = if (inviteCode.isNotBlank()) accentText else secondaryText, style = MaterialTheme.typography.bodySmall)
                }
                Text("Versione ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        }
    }
}

}
}
@Composable private fun AuthScreen(onLogin:(String,String,(String)->Unit)->Unit,onRedeem:(String,String,String,String,(String)->Unit)->Unit){
        val authHaze = rememberHazeState()
    val authGlassTint = frostedSurfaceStrongColor()
    val authGlassStyleBackground = MaterialTheme.colorScheme.surface
    val authGlassStyle = remember(authGlassTint) {
        HazeBlurStyle {
            backgroundColor(authGlassStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(authGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(authGlassTint))
        }
    }
    var redeem by remember{mutableStateOf(false)};var code by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var serverUrl by remember { mutableStateOf(ApiClient.baseUrl().removeSuffix("/")) }
    // Separato dal testo digitato: controlliamo l'URL salvato, non quello ancora in modifica.
    var serverCheckUrl by remember { mutableStateOf(ApiClient.baseUrl().removeSuffix("/")) }
    var serverOnline by remember { mutableStateOf<Boolean?>(null) }
    var serverMessage by remember { mutableStateOf("Verifica connessione…") }
    var urlError by remember { mutableStateOf("") }
    LaunchedEffect(serverCheckUrl) {
        while (true) {
            serverOnline = null
            serverMessage = "Verifica connessione…"
            try {
                ApiClient.api.health()
                serverOnline = true
                serverMessage = "Server online"
            } catch (e: Exception) {
                serverOnline = false
                serverMessage = "Server offline o non raggiungibile"
            }
            delay(15000)
        }
    }
    Column(Modifier.fillMaxSize().background(haliMeoAuthBackdrop()).hazeSource(authHaze).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.Center){Text("HaliMeo",style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black);Text("Un posto solo vostro.",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(16.dp))
        Card(colors=CardDefaults.cardColors(containerColor=frostedSurfaceStrongColor()), modifier=Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(authHaze), authGlassStyle).clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)), shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val dotColor = when (serverOnline) { true -> Color(0xFF86E3B0); false -> Color(0xFFFF8E9B); null -> Color(0xFFFFD18A) }
                    Box(Modifier.size(10.dp).background(dotColor, RoundedCornerShape(50)))
                    Text(serverMessage, color=dotColor, style=MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(serverUrl, { serverUrl=it; urlError="" }, label={Text("Indirizzo del server")}, placeholder={Text("http://192.168.1.50:8000")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                if (urlError.isNotBlank()) Text(urlError, color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={
                        try {
                            if (!serverUrl.trim().startsWith("http://") && !serverUrl.trim().startsWith("https://")) throw IllegalArgumentException("Inserisci un URL che inizi con http:// o https://")
                            ApiClient.setBaseUrl(serverUrl.trim())
                            serverUrl = ApiClient.baseUrl().removeSuffix("/")
                            serverCheckUrl = serverUrl
                            serverOnline = null
                            serverMessage = "Verifica connessione…"
                            urlError = ""
                        } catch (e: Exception) { urlError=e.message ?: "Indirizzo non valido" }
                    }, modifier=Modifier.weight(1f)) { Text("Salva server") }
                    OutlinedButton(onClick={
                        serverUrl = ApiClient.baseUrl().removeSuffix("/")
                        serverCheckUrl = serverUrl
                        serverOnline = null
                        serverMessage = "Verifica connessione…"
                    }, modifier=Modifier.weight(1f)) { Text("Ricarica") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if(redeem){OutlinedTextField(code,{code=it},label={Text("Codice invito")},modifier=Modifier.fillMaxWidth());OutlinedTextField(name,{name=it},label={Text("Nome")},modifier=Modifier.fillMaxWidth())}
        OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth());OutlinedTextField(pass,{pass=it},label={Text("Password")},modifier=Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(12.dp))
        Button(enabled=!busy,onClick={busy=true;val fail:(String)->Unit={error=it;busy=false};if(redeem)onRedeem(code,name,email,pass,fail)else onLogin(email,pass,fail)},modifier=Modifier.fillMaxWidth()){Text(if(redeem)"ACCETTA INVITO" else "ACCEDI")}
        TextButton(onClick={redeem=!redeem;error=""}){Text(if(redeem)"Ho già un account" else "Ho un codice invito")}
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
    onEdit: (DiaryEntry) -> Unit,
    onToggleFavorite: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit,
    onOpenMemory: (DiaryEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    val calendarState = rememberDatePickerState()
    var pendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val diaryHaze = rememberHazeState()
    val diaryGlassTint = frostedSurfaceColor()
    val diaryGlassStyleBackground = MaterialTheme.colorScheme.surface
    val diaryGlassStyle = remember(diaryGlassTint) {
        HazeBlurStyle {
            backgroundColor(diaryGlassStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(diaryGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(diaryGlassTint))
        }
    }
    val diaryContext = LocalContext.current
    val highlightsTitle = "Highlights"
    val feedbackView = LocalView.current
    val listState = rememberLazyListState()
    // Cache diary header data instead of sorting favourites and parsing special dates
    // again whenever Compose recomposes the LazyColumn.
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
    val filtered = remember(entries, query, selectedDate, favoritesOnly) {
        entries.asSequence().filter {
            (it.title.contains(query, true) || it.body.contains(query, true)) &&
                (selectedDate == null || it.memoryDate == selectedDate) &&
                (!favoritesOnly || it.isFavorite)
        }.sortedByDescending { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }.toList()
    }

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = { TextButton(onClick = { selectedDate = calendarState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }; showCalendar = false }) { Text("Filtra") } },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Annulla") } }
        ) { DatePicker(state = calendarState) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .hazeSource(diaryHaze)
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
                IconButton(onClick = { calendarState.selectedDateMillis = selectedDate?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }; showCalendar = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Filtra per data", modifier = Modifier.size(25.dp), tint = if (selectedDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) query = ""
                    }
                ) {
                    Icon(
                        if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchExpanded) "Chiudi ricerca" else "Cerca ricordi",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle).clip(RoundedCornerShape(20.dp)),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("Cerca un ricordo…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella ricerca")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = frostedSurfaceStrongColor(),
                    unfocusedContainerColor = frostedSurfaceColor(),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = selectedDate != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { selectedDate = null }, label = { Text("Data: ${selectedDate?.let(::displayHaliMeoDate)} · Rimuovi filtro") }, leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !favoritesOnly, onClick = { favoritesOnly = false },
                    shape = RoundedCornerShape(50),
                    label = { Text("Tutti", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = frostedSurfaceColor(), labelColor = MaterialTheme.colorScheme.onSurfaceVariant, iconColor = MaterialTheme.colorScheme.onSurfaceVariant, selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), selectedLabelColor = MaterialTheme.colorScheme.onSurface, selectedLeadingIconColor = MaterialTheme.colorScheme.primary)
                )
                FilterChip(
                    selected = favoritesOnly, onClick = { favoritesOnly = true },
                    shape = RoundedCornerShape(50),
                    label = { Text("Preferiti", maxLines = 1) },
                    leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface, selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        containerColor = frostedSurfaceColor(), labelColor = MaterialTheme.colorScheme.onSurfaceVariant, iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        when {
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
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
                    if (!favoritesOnly && query.isBlank()) {
                        if (photoHighlights.isNotEmpty()) {
                            item(key = "memory_highlights") {
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text("Highlights", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        photoHighlights.forEach { highlight ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                                                val storyBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, highlight.photoUri) {
                                                    value = highlight.photoUri?.let { decodeMemoryPhoto(diaryContext, it, maxDimension = 720) }
                                                }
                                                Surface(
                                                    modifier = Modifier.size(62.dp).clip(CircleShape).clickable { onOpenMemory(highlight) },
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (storyBitmap != null) Image(
                                                            bitmap = storyBitmap!!.asImageBitmap(),
                                                            contentDescription = "Apri ${highlight.title}",
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        ) else Icon(Icons.Default.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                                    }
                                                }
                                                Text(highlight.title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
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
                        val photoContext = LocalContext.current
                        val entryPhoto by produceState<android.graphics.Bitmap?>(initialValue = null, entry.photoUri, photoContext, expanded) {
                            // Decode only after opening a card; eagerly decoding every visible photo
                            // causes avoidable IO, allocations and frame drops while scrolling.
                            value = if (expanded) entry.photoUri?.let { decodeMemoryPhoto(photoContext, it, maxDimension = 1600) } else null
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                // Keep list movement attached to each stable diary entry while filtering or deleting.
                                .animateItem(
                                    fadeInSpec = if (reduceAnimationsState.value) tween(0) else tween(150, easing = OneUiMotionEasing),
                                    fadeOutSpec = if (reduceAnimationsState.value) tween(0) else tween(100, easing = OneUiMotionEasing),
                                    placementSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.86f, stiffness = 420f)
                                )
                                // Clip the whole card layer before drawing the outline. This prevents
                                // the rectangular layer from leaking past rounded glass corners.
                                
                                .shadow(9.dp, MaterialTheme.shapes.large, ambientColor = accent.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.22f))
                                // Repeated scrolling cards use translucent surfaces rather than
                                // per-item live blur; reserve Haze for the navigation and hero layers.
                                .animateContentSize(if (reduceAnimationsState.value) tween(0) else tween(210, easing = OneUiMotionEasing))
                                .clip(MaterialTheme.shapes.large)
                                .border(1.dp, frostedEdgeColor().copy(alpha = 0.9f), MaterialTheme.shapes.large)
                                .clickable(
                                    onClick = {
                                        feedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        expandedEntryIds = if (entry.id in expandedEntryIds) expandedEntryIds - entry.id else expandedEntryIds + entry.id
                                    }
                                ),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor()),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box {
                                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.11f), Color.Transparent, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.045f)))));
                                Column(
                                modifier = Modifier.padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(top = 2.dp).size(42.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = accent.copy(alpha = 0.13f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.35).sp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                            Text(displayHaliMeoDate(entry.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.13f)) {
                                                
                                            }
                                        }
                                    }
                                    val isFavorite = entry.isFavorite
                                    IconButton(onClick = { feedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onToggleFavorite(entry) }, modifier = Modifier.semantics { contentDescription = if (entry.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti" }) {
                                        Icon(
                                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                            tint = if (isFavorite) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (entry.syncState != "SYNCED") {
                                        Surface(
                                            color = Color(0xFFFFD479).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFFD479), modifier = Modifier.size(13.dp))
                                                Text("In attesa", color = Color(0xFFFFD479), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = entryPhoto != null,
                                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(240)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(animationSpec = tween(180))
                                ) {
                                    if (entryPhoto != null) {
                                        Image(
                                            bitmap = entryPhoto!!.asImageBitmap(),
                                            contentDescription = "Foto del ricordo: " + entry.title,
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(18.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                                Text(
                                    entry.body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                AnimatedVisibility(
                                    visible = expanded,
                                    enter = fadeIn(tween(160)) + expandVertically(animationSpec = tween(220)),
                                    exit = fadeOut(tween(100)) + shrinkVertically(animationSpec = tween(160))
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = { onEdit(entry) }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Modifica")
                                            }
                                            TextButton(onClick = { pendingDelete = entry }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Elimina", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                if (!expanded && (entry.body.length > 140 || entry.photoUri != null)) {
                                    Text(
                                        if (entry.photoUri != null) "Apri ricordo e foto" else "Leggi tutto",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminare questo ricordo?") },
            text = { Text("Il ricordo verrà rimosso dalla tua lista e la modifica sarà sincronizzata quando possibile.") },
            confirmButton = { TextButton(onClick = { pendingDelete?.let(onDelete); pendingDelete = null }) { Text("Elimina", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun MemoryDetailScreen(entry: DiaryEntry, onBack: () -> Unit, onEdit: () -> Unit) {
    val detailHaze = rememberHazeState()
    val detailTint = frostedSurfaceColor()
    val detailStyleBackground = MaterialTheme.colorScheme.surface
    val detailStyle = remember(detailTint) {
        HazeBlurStyle {
            backgroundColor(detailStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(detailTint.copy(alpha = 0.50f))))
            fallbackColorEffect(HazeColorEffect.tint(detailTint))
        }
    }
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, entry.photoUri) {
        value = entry.photoUri?.let { decodeMemoryPhoto(context, it, maxDimension = 2200) }
    }
    Column(
        Modifier.fillMaxSize().hazeSource(detailHaze).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            AnimatedPageTitle("Il vostro ricordo", MaterialTheme.typography.headlineSmall)
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
            ) { Icon(Icons.Default.Edit, contentDescription = "Modifica ricordo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)) }
        }
        Card(
            modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(26.dp), ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.24f))
                .hazeBlur(HazeInput.Sources(detailHaze), detailStyle)
                .clip(RoundedCornerShape(26.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-0.8).sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Text(displayHaliMeoDate(entry.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(
                    visible = bitmap != null,
                    enter = if (reduceAnimationsState.value) fadeIn(tween(0)) else fadeIn(tween(180, easing = OneUiMotionEasing)) + expandVertically(animationSpec = tween(220, easing = OneUiMotionEasing)),
                    exit = fadeOut(tween(100))
                ) {
                    bitmap?.let { photo ->
                        Image(photo.asImageBitmap(), contentDescription = "Foto del ricordo", modifier = Modifier.fillMaxWidth()
                            .heightIn(max = 620.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                    }
                }
                SelectionContainer {
                    Text(entry.body, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable private fun EditScreen(existing: DiaryEntry?, onSave: (String,String,String,String,String?)->Unit, onCancel:()->Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var cat by remember(existing?.id) { mutableStateOf(existing?.category ?: "Ricordo") }
    var date by remember(existing?.id) { mutableStateOf(existing?.memoryDate?.let { displayHaliMeoDate(it) } ?: displayHaliMeoDate(java.time.LocalDate.now().toString())) }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri) }
    val context = LocalContext.current
    val editHaze = rememberHazeState()
    val editGlassTint = frostedSurfaceColor()
    val editGlassStyleBackground = MaterialTheme.colorScheme.surface
    val editGlassStyle = remember(editGlassTint) {
        HazeBlurStyle {
            backgroundColor(editGlassStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(editGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(editGlassTint))
        }
    }
    val editScrollState = rememberScrollState()
    val editHeaderCollapsed by remember { derivedStateOf { editScrollState.value > 48 } }
    val editTitleScale by animateFloatAsState(
        targetValue = if (editHeaderCollapsed) 0.76f else 1f,
        animationSpec = if (reduceAnimationsState.value) tween(0) else tween(90, easing = OneUiMotionEasing),
        label = "editTitleScale"
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
        }.onFailure { Toast.makeText(context, "Fotocamera non disponibile: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val photoBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUri, context) {
        value = photoUri?.takeIf { it.isNotBlank() }?.let { decodeMemoryPhoto(context, it, maxDimension = 2000) }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Transparent).hazeSource(editHaze).verticalScroll(editScrollState).imePadding().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (existing == null) "Nuovo ricordo" else "Modifica ricordo",
            modifier = Modifier.graphicsLayer {
                scaleX = editTitleScale
                scaleY = editTitleScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.7).sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "I momenti importanti, custoditi in un posto vostro.",
            modifier = Modifier.height(if (editHeaderCollapsed) 0.dp else 24.dp).graphicsLayer { alpha = if (editHeaderCollapsed) 0f else 1f },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp)).hazeBlur(HazeInput.Sources(editHaze), editGlassStyle).clip(RoundedCornerShape(26.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), singleLine = true)
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Data (gg/mm/aa)") }, placeholder = { Text("26/12/26") }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(body, { body = it }, label = { Text("Ricordo") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), shape = RoundedCornerShape(18.dp), minLines = 5)
                Text("FOTO DEL RICORDO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Foto allegata al ricordo",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = frostedSurfaceColor(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor())
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(5.dp))
                            Text("Aggiungi una foto dalla galleria", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoUri == null) "Galleria" else "Cambia foto", maxLines = 1)
                    }
                    OutlinedButton(onClick = { launchFullResolutionCamera() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Fotocamera", maxLines = 1)
                    }
                }
                if (photoUri != null) {
                    OutlinedButton(onClick = { photoUri = "" }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Rimuovi foto")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Annulla") }
                    Button(onClick = {
                        val parsedDate = parseHaliMeoDate(date)
                        if (title.isNotBlank() && body.isNotBlank() && parsedDate != null) onSave(title, body, cat, parsedDate.toString(), photoUri)
                        else if (parsedDate == null) Toast.makeText(context, "Inserisci una data valida nel formato gg/mm/aa", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f), enabled = title.isNotBlank() && body.isNotBlank(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Salva")
                    }
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
            backgroundColor(tripsGlassStyleBackground.copy(alpha = 0.82f))
            colorEffects(listOf(HazeColorEffect.tint(tripsGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(tripsGlassTint))
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
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.65).sp),
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
        val tripPressed by tripInteraction.collectIsPressedAsState()
        val tripScale by animateFloatAsState(
            targetValue = if (tripPressed && !reduceAnimationsState.value) 0.975f else 1f,
            animationSpec = if (reduceAnimationsState.value) tween(0) else spring(dampingRatio = 0.62f, stiffness = 520f),
            label = "tripCardPress"
        )
        Surface(
            modifier = Modifier.fillMaxWidth()
                 .graphicsLayer { scaleX = tripScale; scaleY = tripScale }
                .hazeBlur(HazeInput.Sources(tripsHaze), tripsGlassStyle)
                .clip(RoundedCornerShape(26.dp))
                .clickable(interactionSource = tripInteraction, indication = null, onClick = onOpenMonaco),
            shape = RoundedCornerShape(26.dp),
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
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                return true
                            }
                            return false
                        }
                    }
                    loadUrl("file:///android_asset/missione-monaco.html")
                }
            },
            update = { view ->
                webViewRef = view
                if (view.url.isNullOrBlank()) view.loadUrl("file:///android_asset/missione-monaco.html")
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
