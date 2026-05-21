package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize language support
                var currentLocaleState by remember { mutableStateOf(Locale.getDefault().language) }
                
                // Wrap in a key-provider to force recomposition when language switches
                key(currentLocaleState) {
                    GalleryApp { lang ->
                        currentLocaleState = lang
                        // Update device locale dynamically inside runtime if possible
                        val locale = Locale(lang)
                        Locale.setDefault(locale)
                        val config = resources.configuration
                        config.setLocale(locale)
                        resources.updateConfiguration(config, resources.displayMetrics)
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryApp(
    viewModel: GalleryViewModel = viewModel(),
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Media States
    val mediaList by viewModel.mediaListState.collectAsState()
    val trashList by viewModel.trashListState.collectAsState()
    val activeMedia = viewModel.getActiveMediaList()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PureBlack,
        bottomBar = {
            if (!viewModel.isViewerOpen && !viewModel.isEditorOpen) {
                SamsungBottomNavigation(
                    selectedTab = viewModel.selectedTab,
                    onTabSelected = { 
                        viewModel.selectedTab = it
                        // Reset filters if switching tabs
                        if (it == "images") viewModel.activeFilterPill = "Toate"
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PureBlack)
        ) {
            // Main views based on tab choice
            when (viewModel.selectedTab) {
                "images" -> {
                    GalleryImagesScreen(
                        viewModel = viewModel,
                        activeMedia = activeMedia,
                        onItemClick = { index ->
                            viewModel.viewerActiveIndex = index
                            viewModel.isViewerOpen = true
                        }
                    )
                }
                "albums" -> {
                    GalleryAlbumsScreen(
                        viewModel = viewModel,
                        mediaList = mediaList,
                        onAlbumClick = { category ->
                            viewModel.selectedTab = "images"
                            viewModel.activeFilterPill = "Toate"
                            viewModel.searchQuery = category
                        }
                    )
                }
                "suggestions" -> {
                    GallerySuggestionsScreen(
                        viewModel = viewModel
                    )
                }
                "menu" -> {
                    GalleryMenuScreen(
                        viewModel = viewModel,
                        trashCount = trashList.size,
                        onLanguageSelected = { lang ->
                            onLanguageChange(lang)
                            Toast.makeText(context, if (lang == "ro") "Limba schimbată în Română" else "Language changed to English", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // --- Full Screen Viewer / swiper carousel Overlay ---
            if (viewModel.isViewerOpen && viewModel.viewerActiveIndex in activeMedia.indices) {
                CarouselViewerOverlay(
                    viewModel = viewModel,
                    activeMedia = activeMedia,
                    onClose = { viewModel.isViewerOpen = false }
                )
            }

            // --- Photo Editor Preview Overlay ---
            if (viewModel.isEditorOpen && viewModel.editingItem != null) {
                FullScreenEditorOverlay(
                    viewModel = viewModel,
                    mediaItem = viewModel.editingItem!!,
                    onClose = { viewModel.isEditorOpen = false }
                )
            }

            // --- Floating Add Custom Media Dial ---
            if (viewModel.isAddMediaOpen) {
                AddMediaDialog(
                    onDismiss = { viewModel.isAddMediaOpen = false },
                    onAdd = { path, title, location, type ->
                        viewModel.addCustomMedia(path, title, location, type)
                    }
                )
            }
        }
    }
}

// --- SUB-SCREENS ---

@Composable
fun GalleryImagesScreen(
    viewModel: GalleryViewModel,
    activeMedia: List<MediaItem>,
    onItemClick: (Int) -> Unit
) {
    var searchVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // App top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // Dynamic columns scaling actions (Size adjustment Zoom in / zoom out sliders)
            IconButton(
                onClick = { viewModel.scaleColumnCount(zoomIn = true) },
                enabled = viewModel.gridColumnCount > 2
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In Grid",
                    tint = if (viewModel.gridColumnCount > 2) Color.White else Color.Gray
                )
            }

            IconButton(
                onClick = { viewModel.scaleColumnCount(zoomIn = false) },
                enabled = viewModel.gridColumnCount < 5
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out Grid",
                    tint = if (viewModel.gridColumnCount < 5) Color.White else Color.Gray
                )
            }

            // Actions
            IconButton(onClick = { searchVisible = !searchVisible }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }

            IconButton(onClick = { viewModel.isAddMediaOpen = true }) {
                Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add Media", tint = LightBlue)
            }
        }

        // Animated Search bar sliding
        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder = { Text(text = stringResource(id = R.string.search_hint), fontSize = 13.sp, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SamsungBlue,
                    unfocusedBorderColor = BorderGray,
                    unfocusedContainerColor = SoftGray,
                    focusedContainerColor = SoftGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                },
                singleLine = true
            )
        }

        // Samsung Horizontal Quick Filters / pills Row
        val pills = listOf(
            "Toate" to stringResource(R.string.filter_all),
            "Favorite" to stringResource(R.string.filter_favorites),
            "Video" to stringResource(R.string.filter_videos),
            "Oameni" to stringResource(R.string.filter_people),
            "Locații" to stringResource(R.string.filter_locations)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pills) { pill ->
                val isSelected = viewModel.activeFilterPill == pill.first
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) SamsungBlue else SoftGray)
                        .border(1.dp, if (isSelected) SamsungBlue else BorderGray, RoundedCornerShape(20.dp))
                        .clickable { viewModel.activeFilterPill = pill.first }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = pill.second,
                        color = if (isSelected) Color.White else LightText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Real-time group counter & header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${activeMedia.size} ${stringResource(R.string.item_count, activeMedia.size)}",
                fontSize = 12.sp,
                color = GrayText
            )
            Text(
                text = "${viewModel.gridColumnCount} ${stringResource(R.string.grid_columns_title)}",
                fontSize = 11.sp,
                color = SamsungBlue,
                fontWeight = FontWeight.Bold
            )
        }

        // Adjustable Grid Layout
        if (activeMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "No items",
                        tint = GrayText,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(id = R.string.msg_no_items), color = GrayText, fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(viewModel.gridColumnCount),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(activeMedia) { index, item ->
                    MediaThumbnailItem(
                        item = item,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .testTag("media_item_${item.id}")
                            .clickable { onItemClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun MediaThumbnailItem(
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    // Determine filter matrices to preview save edits in list dynamically!
    val filterStr = if (item.faces.startsWith("Filtru:")) item.faces.substringAfter("Filtru:") else ""
    val colorFilter = if (filterStr.isNotEmpty()) {
        getPresetColorFilter(filterStr)
    } else null

    Box(modifier = modifier.background(DarkGray)) {
        AsyncImage(
            model = item.filePath,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter
        )

        // Overlay badges
        if (item.type == "VIDEO") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item.duration.ifEmpty { "0:12" },
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
        }

        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = Color.Red,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(14.dp)
            )
        }
    }
}

// --- ALBUMS SCREEN (Samsung folders style) ---
@Composable
fun GalleryAlbumsScreen(
    viewModel: GalleryViewModel,
    mediaList: List<MediaItem>,
    onAlbumClick: (String) -> Unit
) {
    val albums = mediaList.groupBy { it.category.ifEmpty { "Natură" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_albums),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.msg_no_items), color = GrayText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums.keys.toList()) { key ->
                    val itemsInAlbum = albums[key] ?: emptyList()
                    val coverItem = itemsInAlbum.firstOrNull()
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(key) }
                    ) {
                        // Album folder cover card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkGray)
                        ) {
                            if (coverItem != null) {
                                AsyncImage(
                                    model = coverItem.filePath,
                                    contentDescription = key,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(48.dp), tint = GrayText)
                                }
                            }
                            // Item counter label overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${itemsInAlbum.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Folder metadata labels
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = key,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// --- SUGGESTIONS / AI SCANNER SCREEN ---
@Composable
fun GallerySuggestionsScreen(
    viewModel: GalleryViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.tab_suggestions),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Premium Dark card detailing Gemini Smart Classifier functions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SoftGray)
                .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(BlueAccentBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Scanner",
                        tint = LightBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.action_gemini_scan),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.gemini_scan_desc),
                    color = LightText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Perform action button
                Button(
                    onClick = { viewModel.runSmartAIScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.aiState != "Scanning"
                ) {
                    if (viewModel.aiState == "Scanning") {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.gemini_scanning))
                    } else {
                        Text(text = stringResource(R.string.gemini_scan_now))
                    }
                }
            }
        }

        // Scanning result status box
        Spacer(modifier = Modifier.height(16.dp))
        when (viewModel.aiState) {
            "Scanning" -> {
                Text(
                    text = stringResource(R.string.gemini_scanning),
                    color = LightBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            "Success" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF132F23))
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gemini_success, viewModel.aiScanResult),
                        color = Color(0xFF52D396),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "* " + stringResource(R.string.gemini_not_config),
            color = GrayText,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

