@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aburv.kurippidu.DiaryViewModel.BackupEvent
import com.aburv.kurippidu.data.TodoEntity
import com.aburv.kurippidu.data.TodoType
import com.aburv.kurippidu.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Pages are days: page [ANCHOR_PAGE] is the anchor date, one page per day either side. */
private const val PAGE_COUNT = 200_000
private const val ANCHOR_PAGE = PAGE_COUNT / 2

@Composable
fun DiaryScreen(
    diaryId: Int,
    onExportStarted: () -> Unit,
    onExportFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val diaryViewModel: DiaryViewModel = viewModel()
    val context = LocalContext.current

    // Save pending edits as soon as the app goes to the background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) diaryViewModel.saveCurrentDiary()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Must run first: gates all database access behind the right diary
    LaunchedEffect(diaryId) { diaryViewModel.setActiveDiary(diaryId) }

    // Open on the day the user last looked at
    var startDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) {
        if (startDate == null) {
            BackupPreferences.lastOpenedDate(context, diaryId).collect { saved ->
                if (startDate == null) {
                    startDate = saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                }
            }
        }
    }

    val anchor = startDate
    if (anchor == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    DiaryContent(
        diaryId = diaryId,
        anchorDate = anchor,
        viewModel = diaryViewModel,
        onExportStarted = onExportStarted,
        onExportFinished = onExportFinished,
        onBack = onBack,
        onOpenSettings = onOpenSettings
    )
}

