@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aburv.kurippidu.CalendarMonthView
import com.aburv.kurippidu.DiaryViewModel.BackupEvent
import com.aburv.kurippidu.R
import com.aburv.kurippidu.data.TodoEntity
import com.aburv.kurippidu.data.TodoType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    diaryId: Int,
    onExportStarted: () -> Unit,
    onExportFinished: () -> Unit,
    onBack: () -> Unit,
    lockMode: LockMode,
    themeMode: ThemeMode

) {
    val diaryViewModel: DiaryViewModel = viewModel()

    // Save pending edits as soon as the app goes to the background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                diaryViewModel.saveCurrentDiary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

// This must fire first — gates all DB access behind the correct diary ID
    LaunchedEffect(diaryId) {
        diaryViewModel.setActiveDiary(diaryId)
    }

    /* ---------------- Restore Backup---------------- */

    val editorScrollState = rememberScrollState()

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }
    var showBackupScreen by remember { mutableStateOf(false) }
    var isRestoreInProgress by rememberSaveable { mutableStateOf(false) }

    /* ---------------- Back-Up ---------------- */

    var showExportMenu by remember { mutableStateOf(false) }

    /* ---------------- SNACKBAR ---------------- */

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    /* ---------------- DATE STATE ---------------- */

    val iconColor = MaterialTheme.colorScheme.onSurface

    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    var pageStack by remember {
        mutableStateOf<Map<Int, DiaryViewModel.DiaryPageState>>(emptyMap())
    }

    LaunchedEffect(Unit) {
        BackupPreferences.lastOpenedDate(context, diaryId).collect { saved ->
            if (selectedDate == null) {
                val resolvedDate =
                    saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: LocalDate.now()

                selectedDate = resolvedDate
            }
        }
    }

    val currentDate = selectedDate ?: LocalDate.now()

    var showCalendarPicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
    val dateText = if (currentDate == LocalDate.now()) {
        "Today (${currentDate.format(dateFormatter)})"
    } else {
        currentDate.format(dateFormatter)
    }

    val isToday = currentDate == LocalDate.now()
    val showEditor = isToday || diaryViewModel.isEditMode

    val isEmptyEntry = diaryViewModel.diaryText.text.isBlank()
    val todosEmpty = diaryViewModel.todos?.isEmpty() ?: true
    val showPlaceholder = !showEditor && isEmptyEntry && todosEmpty

    /*-------------Dairy Template-------------- */

    val templates = listOf(
        DiaryTemplate("Workout", "🏋️"),
        DiaryTemplate("Food", "🍔"),
        DiaryTemplate("Spent", "💸")
    )

    /* ---------------- DIARY TABS STATE ---------------- */

