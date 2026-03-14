package com.eldercare.ai.ui.screens.fridge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import java.io.File
import androidx.core.content.FileProvider
import com.eldercare.ai.ui.theme.ElderCareAITheme
import com.eldercare.ai.ui.viewmodel.FridgeViewModel
import com.eldercare.ai.ui.viewmodel.ScanState
import com.eldercare.ai.ui.viewmodel.FridgeItemUi
import com.eldercare.ai.fridge.FoodStatus
import com.eldercare.ai.fridge.ShelfLifeCalculator
import com.eldercare.ai.rememberElderCareDatabase
import com.eldercare.ai.data.entity.FridgeScanEntity
import com.eldercare.ai.data.entity.FridgeScanItemEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: FridgeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 拍照的临时 URI
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val fridgeItems by viewModel.fridgeItems.collectAsStateWithLifecycle()
    
    // 相机 Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(tempImageUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        lastBitmap = bitmap
                        viewModel.scanFridge(bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后，打开相机
            try {
                val file = File.createTempFile(
                    "fridge_scan_${System.currentTimeMillis()}",
                    ".jpg",
                    context.cacheDir
                )
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                tempImageUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 显示扫描结果提示
    LaunchedEffect(scanState) {
        when (scanState) {
            is ScanState.Success -> {
                // 3秒后自动重置状态
                kotlinx.coroutines.delay(3000)
                viewModel.resetScanState()
            }
            is ScanState.Failed, is ScanState.Empty -> {
                // 5秒后自动重置状态
                kotlinx.coroutines.delay(5000)
                viewModel.resetScanState()
            }
            else -> {}
        }
    }
    
    // 选择图片（Android 13+ 使用 PickVisualMedia）
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (bitmap != null) {
                        lastBitmap = bitmap
                        viewModel.scanFridge(bitmap)
                    }
                } catch (e: Exception) {
                    // 错误处理
                }
            }
        }
    }
    
    // 选择图片（兼容旧版本，使用 GetContent）
    val pickImageLegacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (bitmap != null) {
                        lastBitmap = bitmap
                        viewModel.scanFridge(bitmap)
                    }
                } catch (e: Exception) {
                    // 错误处理
                }
            }
        }
    }
    
    // 读取媒体图片权限请求（Android 13+）
    val readMediaImagesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后，选择图片
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                pickImageLegacyLauncher.launch("image/*")
            }
        }
    }
    
    // 读取外部存储权限请求（Android 12 及以下）
    val readStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后，选择图片
            pickImageLegacyLauncher.launch("image/*")
        }
    }
    
    // 选择图片的辅助函数
    fun selectImageFromGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用 READ_MEDIA_IMAGES 权限
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                else -> {
                    readMediaImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            // Android 12 及以下使用 READ_EXTERNAL_STORAGE 权限
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    pickImageLegacyLauncher.launch("image/*")
                }
                else -> {
                    readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Text(
                text = "拍冰箱",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 拍照和选择图片按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 拍照按钮
            Card(
                onClick = { 
                    val permission = Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val file = File.createTempFile(
                                "fridge_scan_${System.currentTimeMillis()}",
                                ".jpg",
                                context.cacheDir
                            )
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            tempImageUri = uri
                            takePictureLauncher.launch(uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        cameraPermissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍冰箱",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "拍照",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            
            // 选择图片按钮
            Card(
                onClick = { selectImageFromGallery() },
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "选择图片",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "相册",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }
        
        // 扫描状态提示
        when (scanState) {
            is ScanState.Scanning -> {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "正在识别食材...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            is ScanState.Success -> {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "成功",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = (scanState as ScanState.Success).message,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            is ScanState.Empty -> {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = (scanState as ScanState.Empty).message,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            is ScanState.Failed -> {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "错误",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = (scanState as ScanState.Failed).message,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            else -> {}
        }

        if (lastBitmap != null && scanState !is ScanState.Scanning) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.scanFridge(lastBitmap!!, highAccuracy = true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "再识别一次",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("再识别一次（更准确）")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 食材列表标题
        Text(
            text = "冰箱里的食材",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 食材列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(fridgeItems, key = { it.id }) { item ->
                FridgeItemCard(
                    item = item,
                    onRemove = { viewModel.deleteItem(item.id) }
                )
            }
            
            // 底部空白
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FridgeItemCard(
    item: FridgeItemUi,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                FoodStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
                FoodStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondaryContainer
                FoodStatus.FRESH -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (item.status) {
                        FoodStatus.EXPIRED -> MaterialTheme.colorScheme.error
                        FoodStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondary
                        FoodStatus.FRESH -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                if (item.adviceText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.adviceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.status) {
                        FoodStatus.EXPIRED -> Icons.Default.Warning
                        FoodStatus.EXPIRING_SOON -> Icons.Default.Schedule
                        FoodStatus.FRESH -> Icons.Default.CheckCircle
                    },
                    contentDescription = item.status.name,
                    tint = when (item.status) {
                        FoodStatus.EXPIRED -> MaterialTheme.colorScheme.error
                        FoodStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondary
                        FoodStatus.FRESH -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(32.dp)
                )
                
                if (item.status == FoodStatus.EXPIRED) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FridgeScreenPreview() {
    ElderCareAITheme {
        FridgeScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeHistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (Long) -> Unit = {}
) {
    val db = rememberElderCareDatabase()
    val scans by db.fridgeScanDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍冰箱历史") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无历史记录")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scans, key = { it.id }) { scan ->
                    FridgeHistoryScanCard(
                        scan = scan,
                        onClick = { onNavigateToDetail(scan.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FridgeHistoryScanCard(
    scan: FridgeScanEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formatDateTime(scan.scannedAt),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "识别到 ${scan.itemCount} 种食材",
                style = MaterialTheme.typography.bodyLarge
            )
            if (scan.note.isNotBlank()) {
                Text(
                    text = scan.note,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeHistoryDetailScreen(
    scanId: Long,
    onNavigateBack: () -> Unit = {}
) {
    val db = rememberElderCareDatabase()
    val scan by db.fridgeScanDao().getById(scanId).collectAsStateWithLifecycle(initialValue = null)
    val items by db.fridgeScanItemDao().getByScanId(scanId).collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = scan?.let { formatDateTime(it.scannedAt) } ?: "加载中...",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = scan?.let { "本次识别到 ${it.itemCount} 种食材" } ?: "",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("没有找到该次记录的食材")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        FridgeHistoryItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun FridgeHistoryItemCard(item: FridgeScanItemEntity) {
    val currentTime = System.currentTimeMillis()
    val status = ShelfLifeCalculator.calculateFoodStatus(item.expiryAt, currentTime)
    val statusText = ShelfLifeCalculator.getStatusText(item.expiryAt, currentTime)
    val adviceText = ShelfLifeCalculator.getAdviceText(item.name, item.category, item.expiryAt, currentTime)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                FoodStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
                FoodStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondaryContainer
                FoodStatus.FRESH -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = when (status) {
                    FoodStatus.EXPIRED -> MaterialTheme.colorScheme.error
                    FoodStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondary
                    FoodStatus.FRESH -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (adviceText.isNotBlank()) {
                Text(
                    text = adviceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    return sdf.format(Date(epochMs))
}
