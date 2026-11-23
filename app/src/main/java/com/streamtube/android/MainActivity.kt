package com.streamtube.android

import android.annotation.SuppressLint
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
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
    var continuation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Video?>(null) }
    var signedIn by remember { mutableStateOf(YouTubeAuth.isSignedIn(context)) }
    var preferences by remember { mutableStateOf(AppPreferences.load(context)) }
    var login by remember { mutableStateOf<DeviceLogin?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }

    fun openVideo(video: Video) {
        selected = video
        if (preferences.rememberHistory) LocalLibrary.addHistory(context, video)
    }

    fun searchNow() {
        val q = query.trim()
        if (q.isEmpty()) return
        tab = Tab.Search
        loading = true
        error = null
        scope.launch {
            try {
                val page = YouTubeApi.searchPage(q)
                videos = page.videos
                continuation = page.continuation
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
                recent = LocalLibrary.history(context), onVideo = ::openVideo
            )
            Tab.Search -> SearchScreen(
                modifier = Modifier.padding(padding), query = query, onQuery = { query = it },
                onSearch = ::searchNow, videos = videos, loading = loading, error = error,
                continuation = continuation,
                onLoadMore = {
                    val token = continuation
                    if (token != null && !loading) scope.launch {
                        loading = true
                        try { val page = YouTubeApi.continueSearch(token); videos = videos + page.videos.filterNot { v -> videos.any { it.id == v.id } }; continuation = page.continuation }
                        catch (t: Throwable) { error = t.message ?: "Could not load more results" }
                        finally { loading = false }
                    }
                },
                onVideo = ::openVideo
            )
            Tab.Subscriptions -> if (signedIn) SubscriptionsScreen(Modifier.padding(padding), onVideo = { selected = it })
                else InfoScreen(Modifier.padding(padding), "Subscriptions", "Sign in from Settings to see your YouTube subscriptions.")
            Tab.Downloads -> InfoScreen(Modifier.padding(padding), "Downloads",
                "The download manager is not connected yet. It will be added after search and playback are stable.")
            Tab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding), signedIn = signedIn, preferences = preferences,
                onPreference = { key, value ->
                    AppPreferences.setBoolean(context, key, value)
                    preferences = when (key) {
                        "sponsorBlock" -> preferences.copy(sponsorBlock = value)
                        "deArrowTitles" -> preferences.copy(deArrowTitles = value)
                        "returnDislike" -> preferences.copy(returnDislike = value)
                        "autoplayNext" -> preferences.copy(autoplayNext = value)
                        "rememberHistory" -> preferences.copy(rememberHistory = value)
                        else -> preferences
                    }
                },
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
    videos: List<Video>, loading: Boolean, error: String?, continuation: String?,
    onLoadMore: () -> Unit, onVideo: (Video) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        SearchBox(query, onQuery, onSearch, Modifier.padding(16.dp))
        when {
            loading && videos.isEmpty() -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppRed)
            }
            error != null && videos.isEmpty() -> Text(error, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error)
            videos.isEmpty() -> Text("Enter a search term to find videos.", Modifier.padding(18.dp), color = Color.Gray)
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos) { VideoRow(it, onVideo) }
                if (continuation != null) item {
                    OutlinedButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Load more")
                    }
                }
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