// --- MENU SCREEN (With active trash bin & Localization selector) ---
@Composable
fun GalleryMenuScreen(
    viewModel: GalleryViewModel,
    trashCount: Int,
    onLanguageSelected: (String) -> Unit
) {
    var showTrashOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_menu),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Recycle Bin list row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SoftGray)
                .clickable { showTrashOverlay = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2C1E1E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Bin", tint = FavoriteRed)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.filter_trash),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$trashCount ${stringResource(R.string.item_count, trashCount)}",
                    color = GrayText,
                    fontSize = 12.sp
                )
            }
            Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = GrayText)
        }

        // Language Switcher Options
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Setări limbă / Language Settings",
            fontSize = 13.sp,
            color = GrayText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onLanguageSelected("ro") },
                colors = ButtonDefaults.buttonColors(containerColor = SoftGray),
                border = BorderStroke(1.dp, BorderGray),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🇷🇴 Română", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = { onLanguageSelected("en") },
                colors = ButtonDefaults.buttonColors(containerColor = SoftGray),
                border = BorderStroke(1.dp, BorderGray),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🇺🇸 English", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // App spec/about row (Motorola special dark branding)
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkGray)
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, tint = LightBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Motorola Edition", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = "V1.0 • Samsung Galaxy UI Theme", color = GrayText, fontSize = 11.sp)
            }
        }
    }

    // Active Trash Overlay
    if (showTrashOverlay) {
        FullScreenTrashOverlay(
            viewModel = viewModel,
            onClose = { showTrashOverlay = false }
        )
    }
}

