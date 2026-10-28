package it.halimeo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import it.halimeo.app.data.*
import it.halimeo.app.network.*
import it.halimeo.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val Ink=Color(0xFF071014); private val Panel=Color(0xFF102126); private val Mint=Color(0xFF65FFE8); private val Muted=Color(0xFF9CB3B5)
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        SyncScheduler.schedule(this)
        setContent { MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Ink,background=Ink,onBackground=Color(0xFFEAF6F4),surface=Panel,onSurface=Color(0xFFEAF6F4))) { HaliMeoRoot() } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HaliMeoRoot() {
    val ctx= LocalContext.current; val activity=ctx as ComponentActivity
    val session=remember{SessionStore(ctx)}; val repo=remember{DiaryRepository(ctx)}
    var signed by remember{mutableStateOf<Boolean?>(null)}; var displayName by remember{mutableStateOf("")}
    LaunchedEffect(Unit){signed=!session.token().isNullOrBlank();displayName=session.name()}
    if(signed==null){Box(Modifier.fillMaxSize().background(Ink),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    if(signed==false){AuthScreen(
        onLogin={e,p,fail->activity.lifecycleScope.launch{runCatching{ApiClient.api.login(LoginRequest(e,p))}.onSuccess{session.save(it.accessToken,it.userId,it.displayName);displayName=it.displayName;signed=true}.onFailure{fail(it.message?: "Accesso non riuscito")}}},
        onRedeem={code,n,e,p,fail->activity.lifecycleScope.launch{runCatching{ApiClient.api.redeem(RedeemInviteRequest(code,n,e,p))}.onSuccess{session.save(it.accessToken,it.userId,it.displayName);displayName=it.displayName;signed=true}.onFailure{fail(it.message?: "Invito non valido")}}}
    );return}
    val entries by repo.entries.collectAsState(initial= emptyList()); var editing by remember{mutableStateOf<DiaryEntry?>(null)}
    val nav= rememberNavController()
    Scaffold(containerColor=Ink,topBar={TopAppBar(title={Column{Text("HaliMeo",fontWeight=FontWeight.Black);Text("IL VOSTRO SPAZIO PRIVATO",style=MaterialTheme.typography.labelSmall,color=Muted)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink,titleContentColor=Mint),actions={
        IconButton(onClick={activity.lifecycleScope.launch{runCatching{repo.syncNow()}.onSuccess{Toast.makeText(ctx,"Sincronizzazione completata",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(ctx,"Offline: dati conservati sul dispositivo",Toast.LENGTH_LONG).show()}}}){Icon(Icons.Default.CloudSync,"Sincronizza")}
        IconButton(onClick={activity.lifecycleScope.launch{session.clear();signed=false}}){Icon(Icons.Default.Logout,"Esci")}
    })},bottomBar={NavigationBar(containerColor=Panel){NavigationBarItem(nav.currentDestination?.route=="diary",{nav.navigate("diary")},{Icon(Icons.Default.Book,null)},label={Text("Diario")});NavigationBarItem(nav.currentDestination?.route=="mission",{nav.navigate("mission")},{Icon(Icons.Default.Flight,null)},label={Text("Monaco")});NavigationBarItem(nav.currentDestination?.route=="settings",{nav.navigate("settings")},{Icon(Icons.Default.Settings,null)},label={Text("Impostazioni")})}},floatingActionButton={if(nav.currentDestination?.route!="mission")FloatingActionButton(onClick={editing=null;nav.navigate("edit")},containerColor=Mint,contentColor=Ink){Icon(Icons.Default.Add,"Nuovo ricordo")}}){padding->
        NavHost(nav,startDestination="diary",modifier=Modifier.padding(padding)){
            composable("diary"){DiaryScreen(entries,{editing=it;nav.navigate("edit")},{entry->activity.lifecycleScope.launch{repo.delete(entry.id)}})}
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
        Text("IMPOSTAZIONI", color = Mint, style = MaterialTheme.typography.headlineSmall)
        Text("Account: $displayName")
        Text("I ricordi vengono salvati localmente prima della sincronizzazione.", color = Muted)

        Button(onClick = {
            activity.lifecycleScope.launch {
                runCatching { repo.syncNow() }
                    .onSuccess { Toast.makeText(ctx, "Sincronizzazione completata", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(ctx, "Server non raggiungibile: dati conservati sul dispositivo", Toast.LENGTH_LONG).show() }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Sincronizza ora") }

        HorizontalDivider(color = Muted.copy(alpha = 0.25f))
        Text("INVITI", color = Mint, style = MaterialTheme.typography.titleLarge)
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
                            Text("IL TUO CODICE", color = Mint, style = MaterialTheme.typography.labelLarge)
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
            Text(inviteMessage, color = if (inviteCode.isNotBlank()) Mint else Muted, style = MaterialTheme.typography.bodySmall)
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
    Column(Modifier.fillMaxSize().background(Ink).padding(24.dp),verticalArrangement=Arrangement.Center){Text("HaliMeo",style=MaterialTheme.typography.displayMedium,color=Mint,fontWeight=FontWeight.Black);Text("Un posto solo vostro.",color=Muted);Spacer(Modifier.height(16.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Panel), modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val dotColor = when (serverOnline) { true -> Color(0xFF65E6A5); false -> Color(0xFFFF879B); null -> Color(0xFFFFD479) }
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
@Composable private fun DiaryScreen(entries:List<DiaryEntry>,onEdit:(DiaryEntry)->Unit,onDelete:(DiaryEntry)->Unit){
    var query by remember{mutableStateOf("")};val filtered=entries.filter{it.title.contains(query,true)||it.body.contains(query,true)||it.category.contains(query,true)}
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal=16.dp)){Spacer(Modifier.height(12.dp));Text("I VOSTRI RICORDI",style=MaterialTheme.typography.headlineSmall,color=Mint,fontWeight=FontWeight.Bold);Text("Salvati prima sul dispositivo.",color=Muted,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(10.dp));OutlinedTextField(query,{query=it},label={Text("Cerca nei ricordi")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(filtered.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Ancora nessun ricordo. Il primo lo scrivete voi.",color=Muted)}
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(top=12.dp,bottom=90.dp)){items(filtered,key={it.id}){e->Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(e.title,style=MaterialTheme.typography.titleMedium,color=Color.White,fontWeight=FontWeight.Bold);Text("${e.memoryDate} · ${e.category}",color=Mint,style=MaterialTheme.typography.labelSmall);Spacer(Modifier.height(8.dp));Text(e.body);Row{TextButton(onClick={onEdit(e)}){Text("Modifica")};TextButton(onClick={onDelete(e)}){Icon(Icons.Default.Delete,null);Text("Elimina")};if(e.syncState!="SYNCED")Text("In attesa",color=Color(0xFFFFD479),style=MaterialTheme.typography.labelSmall)}}}}}
    }
}
@Composable private fun EditScreen(existing:DiaryEntry?,onSave:(String,String,String,String)->Unit,onCancel:()->Unit){
    var title by remember(existing?.id){mutableStateOf(existing?.title.orEmpty())};var body by remember(existing?.id){mutableStateOf(existing?.body.orEmpty())};var cat by remember(existing?.id){mutableStateOf(existing?.category?:"Ricordo")};var date by remember(existing?.id){mutableStateOf(existing?.memoryDate?:java.time.LocalDate.now().toString())}
    Column(Modifier.fillMaxSize().background(Ink).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(existing==null)"NUOVO RICORDO" else "MODIFICA RICORDO",color=Mint,style=MaterialTheme.typography.headlineSmall);OutlinedTextField(title,{title=it},label={Text("Titolo")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it},label={Text("Data AAAA-MM-GG")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cat,{cat=it},label={Text("Categoria")},modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it},label={Text("Ricordo")},modifier=Modifier.fillMaxWidth().weight(1f),minLines=5);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Annulla")};Button(onClick={if(title.isNotBlank()&&body.isNotBlank())onSave(title,body,cat,date)},modifier=Modifier.weight(1f)){Text("Salva")}}}
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
