package ru.toinet.tgws

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ProxyApp()
            }
        }
    }

    @Composable
    fun AppTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable () -> Unit
    ) {
        val colorScheme = if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }

        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ProxyApp() {
        val context = LocalContext.current
        var host by remember { mutableStateOf("127.0.0.1") }
        var port by remember { mutableStateOf("1480") }
        var dcMappingsText by remember { mutableStateOf("2:149.154.167.220\n4:149.154.167.220") }
        var isRunning by remember { mutableStateOf(ProxyService.isRunning) }
        val logs = remember { mutableStateListOf<String>() }
        
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf(
            TabItem(stringResource(R.string.tab_main), Icons.Default.Home),
            TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings),
            TabItem(stringResource(R.string.tab_logs), Icons.Default.List)
        )

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ProxyService.logs.collect {
                logs.add(0, it)
                if (logs.size > 500) logs.removeAt(logs.size - 1)
            }
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                when (selectedTab) {
                    0 -> MainTab(isRunning) {
                        if (isRunning) {
                            ProxyService.stop(context)
                            isRunning = false
                        } else {
                            try {
                                val mappings = dcMappingsText.lines()
                                    .filter { it.isNotBlank() }
                                    .associate {
                                        val parts = it.split(":")
                                        parts[0].trim().toInt() to parts[1].trim()
                                    }
                                ProxyService.start(context, host, port.toInt(), mappings)
                                isRunning = true
                            } catch (e: Exception) {
                                logs.add(0, context.getString(R.string.config_error, e.message ?: ""))
                            }
                        }
                    }
                    1 -> SettingsTab(
                        host = host,
                        onHostChange = { host = it },
                        port = port,
                        onPortChange = { port = it },
                        dcMappingsText = dcMappingsText,
                        onDcMappingsChange = { dcMappingsText = it },
                        enabled = !isRunning
                    )
                    2 -> LogsTab(logs)
                }
            }
        }
    }

    @Composable
    fun MainTab(isRunning: Boolean, onToggle: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onToggle,
                modifier = Modifier.size(200.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isRunning) stringResource(R.string.stop_proxy) else stringResource(R.string.start_proxy),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    fun SettingsTab(
        host: String, onHostChange: (String) -> Unit,
        port: String, onPortChange: (String) -> Unit,
        dcMappingsText: String, onDcMappingsChange: (String) -> Unit,
        enabled: Boolean
    ) {
        Column {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.listen_address)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.port)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = dcMappingsText,
                onValueChange = onDcMappingsChange,
                label = { Text(stringResource(R.string.dc_mappings)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = enabled
            )
        }
    }

    @Composable
    fun LogsTab(logs: List<String>) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    data class TabItem(val title: String, val icon: ImageVector)
}