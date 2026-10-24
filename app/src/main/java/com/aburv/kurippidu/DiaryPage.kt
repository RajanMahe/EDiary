package com.aburv.kurippidu

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aburv.kurippidu.data.TodoEntity

@Composable
fun EmptyDayPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📖",
            fontSize = 42.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "No entry for this day",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Tap Edit or start writing your thoughts",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
internal fun DiaryPage(
    text: String,
    modifier: Modifier,
    ruledLineColor: Color,
    ruledLineThickness: Float,
    todos: List<TodoEntity>,
    onDeleteTodo: ((TodoEntity) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .ruledBackground(
                lineColor = ruledLineColor,
                strokeWidth = ruledLineThickness
            )
            .padding(12.dp)
    ) {
        Column {
            todos.forEach { todo ->
                var showDeleteOption by remember(todo.id) { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = todo.isDone,
                        onCheckedChange = null
                    )

                    Text(
                        text = todo.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .then(
                                if (onDeleteTodo != null)
                                    Modifier.pointerInput(todo.id) {
                                        detectTapGestures(onLongPress = { showDeleteOption = true })
                                    }
                                else Modifier
                            ),
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
                    )

                    if (showDeleteOption && onDeleteTodo != null) {
                        TextButton(onClick = {
                            onDeleteTodo(todo)
                            showDeleteOption = false
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ✅ DIARY TEXT
            Text(
                text = text,
                style = LocalTextStyle.current.copy(
                    fontFamily = DiaryHandwritingFont,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}