// --- TRASH FULL SCREEN OVERLAY ---
@Composable
fun FullScreenTrashOverlay(
    viewModel: GalleryViewModel,
    onClose: () -> Unit
) {
    val trashItems by viewModel.trashListState.collectAsState()
    var isConfirmEmptyOpen by remember { mutableStateOf(false) }

    Surface(
        color = PureBlack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.filter_trash),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (trashItems.isNotEmpty()) {
                    TextButton(onClick = { isConfirmEmptyOpen = true }) {
                        Text(text = stringResource(R.string.action_empty_trash), color = FavoriteRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp)) // padding spacer
                }
            }

            // Disclaimer banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SoftGray)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.msg_trash_disclaimer),
                    color = GrayText,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (trashItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = GrayText)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.msg_no_trash_items), color = GrayText)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(trashItems) { item ->
                        var showPrompt by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showPrompt = true }
                        ) {
                            AsyncImage(
                                model = item.filePath,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Remaining days badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.msg_days_remaining, item.remainingDays),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Restore / Delete permanently dialog
                        if (showPrompt) {
                            AlertDialog(
                                onDismissRequest = { showPrompt = false },
                                title = { Text(text = item.title, color = Color.White, fontSize = 16.sp) },
                                text = {
                                    Text(
                                        text = stringResource(R.string.msg_trash_disclaimer),
                                        color = LightText,
                                        fontSize = 13.sp
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.restoreFromTrash(item.id)
                                        showPrompt = false
                                    }) {
                                        Text(text = stringResource(R.string.action_restore), color = LightBlue)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        viewModel.deletePermanently(item.id)
                                        showPrompt = false
                                    }) {
                                        Text(text = stringResource(R.string.action_delete_perm), color = FavoriteRed)
                                    }
                                },
                                containerColor = DarkGray,
                                titleContentColor = Color.White,
                                textContentColor = LightText
                            )
                        }
                    }
                }
            }
        }

        // Empty bin confirmation dialog
        if (isConfirmEmptyOpen) {
            AlertDialog(
                onDismissRequest = { isConfirmEmptyOpen = false },
                title = { Text(text = stringResource(R.string.action_empty_trash), color = Color.White, fontSize = 16.sp) },
                text = { Text(text = stringResource(R.string.msg_empty_trash_confirm), color = LightText, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.emptyTrash()
                        isConfirmEmptyOpen = false
                    }) {
                        Text(text = stringResource(R.string.action_empty_trash).uppercase(), color = FavoriteRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isConfirmEmptyOpen = false }) {
                        Text(text = stringResource(R.string.action_cancel), color = Color.White)
                    }
                },
                containerColor = DarkGray,
                titleContentColor = Color.White,
                textContentColor = LightText
            )
        }
    }
}

