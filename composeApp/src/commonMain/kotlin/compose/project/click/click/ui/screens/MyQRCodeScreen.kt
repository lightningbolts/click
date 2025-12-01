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
import compose.project.click.click.ui.components.UserQrCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRCodeScreen(
    userId: String,
    username: String?,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My QR Code") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Scan this code to connect with ${username ?: "me"}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
