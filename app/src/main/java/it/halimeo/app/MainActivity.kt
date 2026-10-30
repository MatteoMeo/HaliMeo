package it.halimeo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
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
import androidx.navigation.compose.*
import it.halimeo.app.data.*
import it.halimeo.app.network.*
import it.halimeo.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
@Composable private fun frostedSurfaceColor(): Color = when { reduceStaticEffectsState.value && isHaliMeoDark() -> Color(0xF0141921); reduceStaticEffectsState.value -> Color(0xF2FFFFFF); isHaliMeoDark() -> Color(0x99101419); else -> Color(0xB8FFFFFF) }
@Composable private fun frostedSurfaceStrongColor(): Color = when { reduceStaticEffectsState.value && isHaliMeoDark() -> Color(0xF51A2029); reduceStaticEffectsState.value -> Color(0xF8FFFFFF); isHaliMeoDark() -> Color(0xCC0C0F12); else -> Color(0xD6FFFFFF) }
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
        customBackgroundColorState.value = prefs.getString("background_color", "default") ?: "default"
        customBackgroundUriState.value = prefs.getString("background_photo", null)
        backgroundBlurState.value = prefs.getFloat("background_blur", 0f)
        backgroundOverlayState.value = prefs.getFloat("background_overlay", 0.48f)
        backgroundBrightnessState.value = readBackgroundBrightness(prefs)
        reduceAnimationsState.value = prefs.getBoolean("reduce_animations", false)
        reduceStaticEffectsState.value = prefs.getBoolean("reduce_static_effects", false)
        val preferredHz = prefs.getInt("preferred_refresh_rate", 120).coerceIn(48, 120)
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
    val backgroundAccent = when (customBackgroundColorState.value) {
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
        else -> scheme.primary
    }
    val adaptiveScheme = scheme.copy(primary = backgroundAccent, secondary = backgroundAccent.copy(alpha = 0.88f))
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
        // Request a high interactive refresh rate, but do not pin a display mode:
        // Samsung/Android can then adapt dynamically (e.g. 120/96/90Hz while active
        // and 60/48Hz for quieter content), subject to device and system policy.
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }
        lastSyncAtState.value = getSharedPreferences("halimeo_preferences", MODE_PRIVATE).getLong("last_sync_at", 0L)
        setContent { HaliMeoTheme { HaliMeoRoot() } }
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
    val favoritesPrefs = remember { ctx.getSharedPreferences("halimeo_favorites", android.content.Context.MODE_PRIVATE) }
    var favoriteIds by remember { mutableStateOf(favoritesPrefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    fun toggleFavorite(id: String) {
        val updated = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
        favoriteIds = updated
        favoritesPrefs.edit().putStringSet("ids", updated.toSet()).apply()
    }
    val featuredPrefs = remember { ctx.getSharedPreferences("halimeo_featured", android.content.Context.MODE_PRIVATE) }
    var featuredIds by remember { mutableStateOf(featuredPrefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    fun toggleFeatured(id: String) {
        val updated = if (id in featuredIds) featuredIds - id else featuredIds + id
        featuredIds = updated
        featuredPrefs.edit().putStringSet("ids", updated.toSet()).apply()
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
        title={Text("HaliMeo", fontWeight=FontWeight.ExtraBold, style=MaterialTheme.typography.headlineSmall.copy(letterSpacing=(-1.1).sp, brush=Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))))},
        modifier=Modifier.heightIn(min=56.dp),
        colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent, scrolledContainerColor=Color.Transparent, titleContentColor=MaterialTheme.colorScheme.onSurface,actionIconContentColor=MaterialTheme.colorScheme.onSurface),
        actions={
            IconButton(onClick={activity.lifecycleScope.launch{runCatching{repo.syncNow()}.onSuccess{val now=System.currentTimeMillis();ctx.getSharedPreferences("halimeo_preferences",android.content.Context.MODE_PRIVATE).edit().putLong("last_sync_at",now).apply();lastSyncAtState.value=now;Toast.makeText(ctx,"Sincronizzazione completata",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(ctx,"Offline: dati conservati sul dispositivo",Toast.LENGTH_LONG).show()}}}){Icon(Icons.Default.CloudSync,"Sincronizza")}
            IconButton(onClick={activity.lifecycleScope.launch{session.clear();signed=false}}){Icon(Icons.AutoMirrored.Filled.Logout,"Esci")}
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
                    beyondViewportPageCount = 1,
                    key = { it }
                ) { page ->
                    when (page) {
                        0 -> DiaryScreen(entries, favoriteIds, featuredIds,
                            { editing = it; nav.navigate("edit") },
                            { toggleFavorite(it.id) },
                            { toggleFeatured(it.id) },
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
            composable("memory/{entryId}") { backStack ->
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
                                else pagerState.animateScrollToPage(destination, spring(dampingRatio = 0.94f, stiffness = 1500f))
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
                    selectPrimaryTab(0)
                }
                FloatingNavItem(
                    Modifier.weight(1f), primaryTab == 1, "Viaggi", Icons.Default.FlightTakeoff
                ) {
                    selectPrimaryTab(1)
                }
                FloatingNavItem(
                    Modifier.weight(1f), primaryTab == 2, "Impostazioni", Icons.Default.Settings
                ) {
                    selectPrimaryTab(2)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = 11.dp)
            .size(58.dp)
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = if (reduceStaticEffectsState.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.80f),
        border = androidx.compose.foundation.BorderStroke(1.dp, frostedEdgeColor()),
        shadowElevation = 7.dp
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable { editing = null; nav.navigate("edit") }, contentAlignment = Alignment.Center) {
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
    val customBase = when (backgroundChoice) {
        "blue" -> Color(0xFF071522)
        "lavender" -> Color(0xFF120E20)
        "mint" -> Color(0xFF071A14)
        "rose" -> Color(0xFF210D16)
        else -> Color.Black
    }
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
        } else if (backgroundChoice == "default") {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF0A1426),
                        0.42f to Color(0xFF080D16),
                        1.0f to Color(0xFF05070C)
                    )
                )
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = driftX.dp, y = (-140).dp + driftY.dp).size(460.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.primary.copy(alpha = 0.15f),
                        0.42f to Color(0xFF8878FF).copy(alpha = 0.055f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-210).dp - driftX.dp * 0.55f, y = 40.dp + driftY.dp).size(380.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.tertiary.copy(alpha = 0.075f),
                        0.48f to palette.tertiary.copy(alpha = 0.028f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 190.dp - driftX.dp * 0.45f, y = 150.dp - driftY.dp).size(400.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF55D8E8).copy(alpha = 0.075f),
                        0.45f to palette.secondary.copy(alpha = 0.025f),
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
        animationSpec = tween(durationMillis = 55),
        label = "navItemIcon"
    )
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 0.96f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 900f),
        label = "navItemScale"
    )
    Box(
        modifier = modifier.fillMaxHeight()
            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
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
    val pendingSyncCount = diaryEntries.count { it.syncState != "SYNCED" }
    val lastSyncAt by lastSyncAtState
    var isOwner by remember { mutableStateOf<Boolean?>(null) }
    var inviteCode by remember { mutableStateOf("") }
    var inviteBusy by remember { mutableStateOf(false) }
    var inviteMessage by remember { mutableStateOf("") }

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
    val settingsGlassStyle = remember(settingsGlassTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
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
                        Text("PROFILO E ACCOUNT", color = accentText, style = MaterialTheme.typography.labelLarge)
                        if ("profile" !in expandedSettingsSections) Text("Il vostro account e la foto profilo", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
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
                        Text("ASPETTO", color = accentText, style = MaterialTheme.typography.labelLarge)
                        if ("appearance" !in expandedSettingsSections) Text("Tema, colori, sfondo e prestazioni", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
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
                Text("Sfondo", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val backgroundOptions = listOf(
                    "default" to "Predefinito", "blue" to "Blu", "lavender" to "Lavanda",
                    "mint" to "Menta", "rose" to "Rosa", "amber" to "Ambra",
                    "coral" to "Corallo", "red" to "Rosso", "orange" to "Arancione",
                    "yellow" to "Giallo", "green" to "Verde", "teal" to "Ottanio",
                    "cyan" to "Ciano", "indigo" to "Indaco", "violet" to "Viola",
                    "magenta" to "Magenta"
                )
                val accentSwatches = mapOf(
                    "blue" to Color(0xFF3E91FF), "lavender" to Color(0xFF8878FF),
                    "mint" to Color(0xFF2F9DAB), "rose" to Color(0xFFD66A9B),
                    "amber" to Color(0xFFFFB547), "coral" to Color(0xFFFF786B),
                    "red" to Color(0xFFFF525F), "orange" to Color(0xFFFF8A35),
                    "yellow" to Color(0xFFE3C84B), "green" to Color(0xFF58C98D),
                    "teal" to Color(0xFF31C7B3), "cyan" to Color(0xFF45C8E8),
                    "indigo" to Color(0xFF6575F5), "violet" to Color(0xFFB17CFF),
                    "magenta" to Color(0xFFE56CCB)
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    backgroundOptions.forEach { option ->
                        val swatch = accentSwatches[option.first] ?: MaterialTheme.colorScheme.primary
                        FilterChip(
                            selected = backgroundColor == option.first,
                            onClick = {
                                backgroundColor = option.first
                                backgroundPhoto = null
                                customBackgroundColorState.value = option.first
                                customBackgroundUriState.value = null
                                ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putString("background_color", option.first).remove("background_photo").apply()
                            },
                            label = { Text(option.second) },
                            leadingIcon = { Box(Modifier.size(13.dp).clip(CircleShape).background(swatch)) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { backgroundPicker.launch(arrayOf("image/*")) }, shape = RoundedCornerShape(22.dp)) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (backgroundColor == "photo") "Cambia foto" else "Usa una foto")
                    }
                    if (backgroundColor == "photo") {
                        TextButton(onClick = {
                            backgroundColor = "default"
                            backgroundPhoto = null
                            customBackgroundColorState.value = "default"
                            customBackgroundUriState.value = null
                            ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putString("background_color", "default").remove("background_photo").apply()
                        }) { Text("Rimuovi") }
                    }
                }
                Text("Puoi scegliere un colore oppure una foto dalla galleria.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                AnimatedVisibility(visible = backgroundColor == "photo") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sfocatura", color = primaryText, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${backgroundBlur.toInt()} dp", color = secondaryText, style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(value = backgroundBlur, onValueChange = { backgroundBlur = it; saveBackgroundTuning() }, valueRange = 0f..24f, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text("Prestazioni e movimento", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val prefs = ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                var selectedRefreshRate by remember { mutableIntStateOf(prefs.getInt("preferred_refresh_rate", 120)) }
                var reduceAnimations by remember { mutableStateOf(prefs.getBoolean("reduce_animations", false)) }
                var reduceStaticEffects by remember { mutableStateOf(prefs.getBoolean("reduce_static_effects", false)) }
                Text("Frequenza schermo", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(48, 60, 90, 120).forEachIndexed { index, hz ->
                        SegmentedButton(
                            selected = selectedRefreshRate == hz,
                            onClick = {
                                selectedRefreshRate = hz
                                prefs.edit().putInt("preferred_refresh_rate", hz).apply()
                                activity.window.attributes = activity.window.attributes.apply { preferredRefreshRate = hz.toFloat() }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 4)
                        ) { Text("$hz", maxLines = 1) }
                    }
                }
                Text("Richiede uno schermo compatibile: Android può comunque scegliere una frequenza diversa in base al contenuto e al risparmio energetico.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
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
                Row(Modifier.fillMaxWidth().clickable { expandedSettingsSections = if ("date" in expandedSettingsSections) expandedSettingsSections - "date" else expandedSettingsSections + "date" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DATA SPECIALE", color = accentText, style = MaterialTheme.typography.labelLarge)
                        if ("date" !in expandedSettingsSections) Text("Date importanti e promemoria", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
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
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(settingsHaze), settingsGlassStyle).clip(RoundedCornerShape(24.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { expandedSettingsSections = if ("sync" in expandedSettingsSections) expandedSettingsSections - "sync" else expandedSettingsSections + "sync" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = accentText, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SINCRONIZZAZIONE", color = accentText, style = MaterialTheme.typography.labelLarge)
                        if ("sync" !in expandedSettingsSections) Text("Backup e stato dei tuoi ricordi", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
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
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { expandedSettingsSections = if ("invites" in expandedSettingsSections) expandedSettingsSections - "invites" else expandedSettingsSections + "invites" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = accentText, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INVITI", color = accentText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if ("invites" !in expandedSettingsSections) Text("Crea e condividi un invito privato", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
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
    var redeem by remember{mutableStateOf(false)};var code by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    val authHaze = rememberHazeState()
    val authGlassTint = frostedSurfaceStrongColor()
    val authGlassStyle = remember(authGlassTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
            colorEffects(listOf(HazeColorEffect.tint(authGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(authGlassTint))
        }
    }
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
        Color(0xFFFFA98F), Color(0xFFD5A6FF), Color(0xFFA9D77D)
    ) else listOf(
        Color(0xFF175E91), Color(0xFF9C3155), Color(0xFF855000),
        Color(0xFF5D42A3), Color(0xFF17694F), Color(0xFF126B73),
        Color(0xFF9A4933), Color(0xFF7545A0), Color(0xFF4B6F24)
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
    favoriteIds: Set<String>,
    featuredIds: Set<String>,
    onEdit: (DiaryEntry) -> Unit,
    onToggleFavorite: (DiaryEntry) -> Unit,
    onToggleFeatured: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit,
    onOpenMemory: (DiaryEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Tutte") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    val calendarState = rememberDatePickerState()
    var pendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showHighlightsSettings by remember { mutableStateOf(false) }
    val highlightsPrefs = remember { diaryContext.getSharedPreferences("halimeo_highlights", android.content.Context.MODE_PRIVATE) }
    var highlightsTitle by remember { mutableStateOf(highlightsPrefs.getString("title", "I vostri momenti") ?: "I vostri momenti") }
    var highlightsNewestFirst by remember { mutableStateOf(highlightsPrefs.getBoolean("newest_first", true)) }
    val feedbackView = LocalView.current
    val diaryContext = LocalContext.current
    val diaryHaze = rememberHazeState()
    val diaryGlassTint = frostedSurfaceColor()
    val diaryGlassStyle = remember(diaryGlassTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
            colorEffects(listOf(HazeColorEffect.tint(diaryGlassTint.copy(alpha = 0.48f))))
            fallbackColorEffect(HazeColorEffect.tint(diaryGlassTint))
        }
    }
    val listState = rememberLazyListState()
    // Cache diary header data instead of sorting favourites and parsing special dates
    // again whenever Compose recomposes the LazyColumn.
    val featuredMemories = remember(entries, featuredIds) {
        entries.asSequence().filter { it.id in featuredIds }.sortedByDescending { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }.toList()
    }
    val memoriesToday = remember(entries) {
        val today = java.time.LocalDate.now()
        entries.mapNotNull { entry ->
            parseHaliMeoDate(entry.memoryDate)?.takeIf { it.year < today.year && it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth }?.let { entry to it }
        }.sortedByDescending { it.second }.map { it.first }
    }
    val photoHighlights = remember(entries, highlightsNewestFirst) {
        val sorted = entries.filter { !it.photoUri.isNullOrBlank() }
            .sortedBy { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }
        (if (highlightsNewestFirst) sorted.reversed() else sorted).take(12)
    }
    val upcomingSpecial = remember(entries, favoriteIds) {
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
    val categories = remember(entries) {
        listOf("Tutte") + entries.asSequence().map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted().toList()
    }
    val filtered = remember(entries, query, selectedDate, selectedCategory, favoritesOnly, favoriteIds) {
        val matches = entries.filter {
            (it.title.contains(query, true) || it.body.contains(query, true) || it.category.contains(query, true)) &&
                (selectedDate == null || it.memoryDate == selectedDate) &&
                (selectedCategory == "Tutte" || it.category.equals(selectedCategory, ignoreCase = true)) &&
                (!favoritesOnly || it.id in favoriteIds)
        }
        matches.sortedByDescending { parseHaliMeoDate(it.memoryDate) ?: java.time.LocalDate.MIN }
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, letterSpacing = (-0.65).sp),
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
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isHaliMeoDark()) 0.56f else 0.72f)
                ) {
                    Text(
                        entries.size.toString(),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { calendarState.selectedDateMillis = selectedDate?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }; showCalendar = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Filtra per data", tint = if (selectedDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
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
                    selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                    shape = RoundedCornerShape(50),
                    label = { Text("Preferiti", maxLines = 1) },
                    leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = categoryAccent("momento").copy(alpha = if (isHaliMeoDark()) 0.28f else 0.16f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface, selectedLeadingIconColor = categoryAccent("momento"),
                        containerColor = frostedSurfaceColor(), labelColor = MaterialTheme.colorScheme.onSurfaceVariant, iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                categories.forEach { category ->
                    val categoryColor = if (category == "Tutte") MaterialTheme.colorScheme.primary else categoryAccent(category)
                    FilterChip(
                        selected = selectedCategory == category, onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(50),
                        label = { Text(category, maxLines = 1) },
                        leadingIcon = if (selectedCategory == category) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else ({ Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(16.dp)) }),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = if (isHaliMeoDark()) 0.28f else 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface, selectedLeadingIconColor = categoryColor,
                            containerColor = frostedSurfaceColor(), labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = if (isHaliMeoDark()) categoryColor else categoryColor.copy(alpha = 0.9f)
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
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
                    if (!favoritesOnly && query.isBlank() && selectedCategory == "Tutte") {
                        if (photoHighlights.isNotEmpty()) {
                            item(key = "memory_highlights") {
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text(highlightsTitle, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { showHighlightsSettings = true }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Tune, contentDescription = "Personalizza momenti in evidenza", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        photoHighlights.forEach { highlight ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                                                val storyBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, highlight.photoUri) {
                                                    value = highlight.photoUri?.let { decodeScaledBitmap(diaryContext, it, maxDimension = 720) }
                                                }
                                                Surface(
                                                    modifier = Modifier.size(62.dp).hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle).clip(CircleShape).clickable { onOpenMemory(highlight) },
                                                    shape = CircleShape,
                                                    color = categoryAccent(highlight.category).copy(alpha = 0.18f),
                                                    border = androidx.compose.foundation.BorderStroke(2.dp, categoryAccent(highlight.category).copy(alpha = 0.8f))
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (storyBitmap != null) Image(
                                                            bitmap = storyBitmap!!.asImageBitmap(),
                                                            contentDescription = "Apri ${highlight.title}",
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        ) else Icon(Icons.Default.Photo, contentDescription = null, tint = categoryAccent(highlight.category), modifier = Modifier.size(26.dp))
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
                                            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle).clip(RoundedCornerShape(18.dp)).clickable { onOpenMemory(memory) },
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
                        item(key = "featured_memories") {
                            val featured = featuredMemories
                            if (featured.isNotEmpty() || upcomingSpecial != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text("In evidenza", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    featured.forEach { favorite ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle).clip(RoundedCornerShape(18.dp)).clickable { onOpenMemory(favorite) },
                                            shape = RoundedCornerShape(18.dp), color = frostedSurfaceStrongColor(),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, categoryAccent(favorite.category).copy(alpha = 0.30f))
                                        ) {
                                            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Favorite, contentDescription = null, tint = categoryAccent(favorite.category), modifier = Modifier.size(18.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(favorite.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(displayHaliMeoDate(favorite.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                    upcomingSpecial?.let { (item, date, daysUntil) ->
                                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f)) {
                                                    Text(item.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                                    Text(if (daysUntil == 0L) "È oggi!" else "Mancano $daysUntil giorni · ${displayHaliMeoDate(date.toString())}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(filtered, key = { _, entry -> entry.id }, contentType = { _, _ -> "diary_entry" }) { _, entry ->
                        val expanded = entry.id in expandedEntryIds
                        val accent = categoryAccent(entry.category, entry.id)
                        val photoContext = LocalContext.current
                        val entryPhoto by produceState<android.graphics.Bitmap?>(initialValue = null, entry.photoUri, photoContext, expanded) {
                            // Decode only after opening a card; eagerly decoding every visible photo
                            // causes avoidable IO, allocations and frame drops while scrolling.
                            value = entry.photoUri?.let { decodeScaledBitmap(photoContext, it, maxDimension = 2400) }
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
                                .hazeBlur(HazeInput.Sources(diaryHaze), diaryGlassStyle)
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
                                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.075f), Color.Transparent, accent.copy(alpha = 0.025f)))));
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
                                            Icon(categoryIcon(entry.category), contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
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
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                            Text(displayHaliMeoDate(entry.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.13f)) {
                                                Text(entry.category.ifBlank { "Ricordo" }, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    val isFavorite = entry.id in favoriteIds
                                    IconButton(onClick = { feedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onToggleFeatured(entry) }) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = if (entry.id in featuredIds) "Rimuovi da In evidenza" else "Aggiungi a In evidenza",
                                            tint = if (entry.id in featuredIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { feedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onToggleFavorite(entry) }) {
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
    if (showHighlightsSettings) {
        var titleDraft by remember(highlightsTitle) { mutableStateOf(highlightsTitle) }
        AlertDialog(
            onDismissRequest = { showHighlightsSettings = false },
            title = { Text("Personalizza i momenti") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = titleDraft, onValueChange = { titleDraft = it.take(32) }, label = { Text("Titolo della raccolta") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ordine dei momenti", fontWeight = FontWeight.SemiBold)
                            Text("Prima i più recenti oppure i più vecchi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = highlightsNewestFirst, onCheckedChange = {
                            highlightsNewestFirst = it
                            highlightsPrefs.edit().putBoolean("newest_first", it).apply()
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    highlightsTitle = titleDraft.trim().ifBlank { "I vostri momenti" }
                    highlightsPrefs.edit().putString("title", highlightsTitle).apply()
                    showHighlightsSettings = false
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { showHighlightsSettings = false }) { Text("Annulla") } }
        )
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
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, entry.photoUri) {
        value = entry.photoUri?.let { decodeScaledBitmap(context, it, maxDimension = 2800) }
    }
    val detailHaze = rememberHazeState()
    val detailTint = frostedSurfaceColor()
    val detailStyle = remember(detailTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
            colorEffects(listOf(HazeColorEffect.tint(detailTint.copy(alpha = 0.50f))))
            fallbackColorEffect(HazeColorEffect.tint(detailTint))
        }
    }
    Column(
        Modifier.fillMaxSize().hazeSource(detailHaze).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
            Text("Il vostro ricordo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Modifica ricordo") }
        }
        Card(
            modifier = Modifier.fillMaxWidth().hazeBlur(HazeInput.Sources(detailHaze), detailStyle)
                .clip(RoundedCornerShape(26.dp)).border(1.dp, frostedEdgeColor(), RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayHaliMeoDate(entry.memoryDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AssistChip(onClick = {}, label = { Text(entry.category) })
                }
                if (bitmap != null) {
                    Image(bitmap!!.asImageBitmap(), contentDescription = "Foto del ricordo", modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 620.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
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
    val editGlassStyle = remember(editGlassTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
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
        value = photoUri?.takeIf { it.isNotBlank() }?.let { decodeScaledBitmap(context, it, maxDimension = 2400) }
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
                OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Data (gg/mm/aa)") }, placeholder = { Text("26/12/26") }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(cat, { cat = it }, label = { Text("Categoria") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text("Ricordo") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), minLines = 5)
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
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoUri == null) "Galleria" else "Cambia foto", maxLines = 1)
                    }
                    OutlinedButton(onClick = { launchFullResolutionCamera() }, modifier = Modifier.weight(1f)) {
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
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Annulla") }
                    Button(onClick = {
                        val parsedDate = parseHaliMeoDate(date)
                        if (title.isNotBlank() && body.isNotBlank() && parsedDate != null) onSave(title, body, cat, parsedDate.toString(), photoUri)
                        else if (parsedDate == null) Toast.makeText(context, "Inserisci una data valida nel formato gg/mm/aa", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f), enabled = title.isNotBlank() && body.isNotBlank()) {
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
    val tripsGlassStyle = remember(tripsGlassTint) {
        HazeBlurStyle {
            backgroundColor(Color(0xFF101419))
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
@Composable private fun MissionScreen(){
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E16)),
        factory = { ctx ->
            WebView(ctx).apply {
                // Keep WebView stable during Compose page transitions; this avoids black frames.
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(android.graphics.Color.rgb(10, 14, 22))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "file" || uri.scheme == "about") return false
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
            if (view.url.isNullOrBlank()) view.loadUrl("file:///android_asset/missione-monaco.html")
        }
    )
}
