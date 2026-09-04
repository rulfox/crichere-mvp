package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.auth.PhoneEntryViewModel

/**
 * Phone Entry screen: phone number input + "request code" button. Calls the real
 * `FirebasePhoneAuthClient.sendVerificationCode` (via `PhoneEntryViewModel`/`AuthRepository`) --
 * navigation to OTP Verify on success is handled by the caller (`AuthNavHost`) via
 * [PhoneEntryViewModel.navigationEvents], not by this composable.
 */
@Composable
fun PhoneEntryScreen(viewModel: PhoneEntryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Sign in", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Enter your phone number to receive a verification code.")

        OutlinedTextField(
            value = state.phoneNumber,
            onValueChange = viewModel::onPhoneNumberChanged,
            label = { Text("Phone number (e.g. +919876543210)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.errorMessage != null) {
            Text(text = state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::requestCode,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            } else {
                Text("Send code")
            }
        }
    }
}
