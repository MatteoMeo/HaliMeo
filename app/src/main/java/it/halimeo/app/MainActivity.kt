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
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private val Ink=Color(0xFF000000); private val Panel=Color(0xFF111116); private val Mint=Color(0xFFC4B5FD); private val Muted=Color(0xFFA6A1B2)
private val Elevated=Color(0xFF1A1A22); private val TextPrimary=Color(0xFFF5F2FA); private val AccentWarm=Color(0xFFFFB4A2)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Android 12+ supplies Material You / Monet colors derived from the device wallpaper.
    // Keep the app dark with a true-black canvas for AMOLED displays.
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color(0xFF0B0B0D),
            surfaceVariant = Color(0xFF17171C),
            onBackground = Color(0xFFF4F0F8),
            onSurface = Color(0xFFF4F0F8)
        )
    } else {
        darkColorScheme(
            primary=Mint, onPrimary=Ink, secondary=AccentWarm, onSecondary=Ink,
            background=Ink, onBackground=TextPrimary, surface=Panel, onSurface=TextPrimary,
            surfaceVariant=Elevated, onSurfaceVariant=Muted, outline=Color(0xFF34323D)
        )
    }
    // Material 3 Expressive supplies the expressive motion system and updated component defaults.
    MaterialExpressiveTheme(
        colorScheme = scheme,
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
        setContent { HaliMeoTheme { HaliMeoRoot() } }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun HaliMeoRoot() {
    val ctx= LocalContext.current; val activity=ctx as ComponentActivity
    val session=remember{SessionStore(ctx)}; val repo=remember{DiaryRepository(ctx)}
    var signed by remember{mutableStateOf<Boolean?>(null)}; var displayName by remember{mutableStateOf("")}
    LaunchedEffect(Unit){signed=!session.token().isNullOrBlank();displayName=session.name()}
    if(signed==null){Box(Modifier.fillMaxSize().background(Ink),contentAlignment=Alignment.Center){LoadingIndicator(modifier=Modifier.size(64.dp), color=MaterialTheme.colorScheme.primary)};return}
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
    Scaffold(containerColor=Ink,topBar={TopAppBar(title={Column(verticalArrangement=Arrangement.spacedBy(1.dp)){Text("HaliMeo",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,letterSpacing=(-0.5).sp);Text("IL VOSTRO SPAZIO PRIVATO",style=MaterialTheme.typography.labelSmall,color=Muted,letterSpacing=1.2.sp)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink,titleContentColor=TextPrimary,actionIconContentColor=Muted),actions={
        IconButton(onClick={activity.lifecycleScope.launch{runCatching{repo.syncNow()}.onSuccess{Toast.makeText(ctx,"Sincronizzazione completata",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(ctx,"Offline: dati conservati sul dispositivo",Toast.LENGTH_LONG).show()}}}){Icon(Icons.Default.CloudSync,"Sincronizza")}
        IconButton(onClick={activity.lifecycleScope.launch{session.clear();signed=false}}){Icon(Icons.Default.Logout,"Esci")}
    })},bottomBar={NavigationBar(containerColor=Color(0xFF08080B),contentColor=Muted,tonalElevation=0.dp){val navColors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=0.13f),unselectedIconColor=Muted,unselectedTextColor=Muted);NavigationBarItem(selected=currentRoute=="diary",onClick={nav.navigate("diary"){launchSingleTop=true;popUpTo("diary"){saveState=true}}},icon={Icon(Icons.Default.Book,null)},label={Text("Diario")},colors=navColors);NavigationBarItem(selected=currentRoute=="mission",onClick={nav.navigate("mission"){launchSingleTop=true;restoreState=true}},icon={Icon(Icons.Default.Flight,null)},label={Text("Monaco")},colors=navColors);NavigationBarItem(selected=currentRoute=="settings",onClick={nav.navigate("settings"){launchSingleTop=true;restoreState=true}},icon={Icon(Icons.Default.Settings,null)},label={Text("Impostazioni")},colors=navColors)}},floatingActionButton={if(nav.currentDestination?.route!="mission")LargeExtendedFloatingActionButton(onClick={editing=null;nav.navigate("edit")},text={Text("Nuovo ricordo",fontWeight=FontWeight.SemiBold)},icon={Icon(Icons.Default.Add,contentDescription=null)},containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,elevation=FloatingActionButtonDefaults.elevation(defaultElevation=4.dp,pressedElevation=8.dp))}){padding->
        NavHost(nav,startDestination="diary",modifier=Modifier.padding(padding),enterTransition={fadeIn(tween(220))+slideInHorizontally(tween(260)){it/14}},exitTransition={fadeOut(tween(150))+slideOutHorizontally(tween(180)){-it/18}},popEnterTransition={fadeIn(tween(220))+slideInHorizontally(tween(260)){ -it/14}},popExitTransition={fadeOut(tween(150))+slideOutHorizontally(tween(180)){it/18}}){
            composable("diary"){DiaryScreen(entries,favoriteIds,{editing=it;nav.navigate("edit")},{toggleFavorite(it.id)},{entry->activity.lifecycleScope.launch{repo.delete(entry.id)}})}
            composable("edit"){EditScreen(editing,{t,b,c,d->activity.lifecycleScope.launch{repo.save(t,b,c,d,editing?.id);editing=null;nav.popBackStack();runCatching{repo.syncNow()}}},{editing=null;nav.popBackStack()})}
            composable("mission"){MissionScreen()}
            composable("settings"){SettingsScreen(displayName=displayName,session=session,repo=repo,activity=activity,ctx=ctx)}
        }
    }
}
@Composable
private fun SettingsScreen(
    displayName: String,
    session: SessionStore,
    repo: DiaryRepository,
    activity: ComponentActivity,
    ctx: android.content.Context
) {
    val diaryEntries by repo.entries.collectAsState(initial = emptyList())
    val pendingSyncCount = diaryEntries.count { it.syncState != "SYNCED" }
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

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("IMPOSTAZIONI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
        Text("Account: $displayName")
        Text("I ricordi vengono salvati localmente prima della sincronizzazione.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (pendingSyncCount == 0) "Tutto aggiornato" else "$pendingSyncCount ricordi in attesa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (pendingSyncCount == 0) "Non ci sono modifiche da inviare." else "I dati restano sul dispositivo finché la sincronizzazione non riesce.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        Button(onClick = {
            activity.lifecycleScope.launch {
                runCatching { repo.syncNow() }
                    .onSuccess { Toast.makeText(ctx, "Sincronizzazione completata", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(ctx, "Server non raggiungibile: dati conservati sul dispositivo", Toast.LENGTH_LONG).show() }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Sincronizza ora") }

        OutlinedButton(
            onClick = {
                activity.lifecycleScope.launch {
                    runCatching {
                        val rows = repo.allForBackup().filter { it.deletedAt == null }
                        fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                        val export = buildString {
                            appendLine("Titolo,Data,Categoria,Ricordo")
                            rows.forEach { row ->
                                appendLine(listOf(row.title,row.memoryDate,row.category,row.body).joinToString(",") { csv(it) })
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
            Text("Esporta backup dei ricordi")
        }

        HorizontalDivider(color = Muted.copy(alpha = 0.25f))
        Text("INVITI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        Text("Crea un codice monouso valido 7 giorni e condividilo direttamente dal telefono.", color = Muted)

        when (isOwner) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Verifica dei permessi…", color = Muted)
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
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("IL TUO CODICE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(inviteCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (inviteMessage.isNotBlank() && isOwner != false) {
            Text(inviteMessage, color = if (inviteCode.isNotBlank()) MaterialTheme.colorScheme.primary else Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("Versione ${BuildConfig.VERSION_NAME}", color = Muted)
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
    Column(Modifier.fillMaxSize().background(Ink).padding(24.dp),verticalArrangement=Arrangement.Center){Text("HaliMeo",style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black);Text("Un posto solo vostro.",color=Muted);Spacer(Modifier.height(16.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Panel), modifier=Modifier.fillMaxWidth()) {
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
@Composable
private fun DiaryScreen(
    entries: List<DiaryEntry>,
    favoriteIds: Set<String>,
    onEdit: (DiaryEntry) -> Unit,
    onToggleFavorite: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tutte") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    val categories = listOf("Tutte") + entries.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val filtered = entries.filter {
        (it.title.contains(query, true) || it.body.contains(query, true) || it.category.contains(query, true)) &&
            (selectedCategory == "Tutte" || it.category.equals(selectedCategory, ignoreCase = true)) &&
            (!favoritesOnly || it.id in favoriteIds)
    }.let { matches -> if (newestFirst) matches.sortedByDescending { it.memoryDate } else matches.sortedBy { it.memoryDate } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("IL VOSTRO DIARIO", style=MaterialTheme.typography.labelSmall, color=Muted, letterSpacing=1.8.sp, fontWeight=FontWeight.SemiBold)
                Text(
                    "La vostra storia",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Piccoli momenti, tutti in un posto solo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    entries.size.toString(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
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
                unfocusedBorderColor = Muted.copy(alpha = 0.35f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected=favoritesOnly,onClick={favoritesOnly=!favoritesOnly},label={Text("Preferiti")},leadingIcon={Icon(if(favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,contentDescription=null,modifier=Modifier.size(16.dp))})
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    leadingIcon = if (selectedCategory == category) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null
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
                        colors = CardDefaults.cardColors(containerColor = Panel)
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
                                color = Color.White
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
                        Text("Nessun ricordo trovato", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("Prova con un'altra parola.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = Panel)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                            Text(entry.memoryDate, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                            Text("·", color = Muted)
                                            Text(entry.category, color = Muted, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    IconButton(onClick={onToggleFavorite(entry)}) {
                                        Icon(if(entry.id in favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,contentDescription=if(entry.id in favoriteIds) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",tint=if(entry.id in favoriteIds) MaterialTheme.colorScheme.primary else Muted)
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
                                Text(
                                    entry.body,
                                    color = Color(0xFFDAD7E2),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
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

@Composable private fun EditScreen(existing:DiaryEntry?,onSave:(String,String,String,String)->Unit,onCancel:()->Unit){
    var title by remember(existing?.id){mutableStateOf(existing?.title.orEmpty())};var body by remember(existing?.id){mutableStateOf(existing?.body.orEmpty())};var cat by remember(existing?.id){mutableStateOf(existing?.category?:"Ricordo")};var date by remember(existing?.id){mutableStateOf(existing?.memoryDate?:java.time.LocalDate.now().toString())}
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(existing==null)"NUOVO RICORDO" else "MODIFICA RICORDO",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineSmall);OutlinedTextField(title,{title=it},label={Text("Titolo")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it},label={Text("Data AAAA-MM-GG")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cat,{cat=it},label={Text("Categoria")},modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it},label={Text("Ricordo")},modifier=Modifier.fillMaxWidth().weight(1f),minLines=5);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Annulla")};Button(onClick={if(title.isNotBlank()&&body.isNotBlank())onSave(title,body,cat,date)},modifier=Modifier.weight(1f)){Text("Salva")}}}
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
