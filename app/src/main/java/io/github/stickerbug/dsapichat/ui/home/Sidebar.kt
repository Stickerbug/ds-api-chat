package io.github.stickerbug.dsapichat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stickerbug.dsapichat.R
import io.github.stickerbug.dsapichat.data.Conversation
import io.github.stickerbug.dsapichat.ui.theme.Ds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val GROUPS = listOf("今天", "昨天", "7 天内", "更早")
private val MUTED = Color(0xFF8E9094)
private val INK = Color(0xFF0F0F0F)

private fun groupIndex(ts: Long): Int {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        d == today -> 0
        d == today.minusDays(1) -> 1
        d.isAfter(today.minusDays(7)) -> 2
        else -> 3
    }
}

@Composable
fun SidebarContent(
    conversations: List<Conversation>,
    currentId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onPin: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val c = Ds.color
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    // 官方侧栏「多选」模式
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = remember(conversations, query) {
        val q = query.trim()
        if (q.isEmpty()) conversations
        else conversations.filter { it.title.contains(q, ignoreCase = true) }
    }
    val bucketed = remember(filtered) {
        GROUPS.indices.map { i -> filtered.filter { groupIndex(it.updatedAt) == i } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (c.dark) Color(0xFF1E1E1E) else Color.White)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .padding(start = 14.7.dp, end = 14.7.dp, top = 11.dp)
                .fillMaxWidth()
                .height(44.dp)
                .background(if (c.dark) c.field else Color(0xFFF5F5F5), CircleShape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(13.3.dp))
            Icon(
                painter = painterResource(R.drawable.ic_search_outline_16),
                contentDescription = "搜索",
                modifier = Modifier.size(14.dp),
                tint = MUTED
            )
            Spacer(Modifier.width(7.3.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = if (c.dark) c.textPrimary else INK),
                cursorBrush = SolidColor(c.brand),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索对话内容...", fontSize = 14.sp, color = MUTED)
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(12.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 10.3.dp, bottom = 8.dp)
        ) {
            GROUPS.indices.forEach { gi ->
                val list = bucketed[gi]
                if (list.isNotEmpty()) {
                    item(key = "h" + gi) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = 1.7.dp)
                                .height(28.7.dp)
                                .padding(start = 20.7.dp, end = 22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (gi == 0 && selecting) "已选 ${picked.size} 项" else GROUPS[gi],
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MUTED,
                                modifier = Modifier.weight(1f)
                            )
                            if (gi == 0) {
                                if (selecting) {
                                    Text(
                                        text = "取消",
                                        fontSize = 13.sp,
                                        color = MUTED,
                                        modifier = Modifier
                                            .clickable { selecting = false; picked = emptySet() }
                                            .padding(horizontal = 4.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_checklist_outline_16),
                                        contentDescription = "多选",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selecting = true; picked = emptySet() },
                                        tint = MUTED
                                    )
                                }
                            }
                        }
                    }
                    items(list, key = { it.id }) { conv ->
                        ConversationRow(
                            conv = conv,
                            selected = conv.id == currentId,
                            selecting = selecting,
                            checked = picked.contains(conv.id),
                            ink = if (c.dark) c.textPrimary else INK,
                            onClick = {
                                if (selecting) {
                                    picked = if (picked.contains(conv.id)) picked - conv.id else picked + conv.id
                                } else {
                                    onSelect(conv.id)
                                }
                            },
                            onToggleSelect = {
                                picked = if (picked.contains(conv.id)) picked - conv.id else picked + conv.id
                            },
                            onMultiSelect = { selecting = true; picked = setOf(conv.id) },
                            onPin = { onPin(conv.id) },
                            onRename = { renaming = conv },
                            onDelete = { deleting = conv }
                        )
                    }
                }
            }
        }

        if (selecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.7.dp, end = 16.dp, bottom = 8.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = if (c.dark) c.textPrimary else INK,
                    modifier = Modifier
                        .clickable { picked = conversations.map { it.id }.toSet() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "删除",
                    fontSize = 14.sp,
                    color = if (picked.isEmpty()) MUTED else Color(0xFFD93025),
                    modifier = Modifier
                        .clickable(enabled = picked.isNotEmpty()) {
                            picked.forEach { onDelete(it) }
                            picked = emptySet()
                            selecting = false
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSettings() }
                .padding(start = 14.7.dp, end = 14.7.dp, bottom = 39.3.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(29.3.dp)
                    .clip(CircleShape)
                    .background(c.brand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.chat_welcome_logo),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "本机用户",
                fontSize = 16.sp,
                color = if (c.dark) c.textPrimary else INK,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_ellipsis_outline),
                contentDescription = "设置",
                modifier = Modifier.size(18.dp),
                tint = MUTED
            )
        }
    }

    renaming?.let { conv ->
        var name by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名对话", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = INK),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(conv.id, name.trim().ifEmpty { conv.title }); renaming = null }) {
                    Text("确认", color = Ds.color.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消", color = MUTED) }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }
    deleting?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除该对话", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("删除后，该对话将不可恢复", fontSize = 14.5.sp, color = MUTED)
                    Spacer(Modifier.height(4.dp))
                    Text("由该对话生成的分享链接也将失效", fontSize = 14.5.sp, color = MUTED)
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(conv.id); deleting = null }) {
                    Text("删除", color = Color(0xFFD93025))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消", color = MUTED) }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun SideMenuDivider() {
    val c = Ds.color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(if (c.dark) Color(0xFF3A3A3E) else Color(0xFFD4D4D4))
    )
}

@Composable
private fun SideMenuRow(iconRes: Int, label: String, danger: Boolean, iconSize: androidx.compose.ui.unit.Dp = 18.dp, onClick: () -> Unit) {
    val c = Ds.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.3.dp)
            .clickable { onClick() }
            .padding(start = 16.3.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = if (danger) Color(0xFFE5484D) else if (c.dark) c.textPrimary else Color(0xFF0F0F0F)
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = if (danger) Color(0xFFE5484D) else if (c.dark) c.textPrimary else Color(0xFF0F0F0F)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: Conversation,
    selected: Boolean,
    selecting: Boolean,
    checked: Boolean,
    ink: Color,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onMultiSelect: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Ds.color
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!selecting) menu = true }
                )
                .padding(start = 20.7.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (checked) c.brand else Color.Transparent)
                        .border(1.2.dp, if (checked) c.brand else Color(0xFFCFD1D6), CircleShape)
                        .clickable { onToggleSelect() },
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_outline_20),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = conv.title,
                fontSize = 15.sp,
                color = if (selected) c.brand else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (conv.pinned) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_pin_outline_20),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MUTED
                )
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_ellipsis_outline),
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { menu = true },
                    tint = MUTED
                )
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            offset = androidx.compose.ui.unit.DpOffset(87.3.dp, 9.3.dp),
            shape = RoundedCornerShape(13.3.dp),
            containerColor = if (c.dark) c.card else Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.width(160.dp)
        ) {
            SideMenuRow(R.drawable.ic_edit_outline_20, "重命名", false) { menu = false; onRename() }
            SideMenuDivider()
            SideMenuRow(R.drawable.ic_pin_outline_20, if (conv.pinned) "取消置顶" else "置顶", false) { menu = false; onPin() }
            SideMenuDivider()
            SideMenuRow(R.drawable.ic_checklist_outline_16, "多选", false, iconSize = 16.dp) { menu = false; onMultiSelect() }
            SideMenuDivider()
            SideMenuRow(R.drawable.ic_trash_outline_20, "删除", true) { menu = false; onDelete() }
        }
    }
}