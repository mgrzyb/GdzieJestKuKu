package com.fungarium.gdziejestkuku

import androidx.compose.ui.graphics.ImageBitmap

class ShareLocationDialogViewModel(
    val qrCode: ImageBitmap,
    private val onDismiss: () -> Unit
) {
    fun dismiss() {
        onDismiss();
    }
}