// --- OVERLAYS: FULL CAROUSEL GALLERY VIEWER SWIPER ---
@Composable
fun CarouselViewerOverlay(
    viewModel: GalleryViewModel,
    activeMedia: List<MediaItem>,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = viewModel.viewerActiveIndex,
        pageCount = { activeMedia.size }
    )
    var showDetailsDialog by remember { mutableStateOf(false) }
    var isDeletePromptOpen by remember { mutableStateOf(false) }

    Surface(
        color = PureBlack,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // Carousel swipeable Horizontal Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = activeMedia.getOrNull(page)
                if (item != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.type == "VIDEO") {
                            // Video player custom play
                            VideoPlayer(
                                videoUrl = item.filePath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16 / 9f)
                            )
                        } else {
                            // Static image loading with filter matrix mapping previews
                            val filterStr = if (item.faces.startsWith("Filtru:")) item.faces.substringAfter("Filtru:") else ""
                            val colorFilter = if (filterStr.isNotEmpty()) {
                                getPresetColorFilter(filterStr)
                            } else null

                            AsyncImage(
                                model = item.filePath,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                contentScale = ContentScale.Fit,
                                colorFilter = colorFilter
                            )
                        }
                    }
                }
            }

            // Top Header controllers
            val currentMedia = activeMedia.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentMedia?.title ?: "Imagine",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${activeMedia.size}",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Info Details", tint = Color.White)
                }
            }

            // Bottom Actions Container (Samsung classic detail view action menu)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite
                IconButton(onClick = { currentMedia?.let { viewModel.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (currentMedia?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentMedia?.isFavorite == true) FavoriteRed else Color.White
                    )
                }

                // Edit
                IconButton(onClick = { currentMedia?.let { viewModel.startEditing(it) } }) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                }

                // Share
                val ctx = LocalContext.current
                IconButton(onClick = {
                    currentMedia?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Vizualizează '${it.title}' în Smart Gallery: ${it.filePath}")
                        }
                        ctx.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                    }
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }

                // Delete (moves to Trash)
                IconButton(onClick = { isDeletePromptOpen = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = FavoriteRed)
                }
            }

            // Move to Bin confirmation dialog
            if (isDeletePromptOpen && currentMedia != null) {
                AlertDialog(
                    onDismissRequest = { isDeletePromptOpen = false },
                    title = { Text(text = stringResource(R.string.msg_move_to_trash), color = Color.White, fontSize = 16.sp) },
                    text = { Text(text = stringResource(R.string.msg_trash_disclaimer), color = LightText, fontSize = 13.sp) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.moveToTrash(currentMedia.id)
                            isDeletePromptOpen = false
                        }) {
                            Text(text = stringResource(R.string.action_delete).uppercase(), color = FavoriteRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isDeletePromptOpen = false }) {
                            Text(text = stringResource(R.string.action_cancel), color = Color.White)
                        }
                    },
                    containerColor = DarkGray,
                    titleContentColor = Color.White,
                    textContentColor = LightText
                )
            }

            // Info details bottom sheet dialog
            if (showDetailsDialog && currentMedia != null) {
                AlertDialog(
                    onDismissRequest = { showDetailsDialog = false },
                    title = { Text(text = stringResource(R.string.details_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                    text = {
                        Column {
                            DetailItemRow(label = stringResource(R.string.add_media_hint_title), value = currentMedia.title)
                            DetailItemRow(label = "Date added", value = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(currentMedia.dateAdded)))
                            DetailItemRow(label = stringResource(R.string.filter_locations), value = currentMedia.location.ifEmpty { "Virtual Location" })
                            DetailItemRow(label = stringResource(R.string.filter_people), value = currentMedia.faces.ifEmpty { "Fără fețe detectate (Unscanned)" })
                            DetailItemRow(label = "Objects/Tags", value = currentMedia.objects.ifEmpty { "Uncategorized" })
                            DetailItemRow(label = "Category Album", value = currentMedia.category.ifEmpty { "Default album" })
                            DetailItemRow(label = "Type format", value = currentMedia.type)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDetailsDialog = false }) {
                            Text(text = "OK", color = LightBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = SoftGray,
                    titleContentColor = Color.White,
                    textContentColor = LightText
                )
            }
        }
    }
}

@Composable
fun DetailItemRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(text = label, color = GrayText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 13.sp)
    }
}

