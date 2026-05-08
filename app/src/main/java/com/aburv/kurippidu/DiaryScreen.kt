@file:OptIn(ExperimentalMaterial3Api::class)




package com.aburv.kurippidu

import com.aburv.kurippidu.CalendarMonthView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.DateRange
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.saveable.rememberSaveable
import com.aburv.kurippidu.DiaryViewModel.BackupEvent
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.aburv.kurippidu.R
import java.time.YearMonth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController


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



// This must fire first — gates all DB access behind the correct diary ID
    LaunchedEffect(diaryId) {
        diaryViewModel.setActiveDiary(diaryId)
        diaryViewModel.loadRecurringTodos()
    }


    /* ---------------- Calendar ---------------- */


    LaunchedEffect(diaryId) {
        diaryViewModel.loadEntryDates()
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
                                val activity = context as FragmentActivity

                                BiometricAuthHelper(
                                    activity = activity,
                                    onSuccess = {
                                        scope.launch {
                                            BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)
                                            snackbarHostState.showSnackbar("App lock enabled")
                                        }
                                    },
                                    onError = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Authentication failed")
                                        }
                                    }
                                ).authenticate()
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
            if (showBackupScreen) {
                BackupRestoreScreen(
                    viewModel = diaryViewModel,
                    onBack = { showBackupScreen = false
                        isRestoreInProgress = false  // ⭐ ADD THIS

                    },
                    onExportStarted = {
                        isRestoreInProgress = true  // ⭐ ADD THIS
                        onExportStarted()


                    },
                    onExportFinished = {
                        isRestoreInProgress = false  // ⭐ ADD THIS
                        onExportFinished()

                    }
                )
            }
        }
    ) { padding ->

        // ✅ TODO SECTION


        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        )


        {

//            // ✅ ADD TODO INPUT
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(8.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//
//                TextField(
//                    value = newTodoText,
//                    onValueChange = { newTodoText = it },
//                    modifier = Modifier.weight(1f),
//                    placeholder = { Text("Add a task...") }
//                )
//
//                Spacer(modifier = Modifier.width(8.dp))
//
//                IconButton(
//                    onClick = {
//                        if (newTodoText.isNotBlank()) {
//                            diaryViewModel.addTodo(newTodoText, currentDate)
//                            newTodoText = ""
//                        }
//                    }
//                ) {
//                    Icon(Icons.Default.Add, contentDescription = "Add Todo")
//                }
//            }




            /* -------- DATE ROW (below blue bar) -------- */

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically

            ) {
                Spacer(Modifier.weight(1f))

                Text("<<",Modifier.clickable { animateToDate(currentDate.plusDays(-2), +1) },color = iconColor)
                Spacer(Modifier.width(12.dp))
                Text("<", Modifier.clickable { animateToDate(currentDate.plusDays(-1), +1) },color = iconColor)

                Spacer(Modifier.width(24.dp))

                Text(
                    text = dateText,
                    modifier = Modifier.clickable { showCalendarPicker = true },
                    style = MaterialTheme.typography.titleMedium,
                    color = iconColor
                )

                Spacer(Modifier.width(24.dp))

                Text(">", Modifier.clickable { animateToDate(currentDate.plusDays(1)
                    , -1) },color = iconColor)
                Spacer(Modifier.width(12.dp))
                Text(">>", Modifier.clickable { animateToDate(currentDate.plusDays(2)
                    , -1) },color = iconColor)

                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Go to Today",

                    modifier = Modifier.clickable {
                        val today = LocalDate.now()
                        if (currentDate != today) {
                            // ✅ direction: if we're in the past, today is in the future → swipe left (-1)
                            //               if we're in the future, today is in the past → swipe right (+1)
                            val direction = if (currentDate.isBefore(today)) -1 else 1
                            animateToDate(today, direction)
                        }
                    },

                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(24.dp))

            }


            Divider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )


            Divider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
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
//                                onDragCancel = {
//                                    scope.launch { slideOffsetX.animateTo(0f) }
//                                    totalDrag = 0f
//                                }

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
                                                        modifier = Modifier.padding(start = 8.dp),
                                                        textDecoration = if (todo.isDone)
                                                            TextDecoration.LineThrough
                                                        else null
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // ✅ ACTUAL DIARY TEXT
                                            inner()
                                        }
                                    }
                                )
                            }
                            // ADD this right after EditorToolbar(...) call, inside the editor Column:
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

                                // REPLACE the todos forEach inside decorationBox:
