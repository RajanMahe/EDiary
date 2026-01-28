@file:OptIn(ExperimentalMaterial3Api::class)




package com.example.diary

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.example.diary.DiaryViewModel.BackupEvent
import com.example.diary.LockMode
import androidx.fragment.app.FragmentActivity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource








@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onExportStarted: () -> Unit,
    onExportFinished: () -> Unit,
    lockMode: LockMode

) {




    val diaryViewModel: DiaryViewModel = viewModel()

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

    LaunchedEffect(Unit) {
        BackupPreferences.lastOpenedDate(context).collect { saved ->
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
    val showPlaceholder = !showEditor && isEmptyEntry




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
            diaryViewModel.updateEditModeForDate(date)

            BackupPreferences.setLastOpenedDate(
                context,
                date.toString()
            )
        }
    }


    var showSearch by remember { mutableStateOf(false) }
    val previewScrollState = rememberScrollState()

    /* ---------------- TOOLBAR STATE ---------------- */

    var showTypeDialog by remember { mutableStateOf(false) }

    /* ---------------- ANIMATION ---------------- */
    val slideOffsetX = remember { Animatable(0f) }
    val screenWidth = 400f // safe constant for now

    fun animateToDate(newDate: LocalDate, direction: Int) {
        scope.launch {
            // slide current page out
            slideOffsetX.animateTo(direction * screenWidth)

            // change date AFTER slide-out
            selectedDate = newDate

            // jump content to opposite side (invisible)
            slideOffsetX.snapTo(-direction * screenWidth)

            // slide new page in
            slideOffsetX.animateTo(0f)
        }
    }


    /* ---------------- App Lock ---------------- */


    var showLockDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isUnlocked by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, lockMode, isRestoreInProgress) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (
                    lockMode != LockMode.OFF &&
                    !isUnlocked &&
                    !isRestoreInProgress   // ⭐ KEY FIX
                ) {
                    showLockDialog = true
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .height(80.dp) ,// adjust if needed

                    )

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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

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
                        selectedDate = LocalDate.now()
                    },
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(24.dp))

            }


            Divider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )


// --- Editor Toolbar ---

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(selectedDate, showEditor) {
                        if (!showEditor) {
                            var totalDrag = 0f

                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                    scope.launch {
                                        slideOffsetX.snapTo(totalDrag)
                                    }
                                },
                                onDragEnd = {
                                    when {
                                        totalDrag < -120f ->
                                            animateToDate(currentDate.plusDays(1)
                                                , -1)

                                        totalDrag > 120f ->
                                            animateToDate(currentDate.minusDays(1)
                                                , +1)

                                        else ->
                                            scope.launch { slideOffsetX.animateTo(0f) }
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = {
                                    scope.launch { slideOffsetX.animateTo(0f) }
                                    totalDrag = 0f
                                }
                            )
                        }
                    }
            ) {

                // ---------- CONTENT ----------
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = slideOffsetX.value
                        }
                ) {

                    if (showEditor) {

                        Column(modifier = Modifier
                            .fillMaxSize()
                            ) {

                            EditorToolbar(
                                enabled = true,
                                onAddType = { showTypeDialog = true },
                                onFormat = { diaryViewModel.applyFormat(it) }
                            )

                            Divider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)

                            BasicTextField(
                                value = diaryViewModel.diaryText,
                                onValueChange = diaryViewModel::updateDiaryText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding()        // ⭐ THIS IS THE KEY
                                    .navigationBarsPadding()
                                    .verticalScroll(editorScrollState),
                                textStyle = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = 24.sp
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                            .ruledBackground(lineColor = ruledLineColor,
                                                strokeWidth = ruledLineThickness)
                                            .padding(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 4.dp,   // ⭐ fine-tuned for baseline
                                                bottom = 8.dp
                                            )
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                        }

                    } else {

                        // ---------- READ ONLY ----------
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(previewScrollState)
                                .background(MaterialTheme.colorScheme.background)
                                .ruledBackground(lineColor = ruledLineColor,
                                    strokeWidth = ruledLineThickness)
                                .padding(12.dp)
                        ) {
                            if (showPlaceholder) {
                                EmptyDayPlaceholder()
                            } else {
                                Text(
                                    text = diaryViewModel.diaryText.text,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        lineHeight = 24.sp
                                    )
                                )
                            }
                        }
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
        MonthYearPicker(
            initialDate = currentDate,
            onDismiss = { showCalendarPicker = false },
            onDateSelected = { newDate ->
                selectedDate = newDate
                showCalendarPicker = false
            }
        )
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
    onFormat: (TextFormat) -> Unit
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
        Spacer(Modifier.weight(1f))

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

            // Results
            if (viewModel.searchResults.isEmpty()) {
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
                                Text(diary.date)
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




