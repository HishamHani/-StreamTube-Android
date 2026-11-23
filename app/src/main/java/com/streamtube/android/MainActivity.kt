package com.streamtube.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val AppBlack = Color(0xFF101114)
private val AppSurface = Color(0xFF1A1C20)
private val AppRed = Color(0xFFFF0033)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = AppRed, background = AppBlack, surface = AppSurface,
                onBackground = Color.White, onSurface = Color.White
            )) { StreamTubeApp() }
        }
    }
}

private enum class Tab(val label: String) {
    Home("Home"), Search("Search"), Subscriptions("Subscriptions"),
    Downloads("Downloads"), Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamTubeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Home) }
    var query by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Video?>(null) }
    var signedIn by remember { mutableStateOf(YouTubeAuth.isSignedIn(context)) }
    var login by remember { mutableStateOf<DeviceLogin?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }

    fun searchNow() {
        val q = query.trim()
        if (q.isEmpty()) return
        tab = Tab.Search
        loading = true
        error = null
        scope.launch {
            try {
                videos = YouTubeApi.search(q)
                if (videos.isEmpty()) error = "No videos found. Try another search."
            } catch (t: Throwable) {
                error = t.message ?: "Search failed. Check your connection."
            } finally { loading = false }
        }
    }

    Scaffold(
        containerColor = AppBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(AppRed, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text("▶", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text("  Stream Tube", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { tab = Tab.Search }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlack)
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar(containerColor = AppSurface) {
                Tab.entries.forEach { item ->
                    val icon = when (item) {
                        Tab.Home -> Icons.Default.Home
                        Tab.Search -> Icons.Default.Search
                        Tab.Subscriptions -> Icons.Default.Subscriptions
                        Tab.Downloads -> Icons.Default.Download
                        Tab.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        if (selected != null) {
            VideoPlayer(selected!!, Modifier.padding(padding))
        } else when (tab) {
            Tab.Home -> HomeScreen(
                modifier = Modifier.padding(padding),
                query = query, onQuery = { query = it }, onSearch = ::searchNow,
                recent = videos.take(8), onVideo = { selected = it }
            )
            Tab.Search -> SearchScreen(
                modifier = Modifier.padding(padding), query = query, onQuery = { query = it },
                onSearch = ::searchNow, videos = videos, loading = loading, error = error,
                onVideo = { selected = it }
            )
            Tab.Subscriptions -> InfoScreen(Modifier.padding(padding), "Subscriptions",
                if (signedIn) "Account connected. Your subscription feed integration is the next step."
                else "Sign in from Settings to connect your YouTube account.")
            Tab.Downloads -> InfoScreen(Modifier.padding(padding), "Downloads",
                "The download manager is not connected yet. It will be added after search and playback are stable.")
            Tab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding), signedIn = signedIn,
                onSignIn = {
                    loginMessage = null
                    loginBusy = true
                    scope.launch {
                        try { login = YouTubeAuth.begin() }
                        catch (t: Throwable) { loginMessage = t.message ?: "Could not start sign-in" }
                        finally { loginBusy = false }
                    }
                },
                onSignOut = { YouTubeAuth.signOut(context); signedIn = false },
                loginBusy = loginBusy
            )
        }
    }

    if (login != null) {
        val device = login!!
        AlertDialog(
            onDismissRequest = { login = null },
            title = { Text("Sign in to YouTube") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Open the Google device page and enter this code:")
                    Text(device.userCode, style = MaterialTheme.typography.headlineMedium,
                        color = AppRed, fontWeight = FontWeight.Bold)
                    Text("2. Return here. The app will check for approval.")
                    Text("Only approve this code if you started this sign-in.")
                    loginMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    YouTubeAuth.openVerification(context, device)
                    loginBusy = true
                    scope.launch {
                        try {
                            loginMessage = YouTubeAuth.approve(context, device)
                            signedIn = YouTubeAuth.isSignedIn(context)
                            login = null
                        } catch (t: Throwable) {
                            loginMessage = t.message ?: "Sign-in failed"
                        } finally { loginBusy = false }
                    }
                }, enabled = !loginBusy) { Text(if (loginBusy) "Waiting…" else "Open Google & approve") }
            },
            dismissButton = { TextButton(onClick = { login = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    recent: List<Video>, onVideo: (Video) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Watch what you love", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Search YouTube videos without signing in.", color = Color.LightGray)
            SearchBox(query, onQuery, onSearch)
        }
        if (recent.isNotEmpty()) {
            Text("Recent results", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent) { VideoRow(it, onVideo) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search for a video to get started", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    videos: List<Video>, loading: Boolean, error: String?, onVideo: (Video) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        SearchBox(query, onQuery, onSearch, Modifier.padding(16.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppRed)
            }
            error != null -> Text(error, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error)
            videos.isEmpty() -> Text("Enter a search term to find videos.", Modifier.padding(18.dp), color = Color.Gray)
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos) { VideoRow(it, onVideo) }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f),
            singleLine = true, placeholder = { Text("Search videos") })
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = AppRed)) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
    }
}

@Composable
private fun VideoRow(video: Video, onClick: (Video) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick(video) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = video.thumbnail, contentDescription = video.title,
            modifier = Modifier.width(144.dp).height(84.dp).background(AppSurface, RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (video.duration.isNotBlank()) Text(video.duration, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoPlayer(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    val html = """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                            <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
                            iframe{border:0;width:100%;height:100%}</style>
                          </head>
                          <body>
                            <iframe
                              src="https://www.youtube.com/embed/${video.id}?playsinline=1&autoplay=0&controls=1&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
                              title="YouTube video player"
                              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                              referrerpolicy="strict-origin-when-cross-origin"
                              allowfullscreen>
                            </iframe>
                          </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
                }
            },
            update = { }
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(video.channel, color = Color.LightGray)
            Text("If YouTube blocks this embed, use the YouTube app/browser to watch this video.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(video.watchUrl))
                context.startActivity(intent)
            }) { Text("Watch in YouTube") }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, loginBusy: Boolean
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = AppSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouTube account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (signedIn) "Connected" else "Not signed in", color = if (signedIn) Color(0xFF75D69C) else Color.LightGray)
                if (signedIn) OutlinedButton(onClick = onSignOut) { Text("Sign out") }
                else Button(onClick = onSignIn, enabled = !loginBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AppRed)) {
                    Text(if (loginBusy) "Starting…" else "Sign in with Google")
                }
                Text("Sign-in uses Google's device approval page. Never share your verification code with anyone.",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("More player, privacy and SponsorBlock settings will be added in later builds.", color = Color.Gray)
    }
}

@Composable
private fun InfoScreen(modifier: Modifier, title: String, detail: String) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(detail, color = Color.LightGray)
    }
}
