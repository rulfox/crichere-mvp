package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.auth.OtpVerifyViewModel

/**
 * OTP Verify screen: 6-digit code entry, 60s resend cooldown with a visible countdown, max 3
 * resends, max 5 wrong attempts (all enforced by [OtpVerifyViewModel], not this composable --
 * this screen only renders [OtpVerifyViewModel.state] and forwards actions). Navigation
 * (Profile Setup / Own Profile / forced-back-to-Phone-Entry) is handled by the caller
 * (`AuthNavHost`) via [OtpVerifyViewModel.navigationEvents].
 */
@Composable
fun OtpVerifyScreen(viewModel: OtpVerifyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Enter verification code", style = MaterialTheme.typography.headlineSmall)
        Text(text = "We sent a 6-digit code by SMS.")

        OutlinedTextField(
            value = state.code,
            onValueChange = { value -> if (value.length <= 6) viewModel.onCodeChanged(value) },
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.errorMessage != null) {
            Text(text = state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::verifyCode,
            enabled = !state.isVerifying,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isVerifying) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            } else {
                Text("Verify")
            }
        }

        TextButton(
            onClick = viewModel::resendCode,
            enabled = state.canResend && !state.isResending,
        ) {
            Text(
                if (state.canResend) {
                    "Resend code (${state.resendsUsed}/${state.maxResends} used)"
                } else {
                    "Resend in ${state.cooldownSecondsRemaining}s"
                },
            )
        }
    }
}
