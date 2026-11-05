package com.musman.kompression.batch

import android.os.Build
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision

private val CompressedGreen = Color(0xFF16A34A)

@Composable
fun BatchCompressScreen(
    viewModel: BatchCompressViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()


    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BatchCompressEvent.CompressionCompleted -> Toast.makeText(
                    context,
                    "Compression completed: ${event.count} images",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(maxItems = 50),
        onResult = viewModel::onPhotosSelected
    )

    BatchCompressContent(
        state = state,
        onSelectPhotos = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onTargetKbChange = viewModel::onTargetKbChange,
        onCompress = viewModel::compressAll
    )
}


@Composable
fun BatchCompressContent(
    state: BatchCompressUiState,
    onSelectPhotos: () -> Unit,
    onTargetKbChange: (Float) -> Unit,
    onCompress: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

                Header()

                SelectPhotosButton(onClick = onSelectPhotos)

                if (state.isLoading) {
                    LoadingIndicator()
                }

                if (state.originalImages.isNotEmpty()) {
                    OriginalImagesSection(state.originalImages)
                }

                if (state.originalImages.isEmpty() && !state.isLoading) {
                    EmptyState()
                }

                TargetSizeSlider(
                    targetKb = state.targetKb,
                    enabled = !state.isCompressing,
                    onTargetKbChange = onTargetKbChange
                )

                CompressButton(
                    photoCount = state.originalImages.size,
                    isCompressing = state.isCompressing,
                    enabled = state.canCompress,
                    onClick = onCompress
                )

                if (state.compressedImages.isNotEmpty()) {
                    CompressedImagesSection(state.compressedImages)
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Batch Compression",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select multiple photos, compress them, and compare their sizes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectPhotosButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text("Select Photos")
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OriginalImagesSection(images: List<ImageItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Original Photos (${images.size})",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(images) { item ->
                ImageCard(item)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No Photos Selected\nTap Select Photos to choose images.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TargetSizeSlider(
    targetKb: Float,
    enabled: Boolean,
    onTargetKbChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Target size: ${targetKb.toInt()} KB",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Slider(
            value = targetKb,
            onValueChange = onTargetKbChange,
            valueRange = 50f..1000f,
            steps = 949,
            enabled = enabled
        )
    }
}

@Composable
private fun CompressButton(
    photoCount: Int,
    isCompressing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CompressedGreen)
    ) {
        if (isCompressing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Compressing...")
        } else {
            Text("Compress $photoCount Photos")
        }
    }
}

@Composable
private fun CompressedImagesSection(images: List<ImageItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Compressed Output",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = CompressedGreen
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(images) { item ->
                ImageCard(item = item, isCompressed = true)
            }
        }
    }
}

@Composable
fun ImageCard(
    item: ImageItem,
    isCompressed: Boolean = false
) {

    val requestBuilder = ImageRequest
        .Builder(LocalContext.current)
        .data(item.data)
        .precision(Precision.EXACT)
        .allowHardware(false)
        .build()

    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        AsyncImage(
            model = requestBuilder,
            contentDescription = if (isCompressed) "Compressed image" else "Selected image",
            modifier = Modifier
                .size(140.dp)
                .background(Color.LightGray, RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        Text(
            text = item.sizeString,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCompressed) CompressedGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}
