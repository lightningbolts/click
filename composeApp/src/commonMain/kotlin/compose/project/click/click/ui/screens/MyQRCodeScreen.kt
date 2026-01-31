package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.User
import androidx.compose.ui.text.style.TextAlign
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.UserQrCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRCodeScreen(
    userId: String,
    username: String?,
    onNavigateBack: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                PageHeader(
                    title = "My QR Code",
                    subtitle = "Share to connect",
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val user = User(
                    id = userId,
                    name = username,
                    createdAt = 0L // Dummy
                )
                
                UserQrCode(user = user, size = 300.dp)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Scan this code to connect with ${username ?: "me"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
