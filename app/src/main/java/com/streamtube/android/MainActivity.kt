package com.streamtube.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val StreamBlack = Color(0xFF101114)
private val StreamSurface = Color(0xFF1A1C20)
private val StreamAccent = Color(0xFFFF0033)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = StreamAccent,
                background = StreamBlack,
                surface = StreamSurface,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                StreamTubeApp()
            }
        }
    }
}

private enum class AppTab(val label: String) {
    Home("Home"), Explore("Explore"), Subscriptions("Subscriptions"),
    Downloads("Downloads"), Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamTubeApp() {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = StreamBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(StreamAccent, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "  Stream Tube",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .size(25.dp)
                            .clickable { selectedTab = AppTab.Explore }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = StreamSurface) {
                AppTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        AppTab.Home -> Icons.Default.Home
                        AppTab.Explore -> Icons.Default.Explore
                        AppTab.Subscriptions -> Icons.Default.Subscriptions
                        AppTab.Downloads -> Icons.Default.Download
                        AppTab.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Home -> HomeScreen(Modifier.padding(innerPadding))
            AppTab.Explore -> SearchScreen(
                query = searchText,
                onQueryChange = { searchText = it },
                modifier = Modifier.padding(innerPadding)
            )
            AppTab.Subscriptions -> PlaceholderScreen(
                "Subscriptions", "Your channel subscriptions will appear here.",
                Modifier.padding(innerPadding)
            )
            AppTab.Downloads -> PlaceholderScreen(
                "Downloads", "Downloaded videos and audio will be available here.",
                Modifier.padding(innerPadding)
            )
            AppTab.Settings -> PlaceholderScreen(
                "Settings", "Player, appearance, privacy and SponsorBlock settings.",
                Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Your video space", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "A mobile-first Stream Tube experience is taking shape.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("For you", "Recently uploaded", "Music", "Gaming").forEach { chip ->
                    Surface(shape = CircleShape, color = StreamSurface) {
                        Text(chip, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                }
            }
        }
        item {
            FeatureCard(
                title = "Mobile-first interface",
                detail = "Designed for touch, portrait screens and one-handed navigation.",
                tag = "ANDROID"
            )
        }
        item {
            FeatureCard(
                title = "Video playback integration",
                detail = "The Android player is a key porting task. Playback is not connected yet.",
                tag = "IN DEVELOPMENT"
            )
        }
        item {
            FeatureCard(
                title = "Your subscriptions",
                detail = "Subscription feeds and local library migration are planned next.",
                tag = "COMING NEXT"
            )
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search videos and channels") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Text(
            if (query.isBlank()) "Search UI is ready. Connecting the video catalogue is the next step."
            else "Search entered: $query\nLive search results will be added with the YouTube data layer.",
            color = Color.LightGray
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(detail, color = Color.LightGray)
    }
}

@Composable
private fun FeatureCard(title: String, detail: String, tag: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StreamSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tag, color = StreamAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
