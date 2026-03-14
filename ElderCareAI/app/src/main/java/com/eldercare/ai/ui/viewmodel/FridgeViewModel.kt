package com.eldercare.ai.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.fridge.FoodStatus
import com.eldercare.ai.fridge.FridgeRepository
import com.eldercare.ai.fridge.ScanResult
import com.eldercare.ai.fridge.ShelfLifeCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 冰箱管理ViewModel
 */
class FridgeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = ElderCareDatabase.getDatabase(application, viewModelScope)
    private val repository = FridgeRepository(
        application,
        database.fridgeItemDao(),
        database.fridgeScanDao(),
        database.fridgeScanItemDao()
    )
    
    // UI状态
    private val _uiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)
    val uiState: StateFlow<FridgeUiState> = _uiState.asStateFlow()
    
    // 扫描状态
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    // 食材列表
    val fridgeItems: StateFlow<List<FridgeItemUi>> = repository.getAllItems()
        .map { items -> items.map { it.toUiModel() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        initializeRepository()
    }
    
    /**
     * 初始化仓库
     */
    private fun initializeRepository() {
        viewModelScope.launch {
            val success = repository.initialize()
            _uiState.value = if (success) {
                FridgeUiState.Ready
            } else {
                FridgeUiState.Error("初始化失败")
            }
        }
    }
    
    /**
     * 扫描冰箱图片
     */
    fun scanFridge(bitmap: Bitmap, highAccuracy: Boolean = false) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            
            when (val result = repository.scanFridge(bitmap, highAccuracy)) {
                is ScanResult.Success -> {
                    val hint = when {
                        result.unknownCount > 0 -> "（${result.unknownCount}种看不清，建议补拍或点“再识别一次”）"
                        result.wasUpgraded -> "（已使用更准确识别）"
                        else -> ""
                    }
                    _scanState.value = ScanState.Success(
                        message = "识别成功！找到${result.itemCount}种食材$hint",
                        itemCount = result.itemCount
                    )
                }
                is ScanResult.Empty -> {
                    _scanState.value = ScanState.Empty(result.message)
                }
                is ScanResult.Error -> {
                    _scanState.value = ScanState.Failed(result.message)
                }
            }
        }
    }
    
    /**
     * 重置扫描状态
     */
    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
    
    /**
     * 删除食材
     */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            repository.deleteItem(itemId)
        }
    }
    
    /**
     * 清空所有食材
     */
    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}

/**
 * UI状态
 */
sealed class FridgeUiState {
    object Loading : FridgeUiState()
    object Ready : FridgeUiState()
    data class Error(val message: String) : FridgeUiState()
}

/**
 * 扫描状态
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val message: String, val itemCount: Int) : ScanState()
    data class Empty(val message: String) : ScanState()
    data class Failed(val message: String) : ScanState()
}

/**
 * 食材UI模型
 */
data class FridgeItemUi(
    val id: Long,
    val name: String,
    val category: String,
    val addedAt: Long,
    val expiryAt: Long,
    val status: FoodStatus,
    val statusText: String,
    val adviceText: String,
    val remainingDays: Long
)

/**
 * 将数据库实体转换为UI模型
 */
private fun FridgeItemEntity.toUiModel(): FridgeItemUi {
    val currentTime = System.currentTimeMillis()
    val status = ShelfLifeCalculator.calculateFoodStatus(expiryAt, currentTime)
    val statusText = ShelfLifeCalculator.getStatusText(expiryAt, currentTime)
    val adviceText = ShelfLifeCalculator.getAdviceText(name, category, expiryAt, currentTime)
    val remainingDays = ShelfLifeCalculator.getRemainingDays(expiryAt, currentTime)
    
    return FridgeItemUi(
        id = id,
        name = name,
        category = category,
        addedAt = addedAt,
        expiryAt = expiryAt,
        status = status,
        statusText = statusText,
        adviceText = adviceText,
        remainingDays = remainingDays
    )
}
