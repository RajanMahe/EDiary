package com.example.diary

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity





    class BiometricAuthHelper(
        private val activity: FragmentActivity,
        private val onSuccess: () -> Unit
    ) {

        fun authenticate() {
            val executor = ContextCompat.getMainExecutor(activity)

            val fragment = activity.supportFragmentManager.fragments.firstOrNull()

            val prompt = BiometricPrompt(
                activity,  // ← still fine
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EDiary")
                .setSubtitle("Authenticate to continue")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
        }
    }