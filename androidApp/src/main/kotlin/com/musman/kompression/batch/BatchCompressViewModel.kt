package com.musman.kompression.batch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musman.kompression.CompressState
import com.musman.kompression.ImageCompressionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class BatchCompressUiState(
    val originalImages: List<ImageItem> = emptyList(),
    val compressedImages: List<ImageItem> = emptyList(),
    val targetKb: Float = 300f,
    val isLoading: Boolean = false,
    val isCompressing: Boolean = false
) {
    val canCompress: Boolean
        get() = originalImages.isNotEmpty() && !isCompressing && !isLoading
}


sealed interface BatchCompressEvent {
    data class CompressionCompleted(val count: Int) : BatchCompressEvent
}

class BatchCompressViewModel(application: Application) : AndroidViewModel(application) {

    private val compressionSource = ImageCompressionSource()

    private val _uiState = MutableStateFlow(BatchCompressUiState())
    val uiState: StateFlow<BatchCompressUiState> = _uiState.asStateFlow()

    private val _events = Channel<BatchCompressEvent>(Channel.BUFFERED)
    val events: Flow<BatchCompressEvent> = _events.receiveAsFlow()

    fun onTargetKbChange(value: Float) {
        _uiState.update { it.copy(targetKb = value) }
    }

    fun onPhotosSelected(uris: List<Uri>) {

        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    originalImages = emptyList(),
                    compressedImages = emptyList()
                )
            }

            val context = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> loadImage(context, uri) }
            }

            _uiState.update { it.copy(originalImages = loaded, isLoading = false) }
        }
    }

    fun compressAll() {
        val state = _uiState.value
        if (!state.canCompress) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, compressedImages = emptyList()) }

            val results = mutableListOf<ImageItem>()

            try {
                for (original in state.originalImages) {
                    compressSingle(original, state.targetKb)?.let { results.add(it) }
                }

                _uiState.update { it.copy(compressedImages = results) }
                _events.send(BatchCompressEvent.CompressionCompleted(results.size))
            } finally {
                _uiState.update { it.copy(isCompressing = false) }
            }
        }
    }

    private suspend fun compressSingle(original: ImageItem, targetKb: Float): ImageItem? {
        return try {

            val response = withContext(Dispatchers.IO) {
                compressionSource.compressImage(
                    original = original.data,
                    targetKb = targetKb
                )
            }

            when (response) {
                is CompressState.Done -> {
                    println(response.summary)

                    val compressedData = response.preview
                    withContext(Dispatchers.IO) {
                        decodeImageItem(getApplication(), compressedData)
                    }
                }

                is CompressState.Failed -> {
                    println("Compression failed: ${response.message}")
                    null
                }

                else -> null
            }
        } catch (e: Exception) {
            println("Single image compression error: ${e.message}")
            null
        }
    }
}
