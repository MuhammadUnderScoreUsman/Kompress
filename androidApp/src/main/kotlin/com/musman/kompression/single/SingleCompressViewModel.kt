package com.musman.kompression.single

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musman.kompression.CompressState
import com.musman.kompression.ImageCompressionSource
import com.musman.kompression.batch.ImageItem
import com.musman.kompression.batch.decodeImageItem
import com.musman.kompression.batch.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpaceSavings(
    val percentage: Double,
    val savedSizeString: String
)

data class SingleCompressUiState(
    val original: ImageItem? = null,
    val compressed: ImageItem? = null,
    val savings: SpaceSavings? = null,
    val targetKb: Float = 200f,
    val isLoading: Boolean = false,
    val isCompressing: Boolean = false,
    val errorMessage: String? = null
) {
    val canCompress: Boolean
        get() = original != null && !isCompressing && !isLoading
}

class SingleCompressViewModel(application: Application) : AndroidViewModel(application) {


    private val compressionSource = ImageCompressionSource()

    private val _uiState = MutableStateFlow(SingleCompressUiState())
    val uiState: StateFlow<SingleCompressUiState> = _uiState.asStateFlow()



    fun onTargetKbChange(value: Float) {
        _uiState.update { it.copy(targetKb = value) }
    }

    fun onPhotoSelected(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    original = null,
                    compressed = null,
                    savings = null
                )
            }

            val context = getApplication<Application>()

            try {
                val item = withContext(Dispatchers.IO) {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?.let { bytes -> decodeImageItem(context, bytes) }
                }

                _uiState.update {
                    it.copy(
                        original = item,
                        errorMessage = if (item == null) "Unable to load image." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Image loading failed.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun compressImage() {
        val state = _uiState.value
        val original = state.original ?: return
        if (!state.canCompress) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCompressing = true,
                    compressed = null,
                    savings = null,
                    errorMessage = null
                )
            }

            try {
                val response = withContext(Dispatchers.Default) {
                    compressionSource.compressImage(
                        original = original.data,
                        targetKb = state.targetKb
                    )
                }

                when (response) {
                    is CompressState.Done -> handleCompressed(original, response.preview)

                    is CompressState.Failed ->
                        _uiState.update { it.copy(errorMessage = response.message) }

                    else ->
                        _uiState.update { it.copy(errorMessage = "Unknown compression result.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Compression failed.") }
            } finally {
                _uiState.update { it.copy(isCompressing = false) }
            }
        }
    }


    private suspend fun handleCompressed(original: ImageItem, compressedData: ByteArray) {
        val context = getApplication<Application>()

        val compressedItem = withContext(Dispatchers.Default) {
            decodeImageItem(context, compressedData)
        }

        if (compressedItem == null) {
            _uiState.update { it.copy(errorMessage = "Unable to display compressed image.") }
            return
        }

        _uiState.update {
            it.copy(
                compressed = compressedItem,
                savings = calculateSavings(
                    originalSize = original.data.size,
                    compressedSize = compressedData.size
                )
            )
        }
    }

    private fun calculateSavings(originalSize: Int, compressedSize: Int): SpaceSavings {
        val savedBytes = (originalSize - compressedSize).coerceAtLeast(0)

        val percentage = if (originalSize > 0) {
            savedBytes.toDouble() / originalSize.toDouble() * 100
        } else {
            0.0
        }

        return SpaceSavings(
            percentage = percentage,
            savedSizeString = formatBytes(getApplication(), savedBytes.toLong())
        )
    }
}
