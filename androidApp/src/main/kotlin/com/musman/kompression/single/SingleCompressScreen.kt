package com.musman.kompression.single

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musman.kompression.batch.ImageItem

import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision

private val CompressedGreen = Color(0xFF269B58)
private val CompressedGreenTint = Color(0x14269B58)

@Composable
fun SingleCompressScreen(
    viewModel: SingleCompressViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Photo Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = viewModel::onPhotoSelected
    )

    SingleCompressContent(
        state = state,
        onSelectPhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onTargetKbChange = viewModel::onTargetKbChange,
        onCompress = viewModel::compressImage
    )
}

@Composable
fun SingleCompressContent(
    state: SingleCompressUiState,
    onSelectPhoto: () -> Unit,
    onTargetKbChange: (Float) -> Unit,
    onCompress: () -> Unit
) {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        Header()

        SelectPhotoButton(
            hasPhoto = state.original != null,
            onClick = onSelectPhoto
        )

        if (state.isLoading) {
            LoadingRow()
        }

        state.original?.let { original ->
            OriginalImageSection(original)

            TargetSizeSection(
                targetKb = state.targetKb,
                enabled = !state.isCompressing,
                onTargetKbChange = onTargetKbChange
            )
        }

        state.errorMessage?.let { message ->
            ErrorText(message)
        }

        CompressButton(
            isCompressing = state.isCompressing,
            enabled = state.canCompress,
            onClick = onCompress
        )

        state.compressed?.let { compressed ->
            CompressedImageSection(
                compressed = compressed,
                savings = state.savings
            )
        }
    }
}


@Composable
private fun Header() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Image Compressor",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select a photo, compress it, and compare the original and compressed sizes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectPhotoButton(
    hasPhoto: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (hasPhoto) "Change Photo" else "Select Photo")
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.width(12.dp))
        Text("Loading image...")
    }
}

@Composable
private fun OriginalImageSection(original: ImageItem) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Original Image",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        ImagePreview(
            item = original,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        )

        InfoRow(
            title = "Original Size",
            value = original.sizeString
        )
    }
}


@Composable
private fun TargetSizeSection(
    targetKb: Float,
    enabled: Boolean,
    onTargetKbChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Target Compression Size",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "${targetKb.toInt()} KB",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Slider(
            value = targetKb,
            onValueChange = onTargetKbChange,
            valueRange = 50f..1000f,
            steps = 94,
            enabled = enabled
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("50 KB")
            Text("1000 KB")
        }

        Text(
            "Adjust the desired target size before compression.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun CompressButton(
    isCompressing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CompressedGreen)
    ) {
        if (isCompressing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Compressing...")
        } else {
            Text(
                "Compress Image",
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun CompressedImageSection(
    compressed: ImageItem,
    savings: SpaceSavings?
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Compressed Output",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = CompressedGreen
        )

        ImagePreview(
            item = compressed,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        )

        InfoRow(
            title = "Compressed Size",
            value = compressed.sizeString,
            valueColor = CompressedGreen
        )

        savings?.let { SpaceSavedCard(it) }
    }
}


@Composable
private fun SpaceSavedCard(savings: SpaceSavings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CompressedGreenTint),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Space Saved",
                fontWeight = FontWeight.SemiBold
            )

            Text(
                String.format(LocalLocale.current.platformLocale, "%.1f%%", savings.percentage),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = CompressedGreen
            )

            Text(
                "${savings.savedSizeString} saved",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImagePreview(
    item: ImageItem,
    modifier: Modifier = Modifier
) {

    val requestBuilder = ImageRequest
        .Builder(LocalContext.current)
        .data(item.data)
        .precision(Precision.EXACT)
        .allowHardware(false)
        .build()

    AsyncImage(
        model = requestBuilder,
        contentDescription = "Image Preview",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )

}


@Composable
private fun InfoRow(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
