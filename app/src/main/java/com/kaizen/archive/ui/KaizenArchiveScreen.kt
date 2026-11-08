package com.kaizen.archive.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaizen.archive.data.ChatEntity
import com.kaizen.archive.data.MessageEntity
import java.text.DateFormat
import java.util.Date

private enum class MainTab(val title: String) {
    CHATS("Sohbetler"), SEARCH("Ara"), TRASH("Silinenler"), SETTINGS("Ayarlar"),
}

@Composable
fun KaizenArchiveScreen(viewModel: ArchiveViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val deletedChats by viewModel.deletedChats.collectAsStateWithLifecycle()
    val deletedMessages by viewModel.deletedMessages.collectAsStateWithLifecycle()
    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
    val chatCount by viewModel.chatCount.collectAsStateWithLifecycle()
    val messageCount by viewModel.messageCount.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importDialog by rememberSaveable { mutableStateOf(false) }
    var importName by rememberSaveable { mutableStateOf("") }
    var confirmAction by rememberSaveable { mutableStateOf<String?>(null) }
    var snackbarText by rememberSaveable { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val title = importName.ifBlank { "İçe aktarılan sohbet" }
            viewModel.importTxt(uri, title) { count ->
                snackbarText = if (count > 0) "$count mesaj içe aktarıldı" else "Dosyadan mesaj okunamadı"
            }
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationAccess(context)
                permissionGranted = viewModel.notificationAccessEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { viewModel.refreshNotificationAccess(context) }

    val selectedChat = chats.firstOrNull { it.id == selectedChatId }
    val selectedMessages by remember(selectedChatId) {
        selectedChatId?.let(viewModel::messages) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        bottomBar = {
            if (selectedChatId == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                val icon = when (item) {
                                    MainTab.CHATS -> Icons.Default.Chat
                                    MainTab.SEARCH -> Icons.Default.Search
                                    MainTab.TRASH -> Icons.Default.DeleteOutline
                                    MainTab.SETTINGS -> Icons.Default.Settings
                                }
                                Icon(icon, contentDescription = item.title)
                            },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        },
        snackbarHost = {},
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                selectedChatId != null && selectedChat != null -> ChatDetailScreen(
                    chat = selectedChat,
                    messages = selectedMessages,
                    onBack = { selectedChatId = null },
                    onToggleCapture = { viewModel.setChatCaptureEnabled(selectedChat.id, it) },
                    onDeleteMessage = viewModel::deleteMessage,
                    onClearChat = { confirmAction = "clear:${selectedChat.id}" },
                    onDeleteChat = { confirmAction = "chat:${selectedChat.id}" },
                )
                selectedChatId != null -> {
                    EmptyState("Sohbet bulunamadı", "Bu sohbet silinmiş veya taşınmış olabilir.") {
                        selectedChatId = null
                    }
                }
                tab == MainTab.CHATS -> ChatsScreen(
                    chats = chats,
                    captureEnabled = captureEnabled,
                    permissionGranted = permissionGranted,
                    messageCount = messageCount,
                    chatCount = chatCount,
                    onToggleCapture = viewModel::setCaptureEnabled,
                    onOpenPermission = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenChat = { selectedChatId = it },
                    onImport = { importDialog = true },
                )
                tab == MainTab.SEARCH -> SearchScreen(
                    viewModel = viewModel,
                    chats = chats,
                    onOpenChat = { selectedChatId = it },
                )
                tab == MainTab.TRASH -> TrashScreen(
                    chats = deletedChats,
                    messages = deletedMessages,
                    onRestoreChat = viewModel::restoreChat,
                    onDeleteChat = { confirmAction = "permanent-chat:$it" },
                    onRestoreMessage = viewModel::restoreMessage,
                    onDeleteMessage = { confirmAction = "permanent-message:$it" },
                    onEmptyTrash = { confirmAction = "empty-trash" },
                )
                else -> SettingsScreen(
                    captureEnabled = captureEnabled,
                    permissionGranted = permissionGranted,
                    onToggleCapture = viewModel::setCaptureEnabled,
                    onOpenPermission = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onImport = { importDialog = true },
                    onDeleteAll = { confirmAction = "delete-all" },
                )
            }
        }
    }

    if (importDialog) {
        AlertDialog(
            onDismissRequest = { importDialog = false },
            title = { Text("WhatsApp sohbetini içe aktar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WhatsApp'tan dışa aktardığın .txt dosyasını seç. Bu işlem yalnızca seçtiğin dosyayı okur.")
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("Sohbet adı") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    importDialog = false
                    importLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
                }) { Text("TXT dosyası seç") }
            },
            dismissButton = { TextButton(onClick = { importDialog = false }) { Text("Vazgeç") } },
        )
    }

    confirmAction?.let { action ->
        val description = when {
            action.startsWith("clear:") -> "Bu sohbetteki mesajlar Silinenler bölümüne taşınacak."
            action.startsWith("chat:") -> "Sohbet Silinenler bölümüne taşınacak."
            action.startsWith("permanent-chat:") -> "Sohbet ve içindeki tüm mesajlar kalıcı olarak silinecek."
            action.startsWith("permanent-message:") -> "Bu mesaj kalıcı olarak silinecek."
            action == "empty-trash" -> "Silinenler bölümündeki tüm sohbet ve mesajlar kalıcı olarak silinecek."
            else -> "Aktif arşivdeki tüm sohbetler Silinenler bölümüne taşınacak."
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("İşlemi onayla") },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        action.startsWith("clear:") -> viewModel.clearChat(action.substringAfter(':').toLong())
                        action.startsWith("chat:") -> viewModel.deleteChat(action.substringAfter(':').toLong())
                        action.startsWith("permanent-chat:") -> viewModel.permanentlyDeleteChat(action.substringAfter(':').toLong())
                        action.startsWith("permanent-message:") -> viewModel.permanentlyDeleteMessage(action.substringAfter(':').toLong())
                        action == "empty-trash" -> viewModel.emptyTrash()
                        action == "delete-all" -> viewModel.softDeleteAllActiveChats()
                    }
                    confirmAction = null
                }) { Text("Onayla", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("İptal") } },
        )
    }

    snackbarText?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2500)
            snackbarText = null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Card(Modifier.padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                Text(message, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsScreen(
    chats: List<ChatEntity>,
    captureEnabled: Boolean,
    permissionGranted: Boolean,
    messageCount: Int,
    chatCount: Int,
    onToggleCapture: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onImport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("KAIZEN Archive", fontWeight = FontWeight.Bold)
                    Text("Kişisel mesaj arşivin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                IconButton(onClick = onImport) { Icon(Icons.Default.FolderOpen, contentDescription = "TXT içe aktar") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CaptureCard(
                    enabled = captureEnabled,
                    permissionGranted = permissionGranted,
                    onToggle = onToggleCapture,
                    onOpenPermission = onOpenPermission,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("$messageCount", "Mesaj", Modifier.weight(1f), Icons.Default.Message)
                    StatCard("$chatCount", "Sohbet", Modifier.weight(1f), Icons.Default.Chat)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Sohbetler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${chats.size} kayıt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Sohbet adı veya son mesaj") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            val visible = chats.filter {
                it.title.contains(query, ignoreCase = true) || it.lastMessage.contains(query, ignoreCase = true)
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "Henüz sohbet yok" else "Sonuç bulunamadı",
                        body = if (query.isBlank()) "WhatsApp bildirim erişimini aç veya dışa aktardığın bir TXT sohbetini içe aktar." else "Arama ifadesini değiştirebilirsin.",
                        action = if (query.isBlank()) onImport else null,
                        actionLabel = "TXT içe aktar",
                    )
                }
            } else {
                items(visible, key = { it.id }) { chat -> ChatRow(chat, onClick = { onOpenChat(chat.id) }) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CaptureCard(
    enabled: Boolean,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EAFE)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE0D2F7)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = Color(0xFF65449A))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Canlı mesaj kaydı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF35234E))
                    Text(if (enabled) "Yeni bildirimler arşivlenir" else "Yeni kayıt duraklatıldı", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6E5D83))
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Divider(color = Color(0xFFDCCEF0))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = if (permissionGranted) Color(0xFF278451) else Color(0xFF9A6C22))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(if (permissionGranted) "Bildirim erişimi açık" else "Bildirim erişimi gerekli", fontWeight = FontWeight.SemiBold, color = Color(0xFF35234E))
                    Text("Yalnızca WhatsApp bildirimleri işlenir", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6E5D83))
                }
                if (!permissionGranted) TextButton(onClick = onOpenPermission) { Text("Aç") }
            }
            if (enabled && !permissionGranted) {
                Text("Kayıt anahtarı açık; ancak Android izni verilene kadar canlı mesaj alınamaz.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B4E1A))
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(chat.title.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chat.lastMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatTime(chat.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    onBack: () -> Unit,
    onToggleCapture: (Boolean) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${messages.size} mesaj", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") } },
            actions = {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Sohbet seçenekleri") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Sohbeti temizle") }, onClick = { menuExpanded = false; onClearChat() })
                    DropdownMenuItem(text = { Text("Sohbeti Silinenler'e taşı") }, onClick = { menuExpanded = false; onDeleteChat() })
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bu sohbeti kaydet", fontWeight = FontWeight.SemiBold)
                Text("Yalnızca bu sohbet için geçerlidir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = chat.captureEnabled, onCheckedChange = onToggleCapture)
        }
        Divider()
        if (messages.isEmpty()) {
            EmptyState("Henüz mesaj yok", "Yeni bildirimler geldikçe bu sohbette görünür.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, onDelete = { onDeleteMessage(message.id) })
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.sender, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Mesajı sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (message.source == "TXT_IMPORT") "TXT içe aktarma" else "Canlı bildirim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    viewModel: ArchiveViewModel,
    chats: List<ChatEntity>,
    onOpenChat: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by remember(query) { viewModel.search(query) }.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Arşivde ara", fontWeight = FontWeight.Bold) })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Mesaj veya gönderen ara") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        if (query.isBlank()) {
            EmptyState("Mesajlarını bul", "Arşivdeki mesaj metinlerinde ve gönderen adlarında ara.")
        } else if (results.isEmpty()) {
            EmptyState("Sonuç bulunamadı", "Farklı bir arama deneyebilirsin.")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { message ->
                    val chat = chats.firstOrNull { it.id == message.chatId }
                    Card(Modifier.fillMaxWidth().clickable { onOpenChat(message.chatId) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(chat?.title ?: "Sohbet", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Text(message.sender, fontWeight = FontWeight.SemiBold)
                            Text(message.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashScreen(
    chats: List<ChatEntity>,
    messages: List<MessageEntity>,
    onRestoreChat: (Long) -> Unit,
    onDeleteChat: (Long) -> Unit,
    onRestoreMessage: (Long) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Silinenler", fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = onEmptyTrash, enabled = chats.isNotEmpty() || messages.isNotEmpty()) { Text("Çöpü boşalt") } },
        )
        if (chats.isEmpty() && messages.isEmpty()) {
            EmptyState("Silinen öğe yok", "Sildiğin sohbetler ve mesajlar burada görünür.")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (chats.isNotEmpty()) {
                    item { Text("Silinen sohbetler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(chats, key = { "c-${it.id}" }) { chat ->
                        TrashRow(
                            title = chat.title,
                            preview = chat.lastMessage,
                            onRestore = { onRestoreChat(chat.id) },
                            onDelete = { onDeleteChat(chat.id) },
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    item { Text("Silinen mesajlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(messages, key = { "m-${it.id}" }) { message ->
                        TrashRow(
                            title = message.sender,
                            preview = message.text,
                            onRestore = { onRestoreMessage(message.id) },
                            onDelete = { onDeleteMessage(message.id) },
                        )
                    }
                }
                item { Text("Silinen öğeler 30 gün sonra otomatik temizlenir.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, preview: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRestore) { Icon(Icons.Default.Restore, contentDescription = null); Text(" Geri yükle") }
                TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null); Text(" Kalıcı sil", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    captureEnabled: Boolean,
    permissionGranted: Boolean,
    onToggleCapture: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Ayarlar", fontWeight = FontWeight.Bold) })
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kayıt kontrolü", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("WhatsApp mesaj kaydı", Modifier.weight(1f))
                            Switch(checked = captureEnabled, onCheckedChange = onToggleCapture)
                        }
                        Text("Kapalıyken yeni canlı bildirimler kaydedilmez. Önceden arşivlenenler korunur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bildirim erişimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (permissionGranted) "Android bildirim erişimi etkin." else "Canlı kayıt için Android bildirim erişimini etkinleştirmen gerekiyor.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onOpenPermission, modifier = Modifier.fillMaxWidth()) { Text(if (permissionGranted) "Android ayarlarını aç" else "Bildirim erişimini aç") }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Geçmiş sohbetler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("WhatsApp'tan dışa aktardığın .txt sohbetlerini içe aktarabilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Text(" TXT içe aktar") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Arşiv yönetimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Aktif sohbetlerin tamamını Silinenler bölümüne taşı.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null); Text(" Tüm arşivi sil") }
                    }
                }
            }
            item {
                Text("Gizlilik: Mesaj verileri bu uygulamanın özel yerel depolamasında tutulur. Uygulama ağ izni istemez. Veritabanı henüz ayrıca şifrelenmez; cihaz kilidini kullanman önerilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Text("KAIZEN Archive · 0.1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    action: (() -> Unit)? = null,
    actionLabel: String = "Devam et",
) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = action) { Text(actionLabel) }
    }
}

private fun formatTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