//                                var todoToDelete by remember { mutableStateOf<TodoEntity?>(null) }

//                                diaryViewModel.todos?.forEach { todo ->
//                                    var showDeleteOption by remember(todo.id) { mutableStateOf(false) }
//
//                                    Row(
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .padding(vertical = 4.dp),
//                                        verticalAlignment = Alignment.CenterVertically
//                                    ) {
//                                        Checkbox(
//                                            checked = todo.isDone,
//                                            onCheckedChange = { diaryViewModel.toggleTodo(todo, currentDate) }
//                                        )
//
//                                        Text(
//                                            text = todo.title,
//                                            modifier = Modifier
//                                                .weight(1f)
//                                                .padding(start = 8.dp)
//                                                .pointerInput(todo.id) {
//                                                    detectTapGestures(
//                                                        onLongPress = { showDeleteOption = true }
//                                                    )
//                                                },
//                                            textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
//                                        )
//
//                                        if (showDeleteOption) {
//                                            TextButton(
//                                                onClick = {
//                                                    diaryViewModel.deleteTodo(todo)
//                                                    showDeleteOption = false
//                                                }
//                                            ) {
//                                                Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
//                                            }
//                                        }
//                                    }
//                                }

                                // In DiaryScreen.kt — PREVIEW mode Box (the else branch)
// REPLACE the existing diaryViewModel.todos?.forEach { ... } block with this:

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


//
//                                val currentText = diaryViewModel.diaryText.text
//                                val todosLoaded = diaryViewModel.todos  // null = still loading
//
//                                when {
//                                    todosLoaded == null -> {
//                                        // Loading state — show blank to avoid flicker
//                                        Box(
//                                            modifier = Modifier
//                                                .fillMaxSize()
//                                                .ruledBackground(lineColor = ruledLineColor, strokeWidth = ruledLineThickness)
//                                        )
//                                    }
//                                    currentText.isBlank() && todosLoaded.isEmpty() -> {
//                                        EmptyDayPlaceholder(
//                                            modifier = Modifier
//                                                .fillMaxSize()
//                                                .ruledBackground(lineColor = ruledLineColor, strokeWidth = ruledLineThickness)
//                                        )
//                                    }
//                                    else -> {
//                                        Column(
//                                            modifier = Modifier
//                                                .fillMaxSize()
//                                                .verticalScroll(previewScrollState)
//                                        ) {
//                                            DiaryPage(
//                                                text = currentText,
//                                                todos = todosLoaded,
//                                                ruledLineColor = ruledLineColor,
//                                                ruledLineThickness = ruledLineThickness,
//                                                modifier = Modifier.fillMaxSize()
//                                            )
//                                        }
//                                    }
//                                }



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




 }




