package com.aburv.kurippidu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-launch introduction.
 * Page 1: what the app promises. Page 2: explains the lock *before* any
 * fingerprint prompt appears, and lets the user skip it.
 */
@Composable
fun WelcomeScreen(
    onTurnOnLock: (onResult: (Boolean) -> Unit) -> Unit,
    onFinished: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var lockMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.height(96.dp)
        )
        Spacer(Modifier.height(16.dp))

        if (page == 0) {
            Text(
                "Welcome to Kurippidu",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Promise("🔒", "Stays on your device", "Your diary is saved only on this phone, encrypted. Nothing is uploaded.")
            Promise("👆", "Lock with fingerprint", "Keep it private with your fingerprint, face or screen lock.")
            Promise("💾", "Backup anytime", "Save everything to a file, or let it back up automatically every day.")
        } else {
            Text(
                "Protect your diary?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Each time you open Kurippidu it will ask for your fingerprint, face or " +
                        "phone screen lock, so nobody else can read your diary, even with your phone.\n\n" +
                        "When you tap \"Turn on lock\", your phone will ask you to confirm once.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            lockMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.weight(1f))

        if (page == 0) {
            Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
        } else {
            Button(
                onClick = {
                    lockMessage = null
                    onTurnOnLock { success ->
                        if (success) onFinished() else lockMessage = "Lock not turned on. You can try again or skip."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Turn on lock") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
}

@Composable
private fun Promise(icon: String, title: String, text: String) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp)) {
        Text(icon, fontSize = 26.sp, modifier = Modifier.width(44.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
        }
    }
}