// ✅ LOAD diary WHEN date changes

    LaunchedEffect(selectedDate) {
        selectedDate?.let { date ->

            diaryViewModel.saveCurrentDiary()

            diaryViewModel.loadDiaryForDate(date)

            pageStack = diaryViewModel.preloadPages(date)

            diaryViewModel.updateEditModeForDate(date)

            BackupPreferences.setLastOpenedDate(context, diaryId, date.toString())

            diaryViewModel.loadTodos(date)
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    val previewScrollState = rememberScrollState()

    /* ---------------- TOOLBAR STATE ---------------- */

    var showAddTodoDialog by remember { mutableStateOf(false) }
    var newTodoText by remember { mutableStateOf("") }

    var showTypeDialog by remember { mutableStateOf(false) }

    var showRecurringTaskDialog by remember { mutableStateOf(false) }

    var showRecurringManager by remember { mutableStateOf(false) }

    /* ---------------- ANIMATION ---------------- */
    var isAnimating by remember { mutableStateOf(false) }

    val slideOffsetX = remember { Animatable(0f) }
//    val screenWidth = 400f // safe constant for now

    val screenWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    fun animateToDate(newDate: LocalDate, direction: Int) {
        if (isAnimating) return  // 🚫 block re-entry

        scope.launch {
            isAnimating = true

            // slide current page out
            slideOffsetX.animateTo(direction * screenWidth)

            // change date AFTER slide-out
            selectedDate = newDate

            // jump content to opposite side (invisible)
            slideOffsetX.snapTo(-direction * screenWidth)

            // slide new page in
            slideOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            )
            withFrameNanos { }
            isAnimating = false
        }
    }

    // Normalize swipe progress (0f → 1f)
    val swipeProgress by remember {
        derivedStateOf {
            (kotlin.math.abs(slideOffsetX.value) / screenWidth)
                .coerceIn(0f, 1f)
        }
    }

// Fade slightly while swiping
    val pageAlpha by remember {
        derivedStateOf {
            1f - (swipeProgress * 0.12f) // very subtle
        }
    }

// Elevation for paper shadow
    val pageElevation by remember {
        derivedStateOf {
            8.dp * swipeProgress
        }
    }

    val shadowOffsetX by remember {
        derivedStateOf {
            if (slideOffsetX.value > 0) -6f else 6f
        }
    }

    /* ---------------- theme Rulled line for unruled background ---------------- */

    val isDark = isSystemInDarkTheme()

    val ruledLineColor = if (isDark) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }

    val ruledLineThickness = if (isDark) 0.8f else 1.2f

    val cursorColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val pageShape = MaterialTheme.shapes.medium

    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    /* ---------------- UI ---------------- */

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },

        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars) // ✅ ONLY HERE
            )
            TopAppBar(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),

                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.height(80.dp)
                        )
                    }
                },

                title = {
                },

                actions = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // ✅ SAVE INDICATOR — ONLY IN EDIT MODE
                    if (showEditor) {
                        when (diaryViewModel.saveState) {
                            DiaryViewModel.SaveState.SAVING -> {
                                Text(
                                    text = "Saving…",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                            DiaryViewModel.SaveState.SAVED -> {
                                Text(
                                    text = "Saved",
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }

                            DiaryViewModel.SaveState.DIRTY,
                            DiaryViewModel.SaveState.IDLE -> {
                                // no UI
                            }
                        }
                    }
                    // ✅ EDIT BUTTON — ONLY IN PREVIEW MODE
                    if (!isToday && !diaryViewModel.isEditMode) {
                        Text(
                            text = "Edit",
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { diaryViewModel.enableEditMode() },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                                modifier = Modifier
                                .width(220.dp)

                    ) {
                        AppLockToggle(
                            currentMode = lockMode,
                            onEnableRequested = {
                                // Confirm it's the owner before turning the lock on
                                (context as? FragmentActivity)?.let { activity ->
                                    AppLockState.authenticate(
                                        activity,
                                        {
                                            scope.launch {
                                                BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)
                                                snackbarHostState.showSnackbar("App lock enabled")
                                            }
                                        },
                                        {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Authentication failed")
                                            }
                                        }
                                    )
                                }
                            },
                            onDisableRequested = {
                                scope.launch {
                                    BackupPreferences.setLockMode(context, LockMode.OFF)
                                    snackbarHostState.showSnackbar("App lock disabled")
                                }
                            }
                        )

                        Divider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (themeMode == ThemeMode.DARK)
                                        "Switch to Light Mode"
                                    else
                                        "Switch to Dark Mode"
                                )
                            },
                            onClick = {
                                scope.launch {
                                    val newMode =
                                        if (themeMode == ThemeMode.DARK)
                                            ThemeMode.LIGHT
                                        else
                                            ThemeMode.DARK

                                    BackupPreferences.setThemeMode(context, newMode)
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Backup & Restore") },
                            onClick = {
                                showExportMenu = false
                                showBackupScreen = true
                                isRestoreInProgress = true   // ⭐ ADD THIS
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->

        // ✅ TODO SECTION

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        )

        {
            DateNavigationRow(
                dateText = dateText,
                color = iconColor,
                onMoveDays = { days ->
                    // Going back slides the page right (+1), forward slides it left (-1)
                    animateToDate(currentDate.plusDays(days.toLong()), if (days < 0) 1 else -1)
                },
                onDateClick = { showCalendarPicker = true },
                onTodayClick = {
                    val today = LocalDate.now()
                    if (currentDate != today) {
                        val direction = if (currentDate.isBefore(today)) -1 else 1
                        animateToDate(today, direction)
                    }
                }
            )

            Divider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 2.dp
            )

// --- Editor Toolbar ---

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(selectedDate, showEditor,isAnimating) {
                        if (!showEditor && !isAnimating) {
                            var totalDrag = 0f

                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                    val resistanceFactor = 0.75f
                                    val resistedDrag = totalDrag * resistanceFactor

                                    scope.launch {
                                        slideOffsetX.snapTo(resistedDrag)
                                    }
                                },
                                onDragEnd = {
                                    val threshold = screenWidth * 0.18f

                                    when {
                                        totalDrag < -threshold ->
                                            animateToDate(
                                                currentDate.plusDays(1), -1
                                            )

                                        totalDrag > threshold ->
                                            animateToDate(
                                                currentDate.minusDays(1), +1
                                            )

                                        else ->
                                            scope.launch {
                                                slideOffsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(

                                                        stiffness = 350f,              // heavier feel
                                                        dampingRatio = 0.9f            // no bounce

//                                                        stiffness = Spring.StiffnessMediumLow,
//                                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                                    )
                                                )
                                            }
                                    }
                                    totalDrag = 0f
                                },

                            )
                        }
                    }

            )

            {
                // ---------- PAGE STACK (Google Photos style) ----------
                Box(modifier = Modifier.fillMaxSize()) {
                    // ◀ PREVIOUS DAY (already loaded, blurred)
                    pageStack[-1]?.let { page ->
                        DiaryPage(
                            text = page.text,
                            todos = emptyList(),
                            ruledLineColor = ruledLineColor,
                            ruledLineThickness = ruledLineThickness,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = slideOffsetX.value - screenWidth
                                    alpha = 0.6f
                                }
                                .blur(8.dp),

                            onDeleteTodo = { diaryViewModel.deleteTodo(it) }

                        )
                    }

                    // ⬛ CURRENT DAY (sharp, interactive)
                    pageStack[0]?.let { page ->

                        if (showEditor) {
                            // ✅ EDITOR — NO animation, NO graphicsLayer
                            Column(
                                modifier = Modifier.fillMaxSize()
                                    .fillMaxSize()
                                    .imePadding()
                            ) {
                                EditorToolbar(
                                    enabled = true,
                                    onAddType = { showTypeDialog = true },
                                    onFormat = { diaryViewModel.applyFormat(it) },
                                    onAddTodoClick = { showAddTodoDialog = true },
                                    onAddRecurringClick = { showRecurringTaskDialog = true },
                                    onManageRecurringClick = { showRecurringManager = true }
                                )

                                Divider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)

                                val keyboardController = LocalSoftwareKeyboardController.current
                                val focusManager = LocalFocusManager.current

                                BasicTextField(
                                    visualTransformation = MarkdownVisualTransformation(onBackgroundColor),
                                    value = diaryViewModel.diaryText,
                                    onValueChange = diaryViewModel::updateDiaryText,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(editorScrollState) // ✅ SAFE
                                        .imePadding()
                                        .navigationBarsPadding()
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = {
                                                keyboardController?.show()
                                            })
                                        },

                                    textStyle = LocalTextStyle.current.copy(
                                        fontFamily = DiaryHandwritingFont,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 18.sp,
                                        lineHeight = 28.sp,
                                        letterSpacing = 0.3.sp
                                    ),

                                    cursorBrush = SolidColor(cursorColor),
                                    decorationBox = { inner ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background)
                                                .ruledBackground(
                                                    lineColor = ruledLineColor,
                                                    strokeWidth = ruledLineThickness
                                                )
                                                .padding(12.dp)
                                        ) {
                                            if (diaryViewModel.todos?.isNotEmpty() == true) {
                                                Text(
                                                    text = "Tasks",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }

                                            // ✅ TODOS INSIDE PAGE
                                            diaryViewModel.todos?.forEach { todo ->

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = todo.isDone,
                                                        onCheckedChange = {
                                                            diaryViewModel.toggleTodo(todo, currentDate)
                                                        }
                                                    )

                                                    Text(
                                                        text = todo.title,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(start = 8.dp),
                                                        textDecoration = if (todo.isDone)
                                                            TextDecoration.LineThrough
                                                        else null
                                                    )

                                                    // Daily tasks are deleted here; recurring ones
                                                    // from "Manage recurring tasks"
                                                    if (todo.type == TodoType.DAILY) {
                                                        IconButton(onClick = { diaryViewModel.deleteTodo(todo) }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Delete task",
                                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // ✅ ACTUAL DIARY TEXT
                                            inner()
                                        }
                                    }
                                )
                            }
                            if ((diaryViewModel.recurringTodos.isNotEmpty())) {
                                TextButton(
                                    onClick = { showRecurringManager = true },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "Manage recurring tasks (${diaryViewModel.recurringTodos.size})",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // ✅ PREVIEW — animated
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .ruledBackground(
                                        lineColor = ruledLineColor,
                                        strokeWidth = ruledLineThickness
                                    )

                                    .graphicsLayer {
                                        translationX = slideOffsetX.value

                                        // 📄 Slight page tilt (realism boost)
                                        rotationY = (slideOffsetX.value / screenWidth) * 4f

                                        // Subtle scale illusion
                                        val progress = kotlin.math.abs(slideOffsetX.value) / screenWidth
                                        val scale = 1f - (progress * 0.03f)
                                        scaleX = scale
                                        scaleY = scale

                                        alpha = pageAlpha

                                        cameraDistance = 12f * density
                                    }

                            ) {
//                                var todoToDelete by remember { mutableStateOf<TodoEntity?>(null) }

                                val todos = diaryViewModel.todos ?: emptyList()

                                if (todos.isEmpty() && diaryViewModel.diaryText.text.isBlank()) {
                                    EmptyDayPlaceholder(modifier = Modifier.fillMaxSize())
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(previewScrollState)
                                            .padding(12.dp)
                                    ) {
                                        // ✅ READ-ONLY todos — no checkbox interaction, no delete, no long-press
                                        todos.forEach { todo ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = todo.isDone,
                                                    onCheckedChange = null  // ✅ completely disabled — read only
                                                )
                                                Text(
                                                    text = todo.title,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(start = 8.dp),
                                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = if (todo.isDone) 0.5f else 1f
                                                    ),
                                                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        // Diary text — read only
                                        Text(
                                            text = diaryViewModel.diaryText.text,
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
                        }
                    }

                    // ▶ NEXT DAY (already loaded, blurred)
                    pageStack[1]?.let { page ->
                        DiaryPage(
                            text = page.text,
                            todos = emptyList(),
                            ruledLineColor = ruledLineColor,
                            ruledLineThickness = ruledLineThickness,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = slideOffsetX.value + screenWidth
                                    alpha = 0.6f
                                }
                                .blur(8.dp),
                            onDeleteTodo = { diaryViewModel.deleteTodo(it) }
                        )
                    }
                }
                }

            /* -------- TYPE PICKER -------- */

            if (showTypeDialog) {
                ModalBottomSheet(onDismissRequest = { showTypeDialog = false }) {
                    listOf("Food", "Workout", "Spent").forEach { type ->
                        ListItem(
                            headlineContent = { Text(type) },
                            modifier = Modifier.clickable {
                                showTypeDialog = false
                                // insertion logic next step

                                val template = templates.firstOrNull { it.label == type }
                                template?.let {
                                    diaryViewModel.insertTemplate(it)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddTodoDialog) {
            AlertDialog(
                onDismissRequest = { showAddTodoDialog = false },
                title = { Text("Add Task") },
                text = {
                    TextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        placeholder = { Text("Enter your task") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newTodoText.isNotBlank()) {
                                diaryViewModel.addTodo(newTodoText, currentDate)
                                newTodoText = ""
                                showAddTodoDialog = false
                            }
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddTodoDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRecurringTaskDialog) {
            RecurringTaskDialog(
                onDismiss = { showRecurringTaskDialog = false },
                onConfirm = { taskTitle, start, end ->
                    diaryViewModel.addRecurringTodo(taskTitle, start, end)
                    showRecurringTaskDialog = false
                }
            )
        }
        if (showRecurringManager) {
            RecurringTaskManagerSheet(
                viewModel = diaryViewModel,
                onDismiss = { showRecurringManager = false }
            )
        }
    }
    LaunchedEffect(Unit) {
        diaryViewModel.reinitializeIfEmpty()
        diaryViewModel.backupEvents.collect { event ->
            val message = when (event) {
                is BackupEvent.Success -> event.message
                is BackupEvent.Error -> event.message
            }

            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    if (showCalendarPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCalendarPicker = false }
        ) {
            CalendarMonthView(
                currentMonth = YearMonth.from(currentDate),
                entryDates = diaryViewModel.entryDates.toList(),
                onDateClick = { date ->
                    selectedDate = date
                    showCalendarPicker = false
                }
            )
        }
    }

    // 🔍 SEARCH SHEET
    if (showSearch) {
        SearchSheet(
            onClose = {
                showSearch = false
                diaryViewModel.clearSearch()
            },
            onResultClick = { date ->
                showSearch = false
                animateToDate(date, 0)
            }
        )
    }

    // Drawn last so it covers the whole diary screen (not inside the top bar)
    if (showBackupScreen) {
        BackupRestoreScreen(
            viewModel = diaryViewModel,
            onBack = {
                showBackupScreen = false
                isRestoreInProgress = false
            },
            onExportStarted = {
                isRestoreInProgress = true
                onExportStarted()
            },
            onExportFinished = {
                isRestoreInProgress = false
                onExportFinished()
            }
        )
    }
 }