@Composable
fun MonthYearPicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val months = listOf(
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    )

    var selectedYear by remember { mutableStateOf(initialDate.year) }
    var selectedMonth by remember { mutableStateOf(initialDate.monthValue - 1) }




    ModalBottomSheet(onDismissRequest = onDismiss) {

        // YEAR SELECTOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedYear-- }) {
                Text("−", fontSize = 24.sp)
            }

            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            IconButton(onClick = { selectedYear++ }) {
                Text("+", fontSize = 24.sp)
            }
        }

        Divider(color = MaterialTheme.colorScheme.primary)

        // MONTH GRID
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            months.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEachIndexed { colIndex, month ->
                        val index = rowIndex * 3 + colIndex
                        val isSelected = index == selectedMonth
                        Text(
                            text = month,
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable {
                                    selectedMonth = index
                                    onDateSelected(
                                        LocalDate.of(
                                            selectedYear,
                                            selectedMonth + 1,
                                            minOf(
                                                initialDate.dayOfMonth,
                                                LocalDate
                                                    .of(
                                                        selectedYear,
                                                        selectedMonth + 1,
                                                        1
                                                    )
                                                    .lengthOfMonth()
                                            )
                                        )
                                    )
                                },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onBackground ,
                            fontWeight = if (isSelected)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorToolbar(
    enabled: Boolean,
    onAddType: () -> Unit,
    onFormat: (TextFormat) -> Unit,
    onAddTodoClick: () -> Unit,
    onAddRecurringClick: () -> Unit,
    onManageRecurringClick: () -> Unit,

) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
//        IconButton(onClick = { onFormat(TextFormat.Bold) }) {
//            Text("B", fontWeight = FontWeight.Bold)
//        }
//        IconButton(onClick = { onFormat(TextFormat.Italic) }) {
//            Text("I", fontStyle = FontStyle.Italic)
//        }
//        IconButton(onClick = { onFormat(TextFormat.Underline) }) {
//            Text("U", textDecoration = TextDecoration.Underline)
//        }
//
//
//        Spacer(Modifier.weight(1f))
//
//        IconButton(onClick = onAddType) {
//            Icon(
//                painter = painterResource(id = R.drawable.baseline_attach_file_24),
//                contentDescription = "Attach"
//            )
//        }


//
//        IconButton(onClick = { onFormat(TextFormat.Bold) }) {
//            Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
//        }
//        IconButton(onClick = { onFormat(TextFormat.Italic) }) {
//            Text("I", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onBackground)
//        }
//        IconButton(onClick = { onFormat(TextFormat.Underline) }) {
//            Text("U", textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.onBackground)
//        }

        Spacer(Modifier.weight(1f))


        // ✅ ADD BUTTON
        IconButton(
            onClick = onAddTodoClick
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = "Add Todo"
            )
        }


        Spacer(Modifier.width(8.dp))


//        IconButton(onClick = onAddRecurringClick) {
//            Icon(
//                imageVector = Icons.Default.DateRange,
//                contentDescription = "Add Recurring Task",
//                tint = MaterialTheme.colorScheme.onBackground
//            )
//        }

        Box {
            IconButton(onClick = onAddRecurringClick) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Add Recurring Task",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }


        Spacer(Modifier.width(8.dp))


        IconButton(onClick = onAddType) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}

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
private fun DiaryPage(
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

            // ✅ TODOS (VISIBLE IN VIEW MODE ALSO)
//            todos.forEach { todo ->
//
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(vertical = 4.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//
//                    Checkbox(
//                        checked = todo.isDone,
//                        onCheckedChange = null // 👈 READ ONLY in view mode
//                    )
//
//                    Text(
//                        text = todo.title,
//                        modifier = Modifier.padding(start = 8.dp),
//                        textDecoration = if (todo.isDone)
//                            TextDecoration.LineThrough
//                        else null
//                    )
//                }
//            }


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


@Composable
fun AutoSaveIndicator(state: DiaryViewModel.SaveState) {
    val text = when (state) {
        DiaryViewModel.SaveState.DIRTY -> "Typing…"
        DiaryViewModel.SaveState.SAVING -> "Saving…"
        DiaryViewModel.SaveState.SAVED -> "Saved"
        DiaryViewModel.SaveState.IDLE -> ""
    }

    val color = when (state) {
        DiaryViewModel.SaveState.DIRTY -> MaterialTheme.colorScheme.onSurfaceVariant
        DiaryViewModel.SaveState.SAVING -> MaterialTheme.colorScheme.secondary
        DiaryViewModel.SaveState.SAVED -> MaterialTheme.colorScheme.primary
        DiaryViewModel.SaveState.IDLE -> Color.Transparent
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
fun SearchSheet(
    onClose: () -> Unit,
    onResultClick: (LocalDate) -> Unit,
    viewModel: DiaryViewModel = viewModel()
) {
    ModalBottomSheet(onDismissRequest = onClose) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground

                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search field
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by date or content…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(Modifier.height(12.dp))


            val resultDateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

            if (viewModel.searchQuery.isNotBlank() && viewModel.searchResults.isEmpty()) {
                Text(
                    text = "No results",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn {
                    items(viewModel.searchResults) { diary ->
                        ListItem(
                            headlineContent = {
                                val formatted = runCatching {
                                    LocalDate.parse(diary.date).format(resultDateFormatter)
                                }.getOrDefault(diary.date)
                                Text(formatted)
                            },



                            supportingContent = {
                                Text(
                                    diary.content,
                                    maxLines = 2
                                )
                            },
                            modifier = Modifier.clickable {
                                onResultClick(LocalDate.parse(diary.date))
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }


}