@Composable
private fun VideoPlayer(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var streamUrl by remember(video.id) { mutableStateOf<String?>(null) }
    var streamMimeType by remember(video.id) { mutableStateOf(MimeTypes.APPLICATION_M3U8) }
    var streamSource by remember(video.id) { mutableStateOf("") }
    var qualityMenu by remember { mutableStateOf(false) }
    var maxQuality by remember { mutableIntStateOf(AppPreferences.load(context).defaultQuality) }
    var streamError by remember(video.id) { mutableStateOf<String?>(null) }
    var resolving by remember(video.id) { mutableStateOf(true) }
    var retryCount by remember(video.id) { mutableIntStateOf(0) }
    val preferences = remember { AppPreferences.load(context) }
    var sponsorSegments by remember(video.id) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var displayTitle by remember(video.id) { mutableStateOf(video.title) }
    var dislikeVotes by remember(video.id) { mutableStateOf<DislikeVotes?>(null) }
    val skippedSegments = remember(video.id) { mutableSetOf<String>() }

    val trackSelector = remember(video.id) {
        DefaultTrackSelector(context).apply { setParameters(buildUponParameters().setMaxVideoSize(Int.MAX_VALUE, maxQuality)) }
    }

    val player = remember(video.id) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    LaunchedEffect(video.id, retryCount) {
        resolving = true
        streamError = null
        streamUrl = null
        try {
            val stream = StreamResolver.resolve(video.id)
            streamUrl = stream.hlsUrl
            streamMimeType = stream.mimeType
            streamSource = stream.source
            if (preferences.sponsorBlock) launch { sponsorSegments = runCatching { YouTubeServices.sponsorSegments(video.id) }.getOrDefault(emptyList()) }
            if (preferences.deArrowTitles) launch { val branding = YouTubeServices.deArrow(video.id); if (!branding?.title.isNullOrBlank()) displayTitle = branding!!.title!! }
            if (preferences.returnDislike) launch { dislikeVotes = YouTubeServices.returnDislike(video.id) }
        } catch (t: Throwable) {
            streamError = t.message ?: "Unable to load this video"
        } finally {
            resolving = false
        }
    }

    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(streamMimeType).build())
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player, sponsorSegments) {
        val autoSkip = preferences.autoSkipCategories
        while (true) {
            val now = player.currentPosition / 1000.0
            val segment = sponsorSegments.firstOrNull { it.category in autoSkip && it.uuid !in skippedSegments && now >= it.start && now < it.end }
            if (segment != null) {
                skippedSegments += segment.uuid
                player.seekTo((segment.end * 1000).toLong())
                launch { YouTubeServices.reportSponsorViewed(segment.uuid) }
            }
            kotlinx.coroutines.delay(350)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { ctx -> PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            } },
            update = { it.player = player }
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(displayTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(video.channel, color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    if (savedLater) LocalLibrary.removeWatchLater(context, video.id)
                    else LocalLibrary.addWatchLater(context, video)
                    savedLater = !savedLater
                    actionMessage = if (savedLater) "Added to Watch Later" else "Removed from Watch Later"
                }) { Text(if (savedLater) "Saved" else "Watch Later") }
                if (!video.channelId.isNullOrBlank()) {
                    OutlinedButton(onClick = {
                        val channelId = video.channelId
                        actionScope.launch {
                            try {
                                if (YouTubeAuth.isSignedIn(context)) YouTubeApi.subscribeChannel(context, channelId, !subscribed)
                                if (subscribed) LocalLibrary.unsubscribe(context, channelId)
                                else LocalLibrary.subscribe(context, channelId, video.channel)
                                subscribed = !subscribed
                                actionMessage = if (subscribed) "Subscribed to channel" else "Unsubscribed"
                            } catch (t: Throwable) { actionMessage = t.message ?: "Subscription failed" }
                        }
                    }) { Text(if (subscribed) "Subscribed" else "Subscribe") }
                }
            }
            actionMessage?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quality", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Box {
                    TextButton(onClick = { qualityMenu = true }) { Text("${maxQuality}p ▾") }
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                        listOf(2160, 1440, 1080, 720, 480, 360).forEach { q ->
                            DropdownMenuItem(text = { Text("${q}p") }, onClick = {
                                maxQuality = q
                                trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(Int.MAX_VALUE, q))
                                qualityMenu = false
                            })
                        }
                    }
                }
                Text("Source: $streamSource", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            dislikeVotes?.let { votes ->
                Text("👍 ${votes.likes}    👎 ${votes.dislikes}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            if (resolving) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppRed, strokeWidth = 2.dp)
                    Text("Resolving YouTube stream…", color = Color.Gray)
                }
            }
            streamError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (streamError != null) {
                OutlinedButton(onClick = {
                    streamError = null
                    retryCount++
                }) {
                    Text("Retry")
                }
                OutlinedButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(video.watchUrl)))
                }) { Text("Open in YouTube") }
            }
        }
    }
}


@Composable
private fun SubscriptionsScreen(modifier: Modifier, onVideo: (Video) -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try { videos = YouTubeApi.signedInSubscriptions(context).videos }
        catch (t: Throwable) { error = t.message ?: "Could not load subscriptions" }
        finally { loading = false }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Latest from your subscriptions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppRed) }
            error != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { refresh++ }) { Text("Try again") }
            }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No subscription videos found.", color = Color.Gray)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos) { VideoRow(it, onVideo) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, signedIn: Boolean, preferences: StreamTubePreferences,
    onPreference: (String, Boolean) -> Unit, onSignIn: () -> Unit, onSignOut: () -> Unit, loginBusy: Boolean
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
        Card(colors = CardDefaults.cardColors(containerColor = AppSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Watching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PreferenceSwitch("SponsorBlock", "Skip sponsor reads and other marked segments", preferences.sponsorBlock) { onPreference("sponsorBlock", it) }
                PreferenceSwitch("DeArrow titles", "Use community-submitted alternative titles", preferences.deArrowTitles) { onPreference("deArrowTitles", it) }
                PreferenceSwitch("Return YouTube Dislike", "Show estimated likes and dislikes", preferences.returnDislike) { onPreference("returnDislike", it) }
                PreferenceSwitch("Autoplay next", "Continue to the next queued video", preferences.autoplayNext) { onPreference("autoplayNext", it) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = AppSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PreferenceSwitch("Watch history", "Remember videos watched in Stream Tube", preferences.rememberHistory) { onPreference("rememberHistory", it) }
            }
        }
        Text("More desktop settings are being ported: playback quality, captions, region, proxy, downloads and feed filters.", color = Color.Gray)
    }
}

@Composable
private fun PreferenceSwitch(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
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