@Composable
private fun DiaryContent(
    diaryId: Int,
    anchorDate: LocalDate,
    viewModel: DiaryViewModel,
    onExportStarted: () -> Unit,
    onExportFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun dateForPage(page: Int): LocalDate = anchorDate.plusDays((page - ANCHOR_PAGE).toLong())
    fun pageForDate(date: LocalDate): Int = ANCHOR_PAGE + ChronoUnit.DAYS.between(anchorDate, date).toInt()

    val pagerState = rememberPagerState(initialPage = ANCHOR_PAGE) { PAGE_COUNT }
    val currentDate = dateForPage(pagerState.settledPage)
    val today = LocalDate.now()
    val isToday = currentDate == today
    val showEditor = isToday || viewModel.isEditMode

    // A settled page is the open day: save the old one, load the new one
    LaunchedEffect(currentDate) {
        viewModel.saveCurrentDiary()
        viewModel.loadDiaryForDate(currentDate)
        viewModel.updateEditModeForDate(currentDate)
        viewModel.loadTodos(currentDate)
        BackupPreferences.setLastOpenedDate(context, diaryId, currentDate.toString())
    }
    // A small tick when a swipe lands on a new day
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Close days slide; far jumps (calendar, search) go straight there. */
    fun goTo(date: LocalDate) {
        val target = pageForDate(date)
        scope.launch {
            if (abs(target - pagerState.currentPage) <= 3) pagerState.animateScrollToPage(target)
            else pagerState.scrollToPage(target)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showBackupScreen by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAddTodo by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showWeekly by remember { mutableStateOf(false) }
    var hiddenMemoryDates by rememberSaveable { mutableStateOf(setOf<String>()) }

    var showPhotoSource by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<com.aburv.kurippidu.data.AttachmentEntity?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val photoError: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    // Pickers and camera open other screens: keep the app lock off meanwhile
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onExportFinished()
        uri?.let { viewModel.addPhoto(it, photoError) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        onExportFinished()
        val file = cameraFile
        if (saved && file != null) viewModel.addPhoto(android.net.Uri.fromFile(file), photoError)
        else file?.delete()
        cameraFile = null
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onExportFinished()
        result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let(viewModel::insertAtCursor)
    }
    fun startVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak your entry")
        try {
            onExportStarted()
            voiceLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            onExportFinished()
            photoError("Speech input isn't available on this phone")
        }
    }

    val reminderTime by BackupPreferences.reminderTime(context, diaryId).collectAsState(initial = null)
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) scope.launch {
            snackbarHostState.showSnackbar("Notifications are off for Kurippidu, so reminders can't be shown.")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.backupEvents.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is BackupEvent.Success -> event.message
                    is BackupEvent.Error -> event.message
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DiaryTopBar(
                saveState = viewModel.saveState,
                showSaveState = showEditor,
                showEdit = !showEditor,
                showMenu = showMenu,
                onBack = onBack,
                onSearch = { showSearch = true },
                onEdit = { viewModel.enableEditMode() },
                onMenu = { showMenu = it },
                onSettings = { showMenu = false; onOpenSettings() },
                onBackup = { showMenu = false; showBackupScreen = true },
                onReminder = { showMenu = false; showReminder = true },
                onWeekly = { showMenu = false; showWeekly = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant) // the "desk" behind the page
        ) {
            DateNavigationRow(
                dateText = dateLabel(currentDate, today),
                color = MaterialTheme.colorScheme.onSurface,
                onMoveDays = { days -> goTo(currentDate.plusDays(days.toLong())) },
                onDateClick = { showCalendar = true },
                onTodayClick = { if (!isToday) goTo(today) }
            )

            HorizontalPager(
                state = pagerState,
                // Editing keeps the page still so selecting text never turns the page
                userScrollEnabled = !showEditor,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f)
            ) { page ->
                val date = dateForPage(page)
                PaperPage(pagerState = pagerState, page = page) {
                    if (date != currentDate) {
                        NeighbourPage(date, viewModel)
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            // "On this day": earlier years' entry for this date
                            viewModel.memories.firstOrNull()
                                ?.takeIf { it.date !in hiddenMemoryDates }
                                ?.let { memory ->
                                    MemoryCard(
                                        memory = memory,
                                        today = currentDate,
                                        onOpen = { goTo(LocalDate.parse(memory.date)) },
                                        onDismiss = { hiddenMemoryDates = hiddenMemoryDates + memory.date }
                                    )
                                }
                            Box(Modifier.weight(1f)) {
                                if (showEditor) {
                                    EditorPage(
                                        viewModel = viewModel,
                                        date = date,
                                        onAddTodo = { showAddTodo = true },
                                        onAddRecurring = { showRecurringDialog = true },
                                        onTemplates = { showTemplates = true },
                                        onManageRecurring = { showRecurringManager = true },
                                        onAddPhoto = { showPhotoSource = true },
                                        onVoice = ::startVoice,
                                        onOpenPhoto = { viewingPhoto = it }
                                    )
                                } else {
                                    PreviewPage(viewModel, onOpenPhoto = { viewingPhoto = it })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /* ---------------- SHEETS & DIALOGS ---------------- */

    if (showTemplates) {
        val templates = templatesFor(viewModel.diaryType)
        ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
            templates.forEach { template ->
                ListItem(
                    headlineContent = { Text(template.label) },
                    leadingContent = { Text(template.emoji) },
                    modifier = Modifier.clickable {
                        showTemplates = false
                        viewModel.insertTemplate(template)
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddTodo) {
        var newTodoText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTodo = false },
            title = { Text("Add Task") },
            text = {
                TextField(
                    value = newTodoText,
                    onValueChange = { newTodoText = it },
                    placeholder = { Text("Enter your task") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTodoText.isNotBlank()) {
                        viewModel.addTodo(newTodoText.trim(), currentDate)
                        showAddTodo = false
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showAddTodo = false }) { Text("Cancel") } }
        )
    }

    if (showRecurringDialog) {
        RecurringTaskDialog(
            onDismiss = { showRecurringDialog = false },
            onConfirm = { title, start, end, repeatDays ->
                viewModel.addRecurringTodo(title, start, end, repeatDays)
                showRecurringDialog = false
            }
        )
    }

    if (showReminder) {
        ReminderDialog(
            current = reminderTime,
            onSave = { time ->
                showReminder = false
                scope.launch {
                    com.aburv.kurippidu.reminders.Reminders.set(context, diaryId, time)
                    snackbarHostState.showSnackbar(
                        if (time == null) "Reminder off" else "Reminder set: ${formatReminder(time)}"
                    )
                }
                if (time != null && !com.aburv.kurippidu.reminders.Reminders.canNotify(context) &&
                    android.os.Build.VERSION.SDK_INT >= 33
                ) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { showReminder = false }
        )
    }

    if (showWeekly) {
        val summary by produceState<com.aburv.kurippidu.data.WeeklySummary?>(null) {
            value = viewModel.weeklySummary()
        }
        WeeklySummarySheet(summary = summary, onDismiss = { showWeekly = false })
    }

    if (showPhotoSource) {
        PhotoSourceSheet(
            onGallery = {
                showPhotoSource = false
                onExportStarted()
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                showPhotoSource = false
                val file = java.io.File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg")
                    .apply { parentFile?.mkdirs() }
                cameraFile = file
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                onExportStarted()
                cameraLauncher.launch(uri)
            },
            onDismiss = { showPhotoSource = false }
        )
    }

    viewingPhoto?.let { photo ->
        PhotoViewer(
            file = viewModel.photoFile(photo),
            onDelete = {
                viewModel.deletePhoto(photo)
                viewingPhoto = null
            },
            onDismiss = { viewingPhoto = null }
        )
    }

    if (showRecurringManager) {
        RecurringTaskManagerSheet(viewModel = viewModel, onDismiss = { showRecurringManager = false })
    }

    if (showCalendar) {
        ModalBottomSheet(onDismissRequest = { showCalendar = false }) {
            CalendarMonthView(
                currentMonth = YearMonth.from(currentDate),
                entryDates = viewModel.entryDates,
                dotColors = viewModel.moods.mapValues { (_, mood) -> Color(mood.color) },
                onDateClick = { date ->
                    showCalendar = false
                    goTo(date)
                }
            )
        }
    }

    if (showSearch) {
        SearchSheet(
            onClose = {
                showSearch = false
                viewModel.clearSearch()
            },
            onResultClick = { date ->
                showSearch = false
                goTo(date)
            },
            viewModel = viewModel
        )
    }

    // Drawn last so it covers the whole diary screen
    if (showBackupScreen) {
        BackupRestoreScreen(
            viewModel = viewModel,
            onBack = { showBackupScreen = false },
            onExportStarted = onExportStarted,
            onExportFinished = onExportFinished
        )
    }
}

/* ---------------- TOP BAR ---------------- */

@Composable
private fun DiaryTopBar(
    saveState: DiaryViewModel.SaveState,
    showSaveState: Boolean,
    showEdit: Boolean,
    showMenu: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onReminder: () -> Unit,
    onWeekly: () -> Unit
) {
    val barText = MaterialTheme.colorScheme.onPrimaryContainer
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = barText,
            actionIconContentColor = barText,
            navigationIconContentColor = barText
        ),
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.height(48.dp)
                )
            }
        },
        title = {},
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }

            if (showSaveState) {
                val status = when (saveState) {
                    DiaryViewModel.SaveState.DIRTY -> "Typing…"
                    DiaryViewModel.SaveState.SAVING -> "Saving…"
                    DiaryViewModel.SaveState.SAVED -> "Saved"
                    DiaryViewModel.SaveState.IDLE -> ""
                }
                if (status.isNotEmpty()) {
                    Text(
                        status,
                        fontSize = 12.sp,
                        color = barText.copy(alpha = if (saveState == DiaryViewModel.SaveState.SAVED) 1f else 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            if (showEdit) {
                TextButton(onClick = onEdit) {
                    Text("Edit", color = barText, fontWeight = FontWeight.Bold)
                }
            }

            Box {
                IconButton(onClick = { onMenu(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onMenu(false) }) {
                    DropdownMenuItem(text = { Text("This week") }, onClick = onWeekly)
                    DropdownMenuItem(text = { Text("Daily reminder") }, onClick = onReminder)
                    DropdownMenuItem(text = { Text("Backup & Restore") }, onClick = onBackup)
                    DropdownMenuItem(text = { Text("Settings") }, onClick = onSettings)
                }
            }
        }
    )
}

/* ---------------- PAGES ---------------- */

/** One sheet of paper on the desk; slides, fades and shrinks a little while swiping. */
@Composable
private fun PaperPage(pagerState: PagerState, page: Int, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, 1f)
                alpha = 1f - 0.25f * offset
                val scale = 1f - 0.05f * offset
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

@Composable
private fun ruledLineColor(): Color =
    if (LocalDarkTheme.current) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

private val DIARY_LINE_HEIGHT = 28.sp
private val PAGE_PADDING = 12.dp

@Composable
private fun diaryTextStyle(fontSize: TextUnit = LocalDiaryTextSize.current) = LocalTextStyle.current.copy(
    fontFamily = DiaryHandwritingFont,
    color = MaterialTheme.colorScheme.onBackground,
    fontSize = fontSize,
    lineHeight = DIARY_LINE_HEIGHT * (fontSize.value / 18f),
    letterSpacing = 0.3.sp
)

/** Ruled lines spaced exactly one text line apart, so writing sits on the lines. */
@Composable
private fun Modifier.diaryLines(): Modifier {
    val lineHeight = diaryTextStyle().lineHeight
    val spacing = with(LocalDensity.current) { lineHeight.toDp() }
    return ruledBackground(
        lineColor = ruledLineColor(),
        lineSpacing = spacing,
        topOffset = PAGE_PADDING,
        strokeWidth = if (LocalDarkTheme.current) 0.8f else 1.2f
    )
}

@Composable
private fun NeighbourPage(date: LocalDate, viewModel: DiaryViewModel) {
    val text by produceState("", date) { value = viewModel.textFor(date) }
    if (text.isBlank()) {
        EmptyDayPlaceholder(modifier = Modifier.fillMaxSize())
    } else {
        Text(
            text = text,
            style = diaryTextStyle(),
            modifier = Modifier
                .fillMaxSize()
                .diaryLines()
                .padding(PAGE_PADDING)
        )
    }
}

@Composable
private fun PreviewPage(viewModel: DiaryViewModel, onOpenPhoto: (com.aburv.kurippidu.data.AttachmentEntity) -> Unit) {
    val todos = viewModel.todos ?: emptyList()
    val mood = viewModel.currentMood
    val photos = viewModel.attachments
    if (todos.isEmpty() && viewModel.diaryText.text.isBlank() && mood == null && photos.isEmpty()) {
        EmptyDayPlaceholder(modifier = Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .diaryLines()
            .padding(PAGE_PADDING)
    ) {
        mood?.let {
            Text("${it.emoji}  Feeling ${it.label.lowercase()}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }
        // Read-only tasks
        todos.forEach { todo -> TaskRow(todo = todo, onToggle = null, onDelete = null) }
        if (todos.isNotEmpty()) Spacer(Modifier.height(8.dp))

        PhotoStrip(photos = photos, fileOf = viewModel::photoFile, onOpen = onOpenPhoto)

        RenderedText(viewModel.diaryText.text, onToggleChecklist = viewModel::toggleChecklistLine)

        viewModel.lastEdited?.let { edited ->
            Spacer(Modifier.height(16.dp))
            Text(
                "Last edited ${formatEdited(edited)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Diary text for reading: formatting shown without its markers, "☐ / ☑" lines
 * as checkboxes you can tick. Plain lines are grouped so they stay on the ruling.
 */
@Composable
private fun RenderedText(text: String, onToggleChecklist: (Int) -> Unit) {
    val style = diaryTextStyle()
    val textColor = MaterialTheme.colorScheme.onBackground
    val tagColor = MaterialTheme.colorScheme.primary
    val lines = text.split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val checked = line.startsWith(CHECKED)
        if (checked || line.startsWith(UNCHECKED)) {
            val index = i
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { onToggleChecklist(index) })
                Text(
                    renderMarkdown(line.drop(2), textColor, tagColor),
                    style = style,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = textColor.copy(alpha = if (checked) 0.6f else 1f)
                )
            }
            i++
        } else {
            val start = i
            while (i < lines.size && !lines[i].startsWith(CHECKED) && !lines[i].startsWith(UNCHECKED)) i++
            Text(renderMarkdown(lines.subList(start, i).joinToString("\n"), textColor, tagColor), style = style)
        }
    }
}

@Composable
private fun EditorPage(
    viewModel: DiaryViewModel,
    date: LocalDate,
    onAddTodo: () -> Unit,
    onAddRecurring: () -> Unit,
    onTemplates: () -> Unit,
    onManageRecurring: () -> Unit,
    onAddPhoto: () -> Unit,
    onVoice: () -> Unit,
    onOpenPhoto: (com.aburv.kurippidu.data.AttachmentEntity) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        EditorToolbar(
            enabled = true,
            onAddType = onTemplates,
            onFormat = { viewModel.applyFormat(it) },
            onAddTodoClick = onAddTodo,
            onAddRecurringClick = onAddRecurring,
            onManageRecurringClick = onManageRecurring,
            onChecklist = viewModel::toggleChecklistAtCursor,
            onAddPhoto = onAddPhoto,
            onVoice = onVoice
        )

        // Everything below the toolbar scrolls as one page, so with the keyboard
        // open the whole space above it is for writing
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageHeight = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Info row: word count, last edit, recurring tasks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_PADDING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val words = remember(viewModel.diaryText.text) { wordCount(viewModel.diaryText.text) }
                    val info = buildString {
                        append(if (words == 1) "1 word" else "$words words")
                        viewModel.lastEdited?.let { append(" · Last edited ${formatEdited(it)}") }
                    }
                    Text(
                        info,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.recurringTodos.isNotEmpty()) {
                        TextButton(onClick = onManageRecurring) {
                            Text(
                                "Manage recurring tasks (${viewModel.recurringTodos.size})",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                MoodRow(
                    selected = viewModel.currentMood,
                    onSelect = viewModel::setMood,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))

                BasicTextField(
                    visualTransformation = MarkdownVisualTransformation(textColor, MaterialTheme.colorScheme.primary),
                    value = viewModel.diaryText,
                    onValueChange = viewModel::updateDiaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        // At least a full page, so tapping the empty lines still starts typing
                        .heightIn(min = pageHeight)
                        .navigationBarsPadding()
                        .pointerInput(Unit) { detectTapGestures(onTap = { keyboardController?.show() }) },
                    textStyle = diaryTextStyle(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = pageHeight)
                                .diaryLines()
                                .padding(PAGE_PADDING)
                        ) {
                            val todos = viewModel.todos.orEmpty()
                            if (todos.isNotEmpty()) {
                                Text(
                                    text = "Tasks",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            todos.forEach { todo ->
                                TaskRow(
                                    todo = todo,
                                    onToggle = { viewModel.toggleTodo(todo, date) },
                                    // Daily tasks are deleted here; recurring ones from "Manage recurring tasks"
                                    onDelete = if (todo.type == TodoType.DAILY) ({ viewModel.deleteTodo(todo) }) else null
                                )
                            }
                            if (todos.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            PhotoStrip(photos = viewModel.attachments, fileOf = viewModel::photoFile, onOpen = onOpenPhoto)
                            inner()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskRow(todo: TodoEntity, onToggle: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = todo.isDone,
            onCheckedChange = onToggle?.let { { _: Boolean -> it() } }
        )
        Text(
            text = todo.title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            color = textColor.copy(alpha = if (todo.isDone) 0.5f else 1f),
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete task", tint = textColor.copy(alpha = 0.5f))
            }
        }
    }
}

/* ---------------- HELPERS ---------------- */

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

private fun dateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "Today (${date.format(shortDate)})" else date.format(shortDate)

internal fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

/** "9:32 PM" for today, otherwise "25 Oct, 9:32 PM". */
internal fun formatEdited(millis: Long, now: Long = System.currentTimeMillis()): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return if (day.format(Date(millis)) == day.format(Date(now))) time
    else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis)) + ", " + time
}