// --- BASIC PHOTO TRANSFORMATION EDITOR VIEW ---
@Composable
fun FullScreenEditorOverlay(
    viewModel: GalleryViewModel,
    mediaItem: MediaItem,
    onClose: () -> Unit
) {
    Surface(
        color = PureBlack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.editor_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = { viewModel.saveEditedPhoto(asCopy = true) }) {
                    Text(text = stringResource(R.string.action_save_copy), color = LightBlue, fontWeight = FontWeight.Bold)
                }
            }

            // Image view with live matrix rendering filters applied
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = mediaItem.filePath,
                    contentDescription = "Editing preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                    colorFilter = getPresetColorFilter(viewModel.editorFilter)
                )
            }

            // Controls adjustment deck (Rotation, presets etc.)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SoftGray)
                    .padding(16.dp)
            ) {
                // Rotation preset selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editor_rotate), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    
                    Button(
                        onClick = { 
                            viewModel.editorFilter = when (viewModel.editorFilter) {
                                "None" -> "Mono"
                                "Mono" -> "Warm"
                                "Warm" -> "Cool"
                                "Cool" -> "Sepia"
                                "Sepia" -> "Invert"
                                else -> "None"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Preset Filter: ${viewModel.editorFilter}", color = Color.White, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom color preset horizontal scrolling filters row
                Text(text = stringResource(R.string.editor_filters), color = GrayText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                val filterPresets = listOf(
                    "None" to stringResource(R.string.editor_filter_none),
                    "Mono" to stringResource(R.string.editor_filter_grayscale),
                    "Warm" to stringResource(R.string.editor_filter_warm),
                    "Cool" to stringResource(R.string.editor_filter_cool),
                    "Sepia" to stringResource(R.string.editor_filter_sepia),
                    "Invert" to stringResource(R.string.editor_filter_invert)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filterPresets) { preset ->
                        val isSel = viewModel.editorFilter == preset.first
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) SamsungBlue else DarkGray)
                                .clickable { viewModel.editorFilter = preset.first }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(text = preset.second, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Save trigger replacement in database
                Button(
                    onClick = { viewModel.saveEditedPhoto(asCopy = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(text = stringResource(R.string.action_replace), color = Color.White)
                }
            }
        }
    }
}

// --- DIALOGS ---

@Composable
fun AddMediaDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit
) {
    var path by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("IMAGE") } // IMAGE or VIDEO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_media_title), color = Color.White, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(text = stringResource(R.string.add_media_hint_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SamsungBlue,
                        unfocusedBorderColor = BorderGray
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(R.string.add_media_hint_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SamsungBlue,
                        unfocusedBorderColor = BorderGray
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(text = stringResource(R.string.add_media_hint_location)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SamsungBlue,
                        unfocusedBorderColor = BorderGray
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                // Mode Type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.add_media_hint_type), color = Color.White, fontSize = 12.sp)
                    Row {
                        Button(
                            onClick = { type = "IMAGE" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (type == "IMAGE") SamsungBlue else SoftGray),
                            modifier = Modifier.padding(end = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = stringResource(R.string.add_media_image), fontSize = 10.sp)
                        }

                        Button(
                            onClick = { type = "VIDEO" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (type == "VIDEO") SamsungBlue else SoftGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = stringResource(R.string.add_media_video), fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (path.isNotEmpty()) {
                        onAdd(path, title, location, type)
                    }
                }
            ) {
                Text(text = stringResource(R.string.action_save), color = LightBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = Color.White)
            }
        },
        containerColor = DarkGray,
        titleContentColor = Color.White,
        textContentColor = LightText
    )
}

// --- SAMSUNG CLASSIC BOTH NAVIGATION VIEW ---
@Composable
fun SamsungBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SoftGray,
        contentColor = LightText,
        tonalElevation = 8.dp,
        modifier = Modifier.border(0.5.dp, BorderGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.Photo, contentDescription = "Images") },
            label = { Text(text = stringResource(R.string.tab_images), fontSize = 10.sp) },
            selected = selectedTab == "images",
            onClick = { onTabSelected("images") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = LightBlue,
                indicatorColor = SamsungBlue,
                unselectedIconColor = GrayText,
                unselectedTextColor = GrayText
            )
        )

        NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Albums") },
            label = { Text(text = stringResource(R.string.tab_albums), fontSize = 10.sp) },
            selected = selectedTab == "albums",
            onClick = { onTabSelected("albums") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = LightBlue,
                indicatorColor = SamsungBlue,
                unselectedIconColor = GrayText,
                unselectedTextColor = GrayText
            )
        )

        NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Suggestions") },
            label = { Text(text = stringResource(R.string.tab_suggestions), fontSize = 10.sp) },
            selected = selectedTab == "suggestions",
            onClick = { onTabSelected("suggestions") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = LightBlue,
                indicatorColor = SamsungBlue,
                unselectedIconColor = GrayText,
                unselectedTextColor = GrayText
            )
        )

        NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu") },
            label = { Text(text = stringResource(R.string.tab_menu), fontSize = 10.sp) },
            selected = selectedTab == "menu",
            onClick = { onTabSelected("menu") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = LightBlue,
                indicatorColor = SamsungBlue,
                unselectedIconColor = GrayText,
                unselectedTextColor = GrayText
            )
        )
    }
}

// --- GPU COLOR FILTER PRESETS ---

fun getPresetColorFilter(filter: String): ColorFilter? {
    return when (filter) {
        "Mono" -> {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        "Warm" -> {
            val warm = floatArrayOf(
                1.15f, 0f, 0f, 0f, 15f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(warm))
        }
        "Cool" -> {
            val cool = floatArrayOf(
                0.85f, 0f, 0f, 0f, -15f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.15f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(cool))
        }
        "Sepia" -> {
            val sepia = floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(sepia))
        }
        "Invert" -> {
            val invert = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            ColorFilter.colorMatrix(ColorMatrix(invert))
        }
        else -> null
    }
}

private fun size(dp: Int): Modifier = Modifier.size(dp.dp)
