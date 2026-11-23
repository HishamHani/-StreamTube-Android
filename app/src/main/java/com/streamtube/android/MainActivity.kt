package com.streamtube.android

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.filled.Delete
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

private val AppBlack = Color(0xFF081525)
private val AppSurface = Color(0xFF10243A)
private val AppSurfaceRaised = Color(0xFF172D46)
private val AppGold = Color(0xFFE8BD68)
private val AppGoldSoft = Color(0xFFF4D99A)
private val AppRed = AppGold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = AppGold, secondary = AppGoldSoft, background = AppBlack, surface = AppSurface,
                onPrimary = Color(0xFF172235), onSecondary = Color(0xFF172235),
                onBackground = Color(0xFFF5F1E8), onSurface = Color(0xFFF5F1E8),
                surfaceVariant = AppSurfaceRaised, onSurfaceVariant = Color(0xFFB8C4D2),
                outline = Color(0xFF52657A), outlineVariant = Color(0xFF344A61)
            )) { StreamTubeApp() }
        }
    }
}

private enum class Tab(val label: String) {
    Home("Home"), Search("Search"), Subscriptions("Subscriptions"),
    Library("Library"), Downloads("Downloads"), Settings("Settings")
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
    var accountName by remember { mutableStateOf(YouTubeAuth.account(context).name) }
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
                        Box(Modifier.size(32.dp).background(AppGold, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF10243A), modifier = Modifier.size(24.dp))
                        }
                        Text("  Stream Tube", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { selected = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { tab = Tab.Search }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlack, titleContentColor = Color(0xFFF5F1E8), navigationIconContentColor = AppGold, actionIconContentColor = AppGold)
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar(containerColor = AppSurface) {
                Tab.entries.forEach { item ->
                    val icon = when (item) {
                        Tab.Home -> Icons.Rounded.PlayCircle
                        Tab.Search -> Icons.Rounded.Search
                        Tab.Subscriptions -> Icons.Rounded.Subscriptions
                        Tab.Library -> Icons.Rounded.VideoLibrary
                        Tab.Downloads -> Icons.Rounded.Download
                        Tab.Settings -> Icons.Rounded.Settings
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppGold, selectedTextColor = AppGold, indicatorColor = AppGold.copy(alpha = 0.16f), unselectedIconColor = Color(0xFF91A4B8), unselectedTextColor = Color(0xFF91A4B8))
                    )
                }
            }
        }
    ) { padding ->
        if (selected != null) {
            VideoPlayer(selected!!, Modifier.padding(padding), onVideo = ::openVideo)
        } else when (tab) {
            Tab.Home -> HomeScreen(
                modifier = Modifier.padding(padding),
                query = query, onQuery = { query = it }, onSearch = ::searchNow,
                recent = LocalLibrary.history(context), watchLater = LocalLibrary.watchLater(context), signedIn = signedIn, onVideo = ::openVideo
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
            Tab.Subscriptions -> SubscriptionsScreen(Modifier.padding(padding), signedIn = signedIn, onVideo = ::openVideo)
            Tab.Library -> LibraryScreen(Modifier.padding(padding), onVideo = ::openVideo)
            Tab.Downloads -> InfoScreen(Modifier.padding(padding), "Downloads",
                "The download manager is not connected yet. It will be added after search and playback are stable.")
            Tab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding), signedIn = signedIn, accountName = accountName, preferences = preferences,
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
                onSignOut = { YouTubeAuth.signOut(context); signedIn = false; accountName = "YouTube account" },
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
                            accountName = YouTubeAuth.account(context).name
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
    recent: List<Video>, watchLater: List<Video>, signedIn: Boolean, onVideo: (Video) -> Unit
) {
    val context = LocalContext.current
    var accountFeed by remember { mutableStateOf<List<Video>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            try { accountFeed = YouTubeApi.signedInHome(context).videos }
            catch (t: Throwable) { feedError = t.message ?: "Could not load your YouTube Home" }
        } else {
            accountFeed = emptyList()
            feedError = null
        }
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (signedIn) "Your YouTube Home" else "Watch what you love", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (signedIn) "Recommendations from your YouTube account." else "Search YouTube videos without signing in.", color = Color.LightGray)
            SearchBox(query, onQuery, onSearch)
        }
        if (accountFeed.isNotEmpty() || recent.isNotEmpty() || watchLater.isNotEmpty()) {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (accountFeed.isNotEmpty()) {
                    item { Text("Recommended for you", fontWeight = FontWeight.Bold) }
                    items(accountFeed) { VideoRow(it, onVideo) }
                }
                if (recent.isNotEmpty()) {
                    item { Text("Continue watching", fontWeight = FontWeight.Bold) }
                    items(recent) { VideoRow(it, onVideo) }
                }
                if (watchLater.isNotEmpty()) {
                    item { Text("Watch Later", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold) }
                    items(watchLater) { VideoRow(it, onVideo) }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(feedError ?: "Search for a video to get started", color = Color.Gray)
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
        Button(onClick = onSearch, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGold, contentColor = Color(0xFF10243A))) {
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
private fun VideoPlayer(video: Video, modifier: Modifier = Modifier, onVideo: (Video) -> Unit) {
    val context = LocalContext.current
    var streamUrl by remember(video.id) { mutableStateOf<String?>(null) }
    var streamMimeType by remember(video.id) { mutableStateOf(MimeTypes.APPLICATION_M3U8) }
    var streamSource by remember(video.id) { mutableStateOf("") }
    var qualityMenu by remember { mutableStateOf(false) }
    var maxQuality by remember { mutableIntStateOf(AppPreferences.load(context).defaultQuality) }
    var savedLater by remember(video.id) { mutableStateOf(LocalLibrary.isWatchLater(context, video.id)) }
    var subscribed by remember(video.channelId) { mutableStateOf(video.channelId?.let { LocalLibrary.isSubscribed(context, it) } ?: false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var downloadDialog by remember(video.id) { mutableStateOf(false) }
    var downloadFormats by remember(video.id) { mutableStateOf<List<DownloadFormat>>(emptyList()) }
    var downloadLoading by remember(video.id) { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()
    var streamError by remember(video.id) { mutableStateOf<String?>(null) }
    var resolving by remember(video.id) { mutableStateOf(true) }
    var retryCount by remember(video.id) { mutableIntStateOf(0) }
    val preferences = remember { AppPreferences.load(context) }
    var sponsorSegments by remember(video.id) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var displayTitle by remember(video.id) { mutableStateOf(video.title) }
    var dislikeVotes by remember(video.id) { mutableStateOf<DislikeVotes?>(null) }
    val skippedSegments = remember(video.id) { mutableSetOf<String>() }
    var relatedVideos by remember(video.id) { mutableStateOf<List<Video>>(emptyList()) }
    var comments by remember(video.id) { mutableStateOf<List<VideoComment>>(emptyList()) }
    var commentsContinuation by remember(video.id) { mutableStateOf<String?>(null) }
    var detailsLoading by remember(video.id) { mutableStateOf(true) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var detailsError by remember(video.id) { mutableStateOf<String?>(null) }

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

    LaunchedEffect(video.id) {
        detailsLoading = true
        detailsError = null
        try {
            val page = YouTubeApi.videoNextPage(video.id)
            relatedVideos = page.related
            comments = page.comments
            commentsContinuation = page.commentContinuation
        } catch (t: Throwable) {
            detailsError = t.message ?: "Could not load comments and related videos"
        } finally { detailsLoading = false }
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                IconButton(
                    onClick = {
                        downloadLoading = true
                        actionScope.launch {
                            try {
                                downloadFormats = StreamResolver.downloadFormats(video.id)
                                downloadDialog = true
                                if (downloadFormats.isEmpty()) actionMessage = "No downloadable formats were returned by YouTube."
                            } catch (t: Throwable) {
                                actionMessage = t.message ?: "Could not load download formats"
                            } finally { downloadLoading = false }
                        }
                    },
                    enabled = !downloadLoading,
                    modifier = Modifier.size(48.dp).background(AppSurfaceRaised, CircleShape)
                ) {
                    if (downloadLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppGold)
                    else Icon(Icons.Rounded.Download, contentDescription = "Download", tint = AppGold, modifier = Modifier.size(25.dp))
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
            Divider(color = Color(0xFF344A61), modifier = Modifier.padding(vertical = 8.dp))
            Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (relatedVideos.isEmpty() && detailsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AppRed, strokeWidth = 2.dp)
            } else if (relatedVideos.isEmpty()) {
                Text("No related videos returned.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                relatedVideos.take(12).forEach { related -> VideoRow(related, onVideo) }
            }
            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
            Text("Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (detailsLoading && comments.isEmpty()) {
                Text("Loading comments…", color = Color.Gray)
            } else if (comments.isEmpty()) {
                Text(detailsError ?: "No comments were returned for this video.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                comments.forEach { comment ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(comment.author.ifBlank { "YouTube user" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(comment.published, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                        if (comment.likes.isNotBlank()) Text("👍 ${comment.likes}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (commentsContinuation != null) {
                OutlinedButton(onClick = {
                    val token = commentsContinuation ?: return@OutlinedButton
                    if (!commentsLoading) actionScope.launch {
                        commentsLoading = true
                        try {
                            val page = YouTubeApi.moreComments(token)
                            comments = comments + page.first.filterNot { item -> comments.any { it.id == item.id } }
                            commentsContinuation = page.second
                        } catch (t: Throwable) { detailsError = t.message ?: "Could not load more comments" }
                        finally { commentsLoading = false }
                    }
                }, enabled = !commentsLoading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (commentsLoading) "Loading…" else "More comments")
                }
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

        if (downloadDialog) {
            AlertDialog(
                onDismissRequest = { downloadDialog = false },
                title = { Text("Download for offline") },
                text = {
                    val videoOptions = downloadFormats.filter { !it.audioOnly && !it.videoOnly }
                    val audioOptions = downloadFormats.filter { it.audioOnly }
                    Column(
                        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Choose a complete video or an audio-only file. Saved in Downloads/StreamTube.", color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.Movie, contentDescription = null, tint = AppGold); Text("VIDEO", fontWeight = FontWeight.Bold, color = AppGold) }
                        if (videoOptions.isEmpty()) Text("No complete video formats available.", color = Color.Gray)
                        videoOptions.forEach { format ->
                            TextButton(onClick = {
                                try {
                                    OfflineDownloads.enqueue(context, video, format)
                                    actionMessage = "Video download queued: ${format.label}"
                                    downloadDialog = false
                                } catch (t: Throwable) { actionMessage = t.message ?: "Could not start download" }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(format.label, color = AppRed)
                                    Text(if (format.sizeBytes > 0L) formatBytes(format.sizeBytes) else "Size unavailable", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Divider(color = Color.DarkGray)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AppGold); Text("AUDIO", fontWeight = FontWeight.Bold, color = AppGold) }
                        if (audioOptions.isEmpty()) Text("YouTube did not return a directly downloadable audio track for this video.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        audioOptions.forEach { format ->
                            TextButton(onClick = {
                                try {
                                    OfflineDownloads.enqueue(context, video, format)
                                    actionMessage = "Audio download queued: ${format.label}"
                                    downloadDialog = false
                                } catch (t: Throwable) { actionMessage = t.message ?: "Could not start download" }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(format.label, color = AppRed)
                                    Text(if (format.sizeBytes > 0L) formatBytes(format.sizeBytes) else "Size unavailable", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("Audio keeps its real M4A or WebM file extension.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { downloadDialog = false }) { Text("Close") } }
            )
        }
}


private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "Size unavailable"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
}

@Composable
private fun SubscriptionsScreen(modifier: Modifier, signedIn: Boolean, onVideo: (Video) -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh, signedIn) {
        loading = true
        error = null
        try { videos = if (signedIn) YouTubeApi.signedInSubscriptions(context).videos else YouTubeApi.localSubscriptions(context) }
        catch (t: Throwable) { error = t.message ?: "Could not load subscriptions" }
        finally { loading = false }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Latest from your subscriptions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppRed) }
            error != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { refresh++ }) { Text("Try again") }
            }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (signedIn) "No subscription videos found." else "No local subscriptions yet. Open a video and tap Subscribe.", color = Color.Gray)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos) { VideoRow(it, onVideo) }
            }
        }
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier, onVideo: (Video) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val history = remember(refresh) { LocalLibrary.history(context, 100) }
    val watchLater = remember(refresh) { LocalLibrary.watchLater(context) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { LocalLibrary.clearHistory(context); LocalLibrary.clearWatchLater(context); refresh++ }) { Text("Clear all") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Watch Later (${watchLater.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (watchLater.isEmpty()) item { Text("Videos you save with Watch Later will appear here.", color = Color.Gray) }
            items(watchLater, key = { "later_" + it.id }) { video ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { VideoRow(video, onVideo) }
                    IconButton(onClick = { LocalLibrary.removeWatchLater(context, video.id); refresh++ }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove from Watch Later", tint = AppGold) }
                }
            }
            item { Text("Watch history (${history.size})", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (history.isEmpty()) item { Text("Videos you watch will appear here.", color = Color.Gray) }
            items(history, key = { "history_" + it.id }) { video ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { VideoRow(video, onVideo) }
                    IconButton(onClick = { LocalLibrary.removeHistory(context, video.id); refresh++ }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove from history", tint = AppGold) }
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen(
    modifier: Modifier, signedIn: Boolean, accountName: String, preferences: StreamTubePreferences,
    onPreference: (String, Boolean) -> Unit, onSignIn: () -> Unit, onSignOut: () -> Unit, loginBusy: Boolean
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouTube account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (signedIn) accountName else "Not signed in", color = if (signedIn) Color(0xFF75D69C) else Color.LightGray)
                if (signedIn) YouTubeAuth.account(LocalContext.current).handle?.let { Text(it, color = Color.Gray) }
                if (signedIn) OutlinedButton(onClick = onSignOut) { Text("Sign out") }
                else Button(onClick = onSignIn, enabled = !loginBusy,
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGold, contentColor = Color(0xFF10243A))) {
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
