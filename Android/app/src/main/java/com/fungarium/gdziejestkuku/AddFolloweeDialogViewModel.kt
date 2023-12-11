package com.fungarium.gdziejestkuku

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AddFolloweeDialogViewModel(
    onSubmit: (dialog: AddFolloweeDialogViewModel) -> Unit,
    onDismiss: () -> Unit
) {

    val canSubmit by derivedStateOf { nickname.isNotEmpty() }
    var nickname by mutableStateOf("")

    private val _onDismiss: () -> Unit = onDismiss
    private val _onSubmit: (dialog : AddFolloweeDialogViewModel) -> Unit = onSubmit

    fun dismiss() {
        this._onDismiss();
    }

    fun submit() {
        if (this.canSubmit)
            this._onSubmit(this);
    }
}