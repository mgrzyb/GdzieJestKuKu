package com.fungarium.gdziejestkuku

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun AddFolloweeDialog(dialog : AddFolloweeDialogViewModel) {
    var foloweeNicknameFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = { dialog.dismiss() },
        confirmButton = { Button(onClick = { dialog.submit()}, enabled = dialog.canSubmit )  { Text("Add") } },
        dismissButton = {
            Button(onClick = { dialog.dismiss()}) { Text("Cancel") }
        },
        text = {
            LaunchedEffect(Unit) {
                foloweeNicknameFocus.requestFocus()
            }

            OutlinedTextField(
                modifier = Modifier.focusRequester(foloweeNicknameFocus),
                singleLine = true,
                label = { Text("Nickname") },
                value = dialog.nickname,
                onValueChange = { v -> dialog.nickname = v },
                keyboardActions = KeyboardActions(onDone = { dialog.submit() })
            )
        }
    )
}
