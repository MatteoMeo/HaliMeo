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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import it.halimeo.app.data.*
import it.halimeo.app.network.*
import it.halimeo.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val Ink = Color.Black
private val Panel = Color(0xFF101114)
private val Mint = Color(0xFFB9DDF5)
private val Muted = Color(0xFFB4BAC5)
private val Elevated = Color(0xFF101317)
// Balanced One UI-inspired glass surfaces: translucent charcoal with a cool edge highlight.
private val FrostedSurface = Color(0xE6101419)
private val FrostedSurfaceStrong = Color(0xF2101419)
private val FrostedEdge = Color.White.copy(alpha = 0.17f)
@Composable private fun isHaliMeoDark(): Boolean = when (themeModeState.value) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
@Composable private fun frostedSurfaceColor(): Color = if (isHaliMeoDark()) FrostedSurface else Color(0xF5FFFFFF)
@Composable private fun frostedSurfaceStrongColor(): Color = if (isHaliMeoDark()) FrostedSurfaceStrong else Color(0xFCFFFFFF)
private val themeModeState = mutableStateOf("system")
private val customBackgroundColorState = mutableStateOf("default")
private val customBackgroundUriState = mutableStateOf<String?>(null)
private val backgroundBlurState = mutableStateOf(0f)
private val backgroundOverlayState = mutableStateOf(0.48f)
private val backgroundBrightnessState = mutableStateOf(0f)
private fun parseHaliMeoDate(value: String): java.time.LocalDate? {
    val trimmed = value.trim()
    return runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrNull()
}
private fun displayHaliMeoDate(value: String): String {
    val date = runCatching { java.time.LocalDate.parse(value) }.getOrNull() ?: return value
    return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"))
}
private val lastSyncAtState = mutableStateOf(0L)
private val HaliMeoBackdrop = Brush.verticalGradient(listOf(Color.Black, Color.Black, Color.Black))
private val TextPrimary = Color(0xFFF5F7FA)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoTheme(content: @Composable () -> Unit) {
    val mode by themeModeState
    val darkTheme = isHaliMeoDark()
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
        backgroundBrightnessState.value = prefs.getFloat("background_brightness", 0f)
    }
    val staticFontWidth = 100f
    val staticFontRoundness = 28f
    // Android 12+ supplies Material You / Monet colors derived from the device wallpaper.
    // Keep the app dark with a true-black canvas for AMOLED displays.
    val scheme = if (darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color(0xFF080A0C),
            surfaceVariant = Color(0xFF111519),
            onBackground = Color(0xFFF5F7FA),
            onSurface = Color(0xFFF5F7FA),
            onSurfaceVariant = Color(0xFFD0D4DC),
            outline = Color(0xFF30383E)
        )
    } else if (darkTheme) {
        darkColorScheme(
            primary=Mint, onPrimary=Color(0xFF102A3A), secondary=Color(0xFF72D9E8), onSecondary=Color(0xFF06272D),
            background=Ink, onBackground=TextPrimary, surface=Panel, onSurface=TextPrimary,
            surfaceVariant=Elevated, onSurfaceVariant=Muted, outline=Color(0xFF30383E)
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context).copy(
            background = Color(0xFFF3F6FA),
            surface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFFE5EBF0),
            onBackground = Color(0xFF171B20),
            onSurface = Color(0xFF171B20),
            onSurfaceVariant = Color(0xFF46515B),
            outline = Color(0xFF8996A1)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF315D78), onPrimary = Color.White,
            secondary = Color(0xFF286A73), onSecondary = Color.White,
            background = Color(0xFFF4F7FA), onBackground = Color(0xFF171B20),
            surface = Color(0xFFF8FAFC), onSurface = Color(0xFF171B20),
            surfaceVariant = Color(0xFFE5EBF0), onSurfaceVariant = Color(0xFF46515B),
            outline = Color(0xFF8996A1)
        )
    }
    val backgroundAccent = when (customBackgroundColorState.value) {
        "blue" -> Color(0xFF397CA6)
        "lavender" -> Color(0xFF7961B5)
        "mint" -> Color(0xFF287D65)
        "rose" -> Color(0xFFB34F75)
        else -> scheme.primary
    }
    val adaptiveScheme = scheme.copy(primary = backgroundAccent, secondary = backgroundAccent.copy(alpha = 0.88f))
    // Material 3 Expressive supplies the expressive motion system and updated component defaults.
    MaterialExpressiveTheme(
            colorScheme = adaptiveScheme,
            typography = rememberHaliMeoTypography(staticFontWidth, staticFontRoundness),
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes(
                small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(24.dp),
                large = RoundedCornerShape(32.dp)
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
        themeModeState.value = getSharedPreferences("halimeo_preferences", MODE_PRIVATE).getString("theme_mode", "system") ?: "system"
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
    if(signed==null){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){LoadingIndicator(modifier=Modifier.size(64.dp), color=MaterialTheme.colorScheme.primary)};return}
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
    val nav= rememberNavController(); val backStackEntry by nav.currentBackStackEntryAsState(); val currentRoute=backStackEntry?.destination?.route
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
    )},bottomBar={
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.weight(1f).shadow(14.dp, RoundedCornerShape(50), clip = false).clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50)),
                shape = RoundedCornerShape(50), color = frostedSurfaceColor(), tonalElevation = 0.dp
            ) {
                Row(Modifier.fillMaxWidth().height(68.dp).background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.055f), Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)))).padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FloatingNavItem(Modifier.weight(1f), currentRoute == "diary", "Diario", Icons.Default.Book) { nav.navigate("diary") { launchSingleTop = true; popUpTo("diary") { saveState = true } } }
                    FloatingNavItem(Modifier.weight(1f), currentRoute == "trips" || currentRoute == "tripMonaco", "Viaggi", Icons.Default.FlightTakeoff) { nav.navigate("trips") { launchSingleTop = true; restoreState = true } }
                    FloatingNavItem(Modifier.weight(1f), currentRoute == "settings", "Impostazioni", Icons.Default.Settings) { nav.navigate("settings") { launchSingleTop = true; restoreState = true } }
                }
            }
            AnimatedVisibility(visible = currentRoute == "diary", enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 }, exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it / 3 }) {
                val addInteraction = remember { MutableInteractionSource() }
                val addPressed by addInteraction.collectIsPressedAsState()
                val addScale by animateFloatAsState(targetValue = if (addPressed) 0.90f else 1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f), label = "addMemoryPress")
                Surface(
                    modifier = Modifier.size(62.dp).graphicsLayer { scaleX = addScale; scaleY = addScale }.clickable(interactionSource = addInteraction, indication = null, onClick = { editing = null; nav.navigate("edit") }),
                    shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shadowElevation = 8.dp
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Nuovo ricordo", modifier = Modifier.size(29.dp)) } }
            }
        }
    }, floatingActionButton = {}, content = { padding ->
        NavHost(nav,startDestination="diary",modifier=Modifier.padding(padding),enterTransition={fadeIn(tween(240))+slideInHorizontally(spring(dampingRatio=0.88f, stiffness=420f)){it/18}},exitTransition={fadeOut(tween(160))+slideOutHorizontally(tween(200)){ -it/24}},popEnterTransition={fadeIn(tween(240))+slideInHorizontally(spring(dampingRatio=0.88f, stiffness=420f)){ -it/18}},popExitTransition={fadeOut(tween(160))+slideOutHorizontally(tween(200)){it/24}}){
            composable("diary"){DiaryScreen(entries,favoriteIds,{editing=it;nav.navigate("edit")},{toggleFavorite(it.id)},{entry->activity.lifecycleScope.launch{repo.delete(entry.id)}})}
            composable("edit"){EditScreen(editing,{t,b,c,d,photo->activity.lifecycleScope.launch{repo.save(t,b,c,d,editing?.id,photo);editing=null;nav.popBackStack();runCatching{repo.syncNow()}}},{editing=null;nav.popBackStack()})}
            composable("trips"){TripsScreen(onOpenMonaco = { nav.navigate("tripMonaco") })}
            composable("tripMonaco"){MissionScreen()}
            composable("settings"){SettingsScreen(displayName=displayName,session=session,repo=repo,activity=activity,ctx=ctx,)}
        }
    })
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
    val customBase = when (backgroundChoice) {
        "blue" -> if (isHaliMeoDark()) Color(0xFF071522) else Color(0xFFDFEEFA)
        "lavender" -> if (isHaliMeoDark()) Color(0xFF120E20) else Color(0xFFECE7FA)
        "mint" -> if (isHaliMeoDark()) Color(0xFF071A14) else Color(0xFFE0F3EB)
        "rose" -> if (isHaliMeoDark()) Color(0xFF210D16) else Color(0xFFF9E4EC)
        else -> if (isHaliMeoDark()) Color.Black else Color(0xFFF3F6FA)
    }
    val backgroundBitmap = remember(backgroundUri) {
        backgroundUri?.let { value -> runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    }
    Box(modifier = modifier.clipToBounds().background(customBase)) {
        if (backgroundBitmap != null && backgroundChoice == "photo") {
            Image(bitmap = backgroundBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(photoBlur.dp), contentScale = ContentScale.Crop)
            val averageLuminance = remember(backgroundBitmap) {
                runCatching {
                    var total = 0L
                    var samples = 0
                    val stepX = (backgroundBitmap.width / 24).coerceAtLeast(1)
                    val stepY = (backgroundBitmap.height / 24).coerceAtLeast(1)
                    for (y in 0 until backgroundBitmap.height step stepY) for (x in 0 until backgroundBitmap.width step stepX) {
                        val pixel = backgroundBitmap.getPixel(x, y)
                        total += (0.2126 * android.graphics.Color.red(pixel) + 0.7152 * android.graphics.Color.green(pixel) + 0.0722 * android.graphics.Color.blue(pixel)).toLong()
                        samples++
                    }
                    if (samples == 0) 128f else total.toFloat() / samples
                }.getOrDefault(128f)
            }
            val useDarkOverlay = if (brightnessAdjustment == 0f) averageLuminance >= 128f else brightnessAdjustment < 0f
            val overlayColor = if (useDarkOverlay) Color.Black else Color.White
            val effectiveOverlay = if (isHaliMeoDark()) maxOf(overlayStrength, 0.58f) else maxOf(overlayStrength, 0.46f)
            Box(Modifier.fillMaxSize().background(overlayColor.copy(alpha = effectiveOverlay)))
        } else if (backgroundChoice == "default") {
            Box(
                Modifier.fillMaxSize().background(
                    if (isHaliMeoDark()) Brush.verticalGradient(
                        0.0f to Color(0xFF050608),
                        0.48f to Color(0xFF08090C),
                        1.0f to Color(0xFF030405)
                    ) else Brush.verticalGradient(
                        0.0f to Color(0xFFF5F8FC),
                        0.38f to Color(0xFFE6EEF7),
                        0.72f to Color(0xFFF0EAF8),
                        1.0f to Color(0xFFE8F2F1)
                    )
                )
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-170).dp).size(560.dp)
                .blur(150.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.primary.copy(alpha = 0.12f),
                        0.42f to palette.primary.copy(alpha = 0.055f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-250).dp, y = 40.dp).size(480.dp)
                .blur(155.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.tertiary.copy(alpha = 0.075f),
                        0.48f to palette.tertiary.copy(alpha = 0.028f),
                        1.0f to Color.Transparent
                    ), RoundedCornerShape(50)
                )
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 220.dp, y = 170.dp).size(500.dp)
                .blur(165.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.secondary.copy(alpha = 0.065f),
                        0.45f to palette.secondary.copy(alpha = 0.022f),
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
    val animatedContainer by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "navItemContainer"
    )
    val animatedIcon by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFD1D5D9),
        animationSpec = tween(durationMillis = 180),
        label = "navItemIcon"
    )
    val animatedLabel by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "navItemLabel"
    )
    val itemScale by animateFloatAsState(targetValue = if (selected) 1f else 0.94f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f), label = "navItemScale")
    val indicatorAlpha by animateFloatAsState(targetValue = if (selected) 1f else 0f, animationSpec = tween(220), label = "navIndicatorAlpha")
    Column(
        modifier = modifier.fillMaxHeight().graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .clip(RoundedCornerShape(50)).background(animatedContainer)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(50)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 34.dp, height = 27.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f * indicatorAlpha)))
            Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = animatedIcon)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = animatedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    var themeMode by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("theme_mode", "system") ?: "system") }
    var specialDateLabel by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("special_date_label", "") ?: "") }
    var specialDate by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("special_date", "")?.let { displayHaliMeoDate(it) } ?: "") }
    var backgroundColor by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("background_color", "default") ?: "default") }
    var backgroundPhoto by remember { mutableStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getString("background_photo", null)) }
    var backgroundBlur by remember { mutableFloatStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getFloat("background_blur", 0f)) }
    var backgroundOverlay by remember { mutableFloatStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getFloat("background_overlay", 0.48f)) }
    var backgroundBrightness by remember { mutableFloatStateOf(ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).getFloat("background_brightness", 0f)) }
    fun saveBackgroundTuning() {
        ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putFloat("background_blur", backgroundBlur).putFloat("background_overlay", backgroundOverlay).putFloat("background_brightness", backgroundBrightness).apply()
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

    Column(
        Modifier.fillMaxSize().background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("IMPOSTAZIONI", color = primaryText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)).border(1.dp, FrostedEdge, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Text("ACCOUNT", color = accentText, style = MaterialTheme.typography.labelLarge)
                }
                Text(displayName, color = primaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("I ricordi vengono salvati localmente prima della sincronizzazione.", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, FrostedEdge, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Text("ASPETTO", color = accentText, style = MaterialTheme.typography.labelLarge)
                }
                Text("Adatta HaliMeo al tuo dispositivo", color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("system" to "Sistema", "dark" to "Scura", "light" to "Chiara").forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = themeMode == option.first,
                            onClick = {
                                themeMode = option.first
                                themeModeState.value = option.first
                                ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putString("theme_mode", option.first).apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) { Text(option.second, maxLines = 1) }
                    }
                }
                Text("La scelta viene salvata sul dispositivo.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                Text("Sfondo", color = primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val backgroundOptions = listOf("default" to "Predefinito", "blue" to "Blu", "lavender" to "Lavanda", "mint" to "Menta", "rose" to "Rosa")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    backgroundOptions.forEach { option ->
                        val swatch = when (option.first) {
                            "blue" -> if (isHaliMeoDark()) Color(0xFF397CA6) else Color(0xFF8CCFFF)
                            "lavender" -> if (isHaliMeoDark()) Color(0xFF7961B5) else Color(0xFFC2B2FF)
                            "mint" -> if (isHaliMeoDark()) Color(0xFF287D65) else Color(0xFF8EDFC3)
                            "rose" -> if (isHaliMeoDark()) Color(0xFFB34F75) else Color(0xFFFFAFC8)
                            else -> MaterialTheme.colorScheme.primary
                        }
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
                        Slider(value = backgroundBlur, onValueChange = { backgroundBlur = it; saveBackgroundTuning() }, valueRange = 0f..24f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Intensità overlay", color = primaryText, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${(backgroundOverlay * 100).toInt()}%", color = secondaryText, style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(value = backgroundOverlay, onValueChange = { backgroundOverlay = it; saveBackgroundTuning() }, valueRange = 0.12f..0.82f)
                        Text("Luminosità e contrasto", color = primaryText, style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(-1f to "Più scuro", 0f to "Auto", 1f to "Più chiaro").forEachIndexed { index, option ->
                                SegmentedButton(selected = backgroundBrightness == option.first, onClick = { backgroundBrightness = option.first; saveBackgroundTuning() }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(option.second, maxLines = 1) }
                            }
                        }
                        Text("Auto sceglie un velo chiaro o scuro in base alla luminosità media della foto.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, FrostedEdge, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = accentText, modifier = Modifier.size(18.dp))
                    Text("DATA SPECIALE", color = accentText, style = MaterialTheme.typography.labelLarge)
                }
                Text("Tenete a portata di mano una data importante", color = primaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = specialDateLabel, onValueChange = { specialDateLabel = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nome (es. compleanno)") })
                OutlinedTextField(value = specialDate, onValueChange = { specialDate = it.take(10) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp), label = { Text("Data (gg/mm/aa)") }, placeholder = { Text("26/12/26") })
                Button(
                    onClick = {
                        val parsed = parseHaliMeoDate(specialDate)
                        if (specialDateLabel.isNotBlank() && parsed != null) {
                            ctx.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE).edit().putString("special_date_label", specialDateLabel.trim()).putString("special_date", parsed.toString()).apply()
                            specialDate = displayHaliMeoDate(parsed.toString())
                            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                scheduleSpecialDateReminder(ctx, specialDateLabel.trim(), parsed)
                            }
                            Toast.makeText(ctx, "Data speciale salvata", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(ctx, "Inserisci un nome e una data valida", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salva data") }
                val parsedDate = parseHaliMeoDate(specialDate)
                if (parsedDate != null && specialDateLabel.isNotBlank()) {
                    val today = java.time.LocalDate.now()
                    val next = if (parsedDate.isBefore(today)) parsedDate.withYear(today.year + 1) else parsedDate
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, next)
                    Text(if (days == 0L) "È oggi: $specialDateLabel!" else "Mancano $days giorni a $specialDateLabel", color = accentText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("Promemoria locale alle 09:00 nel giorno scelto, se le notifiche sono abilitate.", color = secondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = accentText, modifier = Modifier.size(20.dp))
                    Text("SINCRONIZZAZIONE", color = accentText, style = MaterialTheme.typography.labelLarge)
                }
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

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = frostedSurfaceStrongColor())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = accentText, modifier = Modifier.size(22.dp))
                    Text("INVITI", color = accentText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
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
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp)),
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

@Composable private fun AuthScreen(onLogin:(String,String,(String)->Unit)->Unit,onRedeem:(String,String,String,String,(String)->Unit)->Unit){
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
    Column(Modifier.fillMaxSize().background(HaliMeoBackdrop).padding(20.dp),verticalArrangement=Arrangement.Center){Text("HaliMeo",style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black);Text("Un posto solo vostro.",color=Muted);Spacer(Modifier.height(16.dp))
        Card(colors=CardDefaults.cardColors(containerColor=frostedSurfaceStrongColor()), modifier=Modifier.fillMaxWidth().border(1.dp, FrostedEdge, RoundedCornerShape(24.dp)), shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val dotColor = when (serverOnline) { true -> Color(0xFF86E3B0); false -> Color(0xFFFF8E9B); null -> Color(0xFFFFD18A) }
                    Box(Modifier.size(10.dp).background(dotColor, RoundedCornerShape(50)))
                    Text(serverMessage, color=dotColor, style=MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(serverUrl, { serverUrl=it; urlError="" }, label={Text("Indirizzo del server")}, placeholder={Text("http://192.168.1.50:8000")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                if (urlError.isNotBlank()) Text(urlError, color=Color(0xFFFF879B), style=MaterialTheme.typography.bodySmall)
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
        if(error.isNotBlank())Text(error,color=Color(0xFFFF879B));Spacer(Modifier.height(12.dp))
        Button(enabled=!busy,onClick={busy=true;val fail:(String)->Unit={error=it;busy=false};if(redeem)onRedeem(code,name,email,pass,fail)else onLogin(email,pass,fail)},modifier=Modifier.fillMaxWidth()){Text(if(redeem)"ACCETTA INVITO" else "ACCEDI")}
        TextButton(onClick={redeem=!redeem;error=""}){Text(if(redeem)"Ho già un account" else "Ho un codice invito")}
    }
}
private fun categoryAccent(category: String): Color {
    val normalized = category.trim().lowercase()
    return when {
        listOf("viagg", "vacanz", "gita", "monaco", "avventura").any(normalized::contains) -> Color(0xFF83C9FF)
        listOf("compleanno", "anniversario", "festa", "regalo").any(normalized::contains) -> Color(0xFFFF9FBD)
        listOf("cena", "ristorante", "cibo", "pranzo", "aperitivo").any(normalized::contains) -> Color(0xFFFFC878)
        listOf("momento", "ricordo", "giornata", "speciale").any(normalized::contains) -> Color(0xFFB9A5FF)
        listOf("foto", "fotografia", "immagine").any(normalized::contains) -> Color(0xFF8DE0C1)
        else -> Color(0xFFB9DDF5)
    }
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
    onEdit: (DiaryEntry) -> Unit,
    onToggleFavorite: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Tutte") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val feedbackView = LocalView.current
    var headerCollapsed by remember { mutableStateOf(false) }
    val diaryNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) headerCollapsed = true
                else if (available.y > 8f) headerCollapsed = false
                return Offset.Zero
            }
        }
    }
    val headerTitleSize by animateFloatAsState(
        targetValue = if (headerCollapsed) 23f else 31f,
        animationSpec = tween(180),
        label = "diaryHeaderTitleSize"
    )
    val categories = listOf("Tutte") + entries.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val filtered = entries.filter {
        (it.title.contains(query, true) || it.body.contains(query, true) || it.category.contains(query, true)) &&
            (selectedCategory == "Tutte" || it.category.equals(selectedCategory, ignoreCase = true)) &&
            (!favoritesOnly || it.id in favoriteIds)
    }.let { matches -> if (newestFirst) matches.sortedByDescending { it.memoryDate } else matches.sortedBy { it.memoryDate } }

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(diaryNestedScroll)
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = headerTitleSize.sp, letterSpacing = (-0.65).sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.07f)
                ) {
                    Text(
                        entries.size.toString(),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                modifier = Modifier.fillMaxWidth(),
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
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Preferiti") },
                leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = categoryAccent("momento").copy(alpha = if (isHaliMeoDark()) 0.28f else 0.20f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = categoryAccent("momento"),
                    containerColor = frostedSurfaceColor(),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            categories.forEach { category ->
                val categoryColor = if (category == "Tutte") MaterialTheme.colorScheme.primary else categoryAccent(category)
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    leadingIcon = if (selectedCategory == category) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else ({ Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(16.dp)) }),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = categoryColor.copy(alpha = if (isHaliMeoDark()) 0.28f else 0.20f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLeadingIconColor = categoryColor,
                        containerColor = frostedSurfaceColor(),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = categoryColor.copy(alpha = 0.85f)
                    )
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { newestFirst = !newestFirst }) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (newestFirst) "Più recenti" else "Più vecchi")
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
                                color = Muted
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
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
                        Text("Nessun ricordo trovato", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text("Prova con un'altra parola.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    if (!favoritesOnly && query.isBlank() && selectedCategory == "Tutte") {
                        item(key = "featured_memories") {
                            val featured = entries.filter { it.id in favoriteIds }.sortedByDescending { it.memoryDate }.take(2)
                            val prefs = LocalContext.current.getSharedPreferences("halimeo_preferences", android.content.Context.MODE_PRIVATE)
                            val specialLabel = prefs.getString("special_date_label", "").orEmpty()
                            val specialDate = parseHaliMeoDate(prefs.getString("special_date", "").orEmpty())
                            val daysUntil = specialDate?.let { date ->
                                val today = java.time.LocalDate.now()
                                var next = date.withYear(today.year)
                                if (next.isBefore(today)) next = next.plusYears(1)
                                java.time.temporal.ChronoUnit.DAYS.between(today, next)
                            }
                            if (featured.isNotEmpty() || (!specialLabel.isBlank() && specialDate != null)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text("In evidenza", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    featured.forEach { favorite ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clickable { expandedEntryIds = expandedEntryIds + favorite.id },
                                            shape = RoundedCornerShape(18.dp), color = frostedSurfaceColor(),
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
                                    if (!specialLabel.isBlank() && daysUntil != null) {
                                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f)) {
                                                    Text(specialLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                                    Text(if (daysUntil == 0L) "È oggi!" else "Mancano " + daysUntil + " giorni · " + displayHaliMeoDate(specialDate.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(filtered, key = { _, entry -> entry.id }) { index, entry ->
                        var itemVisible by remember(entry.id) { mutableStateOf(false) }
                        LaunchedEffect(entry.id) { delay((index * 42L).coerceAtMost(280L)); itemVisible = true }
                        val itemAlpha by animateFloatAsState(targetValue = if (itemVisible) 1f else 0f, animationSpec = tween(260), label = "memoryEntryFade")
                        val itemOffset by animateFloatAsState(targetValue = if (itemVisible) 0f else 22f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f), label = "memoryEntryRise")
                        val cardInteraction = remember(entry.id) { MutableInteractionSource() }
                        val cardPressed by cardInteraction.collectIsPressedAsState()
                        val cardScale by animateFloatAsState(
                            targetValue = if (cardPressed) 0.985f else 1f,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                            label = "memoryCardPress"
                        )
                        val expanded = entry.id in expandedEntryIds
                        val accent = categoryAccent(entry.category)
                        val photoContext = LocalContext.current
                        val entryPhoto = remember(entry.photoUri) {
                            entry.photoUri?.let { value -> runCatching { photoContext.contentResolver.openInputStream(Uri.parse(value))?.use { BitmapFactory.decodeStream(it) } }.getOrNull() }
                        }
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                                .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = itemAlpha; translationY = itemOffset }
                                .clickable(
                                    interactionSource = cardInteraction,
                                    indication = null,
                                    onClick = {
                                        feedbackView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        expandedEntryIds = if (entry.id in expandedEntryIds) expandedEntryIds - entry.id else expandedEntryIds + entry.id
                                    }
                                )
                                .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
                                .border(1.dp, FrostedEdge, MaterialTheme.shapes.large)
                                .animateContentSize(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = frostedSurfaceColor())
                        ) {
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
                                    val heartInteraction = remember(entry.id) { MutableInteractionSource() }
                                    val heartPressed by heartInteraction.collectIsPressedAsState()
                                    val heartScale by animateFloatAsState(targetValue = if (heartPressed) 1.32f else if (isFavorite) 1.08f else 1f, animationSpec = spring(dampingRatio = 0.38f, stiffness = 650f), label = "favoriteHeartPop")
                                    IconButton(onClick = { feedbackView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onToggleFavorite(entry) }, interactionSource = heartInteraction) {
                                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti", tint = if (isFavorite) accent else Muted, modifier = Modifier.graphicsLayer { scaleX = heartScale; scaleY = heartScale })
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
                                    visible = expanded && entryPhoto != null,
                                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(240)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(animationSpec = tween(180))
                                ) {
                                    if (entryPhoto != null) {
                                        Image(
                                            bitmap = entryPhoto!!.asImageBitmap(),
                                            contentDescription = "Foto del ricordo: " + entry.title,
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(18.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
                                        HorizontalDivider(color = Muted.copy(alpha = 0.16f))
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
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF879B), modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Elimina", color = Color(0xFFFF879B))
                                            }
                                        }
                                    }
                                }
                                if (!expanded && (entry.body.length > 140 || entryPhoto != null)) {
                                    Text(
                                        if (entryPhoto != null) "Apri ricordo e foto" else "Leggi tutto",
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

@Composable private fun EditScreen(existing: DiaryEntry?, onSave: (String,String,String,String,String?)->Unit, onCancel:()->Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var cat by remember(existing?.id) { mutableStateOf(existing?.category ?: "Ricordo") }
    var date by remember(existing?.id) { mutableStateOf(existing?.memoryDate?.let { displayHaliMeoDate(it) } ?: displayHaliMeoDate(java.time.LocalDate.now().toString())) }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photoUri = uri.toString()
        }
    }
    val photoBitmap = remember(photoUri) {
        photoUri?.takeIf { it.isNotBlank() }?.let { value -> runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(if (existing == null) "NUOVO RICORDO" else "MODIFICA RICORDO", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("I momenti importanti, custoditi in un posto vostro.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp)).border(1.dp, FrostedEdge, RoundedCornerShape(26.dp)),
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
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.045f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedEdge)
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
                        Text(if (photoUri == null) "Scegli foto" else "Cambia foto", maxLines = 1)
                    }
                    if (photoUri != null) {
                        OutlinedButton(onClick = { photoUri = "" }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Rimuovi")
                        }
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
private fun TripsScreen(onOpenMonaco: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Transparent).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("I VOSTRI VIAGGI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
        Text("Un archivio di avventure", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Apri un viaggio per entrare nella sua pagina dedicata.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        val tripInteraction = remember { MutableInteractionSource() }
        val tripPressed by tripInteraction.collectIsPressedAsState()
        val tripScale by animateFloatAsState(
            targetValue = if (tripPressed) 0.975f else 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
            label = "tripCardPress"
        )
        Surface(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { scaleX = tripScale; scaleY = tripScale }
                .clickable(interactionSource = tripInteraction, indication = null, onClick = onOpenMonaco),
            shape = RoundedCornerShape(26.dp),
            color = frostedSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedEdge),
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
    AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->WebView(ctx).apply{
        settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=true;settings.allowContentAccess=true
        webChromeClient=WebChromeClient()
        webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{val u=request.url;return if(u.scheme=="https"||u.scheme=="http"){ctx.startActivity(Intent(Intent.ACTION_VIEW,u));true}else false}}
        loadUrl("file:///android_asset/missione-monaco.html")
    }})
}
