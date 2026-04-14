package com.example.ayniqbooking

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ayniqbooking.ui.theme.AyniqTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AyniqTheme {
                AppScreen()
            }
        }
    }
}

private val successLogRegex = Regex("""\[(\d{2}:\d{2}:\d{2})]\s+([^|]+?)\s+\|\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+->\s+✅""")

private fun parseSuccessRecord(
    line: String,
    accountsByPhone: Map<String, AccountSession>,
    gateEmail: String
): BookingSuccessRecord? {
    val match = successLogRegex.find(line) ?: return null
    val processedAt = match.groupValues[1]
    val phone = match.groupValues[2].trim()
    val bookingDate = match.groupValues[3]
    val slot = match.groupValues[4].trim()
    val account = accountsByPhone[phone]
    val fullName = if (account == null) {
        "غير معروف"
    } else {
        "${account.payload.citizen_name} ${account.payload.citizen_name_2} ${account.payload.citizen_surname}".trim()
    }
    val officeName = account?.officeName ?: "غير معروف"
    return BookingSuccessRecord(
        processedAt = processedAt,
        gateEmail = gateEmail.ifBlank { "غير معروف" },
        phone = phone,
        fullName = fullName,
        officeName = officeName,
        bookingDate = bookingDate,
        slot = slot
    )
}

private data class FamilyMemberDraft(
    val firstName: String = "",
    val fatherName: String = "",
    val grandName: String = "",
    val surname: String = "",
    val motherName: String = "",
    val gender: Int = 0,
    val dob: String = "",
    val relationId: String = "3"
)

private data class BookingSuccessRecord(
    val processedAt: String,
    val gateEmail: String,
    val phone: String,
    val fullName: String,
    val officeName: String,
    val bookingDate: String,
    val slot: String
)

@Composable
private fun DateInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    autoValue: String,
    minDate: LocalDate = LocalDate.of(1950, 1, 1),
    maxDate: LocalDate = LocalDate.now(),
    allowClear: Boolean = true,
    manualHint: String = "اكتب التاريخ أو عدله من السليدر بالأسفل",
    modifier: Modifier = Modifier
) {
    val safeMinDate = if (minDate.isAfter(maxDate)) maxDate else minDate
    fun clampDate(date: LocalDate): LocalDate {
        return when {
            date.isBefore(safeMinDate) -> safeMinDate
            date.isAfter(maxDate) -> maxDate
            else -> date
        }
    }

    val fallback = if (isValidIsoDate(autoValue)) {
        clampDate(LocalDate.parse(autoValue, DateTimeFormatter.ISO_DATE))
    } else {
        safeMinDate
    }
    val selectedDate = if (isValidIsoDate(value)) {
        clampDate(LocalDate.parse(value, DateTimeFormatter.ISO_DATE))
    } else {
        fallback
    }
    val yearMin = safeMinDate.year
    val yearMax = maxDate.year
    val monthLength = YearMonth.of(selectedDate.year, selectedDate.monthValue).lengthOfMonth()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = {
                Text(
                    if (value.isBlank()) manualHint
                    else "الصيغة المطلوبة: YYYY-MM-DD"
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onValueChange(fallback.format(DateTimeFormatter.ISO_DATE)) }) {
                Text("تاريخ تلقائي")
            }
            if (allowClear && value.isNotBlank()) {
                TextButton(onClick = { onValueChange("") }) {
                    Text("مسح")
                }
            }
        }
        Text("السنة: ${selectedDate.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = selectedDate.year.toFloat(),
            onValueChange = { raw ->
                val year = raw.roundToInt().coerceIn(yearMin, yearMax)
                val day = selectedDate.dayOfMonth.coerceAtMost(YearMonth.of(year, selectedDate.monthValue).lengthOfMonth())
                val updated = clampDate(LocalDate.of(year, selectedDate.monthValue, day))
                onValueChange(updated.format(DateTimeFormatter.ISO_DATE))
            },
            valueRange = yearMin.toFloat()..yearMax.toFloat()
        )
        Text("الشهر: ${selectedDate.monthValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = selectedDate.monthValue.toFloat(),
            onValueChange = { raw ->
                val month = raw.roundToInt().coerceIn(1, 12)
                val day = selectedDate.dayOfMonth.coerceAtMost(YearMonth.of(selectedDate.year, month).lengthOfMonth())
                val updated = clampDate(LocalDate.of(selectedDate.year, month, day))
                onValueChange(updated.format(DateTimeFormatter.ISO_DATE))
            },
            valueRange = 1f..12f
        )
        Text("اليوم: ${selectedDate.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = selectedDate.dayOfMonth.toFloat(),
            onValueChange = { raw ->
                val day = raw.roundToInt().coerceIn(1, monthLength)
                val updated = clampDate(LocalDate.of(selectedDate.year, selectedDate.monthValue, day))
                onValueChange(updated.format(DateTimeFormatter.ISO_DATE))
            },
            valueRange = 1f..monthLength.toFloat()
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(999.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrimaryWideButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text)
    }
}

@Composable
private fun RunControlButtons(
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStart,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
        ) {
            Text("تشغيل")
        }
        Button(
            onClick = onPause,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
        ) {
            Text("Pause")
        }
        Button(
            onClick = onStop,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Text("إيقاف")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { SessionStore(context) }
    var appState by remember { mutableStateOf(AppState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        store.stateFlow.collectLatest { appState = it }
    }

    val snackbarHost = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    var appCheck by rememberSaveable { mutableStateOf("") }
    var proxyEnabled by rememberSaveable { mutableStateOf(false) }
    var proxyHost by rememberSaveable { mutableStateOf("") }
    var proxyPort by rememberSaveable { mutableStateOf("") }
    var proxyUser by rememberSaveable { mutableStateOf("") }
    var proxyPass by rememberSaveable { mutableStateOf("") }
    var gateEmail by rememberSaveable { mutableStateOf("") }
    var gatePassword by rememberSaveable { mutableStateOf("") }
    var blockedNotice by rememberSaveable { mutableStateOf("") }
    var accessValidationRunning by remember { mutableStateOf(false) }
    var lastValidatedToken by remember { mutableStateOf("") }
    var fetchAdminKey by rememberSaveable { mutableStateOf("") }
    var fetchServerUrl by rememberSaveable { mutableStateOf("") }
    var fetchingAppCheck by remember { mutableStateOf(false) }
    var useAccountAppCheckOverride by rememberSaveable { mutableStateOf(false) }
    var accountAppCheckOverride by rememberSaveable { mutableStateOf("") }
    var useAccountProxyOverride by rememberSaveable { mutableStateOf(false) }
    var accountProxyEnabled by rememberSaveable { mutableStateOf(false) }
    var accountProxyHost by rememberSaveable { mutableStateOf("") }
    var accountProxyPort by rememberSaveable { mutableStateOf("") }
    var accountProxyUser by rememberSaveable { mutableStateOf("") }
    var accountProxyPass by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(appState.settings) {
        appCheck = appState.settings.appCheck
        proxyEnabled = appState.settings.proxy.enabled
        proxyHost = appState.settings.proxy.host
        proxyPort = appState.settings.proxy.port.takeIf { it > 0 }?.toString().orEmpty()
        proxyUser = appState.settings.proxy.username
        proxyPass = appState.settings.proxy.password
    }

    LaunchedEffect(appState.access) {
        gateEmail = appState.access.email
    }

    val draftProxy = ProxyConfig(
        enabled = proxyEnabled,
        host = normalizeProxyHost(proxyHost),
        port = proxyPort.toIntOrNull() ?: 0,
        username = proxyUser.trim(),
        password = proxyPass
    )
    val accountDraftProxy = ProxyConfig(
        enabled = accountProxyEnabled,
        host = normalizeProxyHost(accountProxyHost),
        port = accountProxyPort.toIntOrNull() ?: 0,
        username = accountProxyUser.trim(),
        password = accountProxyPass
    )
    val accountNetworkOverrideDraft = AccountNetworkOverride(
        useCustomAppCheck = useAccountAppCheckOverride,
        appCheck = accountAppCheckOverride.trim(),
        useCustomProxy = useAccountProxyOverride,
        proxy = accountDraftProxy
    )
    val currentSettings = AppSettings(appCheck = appCheck.trim(), proxy = draftProxy)
    val inlineAppCheckError = validateAppCheck(appCheck, required = false)
    val inlineProxyError = validateProxyConfig(draftProxy)
    val inlineAccountAppCheckError = if (useAccountAppCheckOverride) {
        validateAppCheck(accountAppCheckOverride, required = true)
    } else {
        null
    }
    val inlineAccountProxyError = if (useAccountProxyOverride) {
        validateProxyConfig(accountDraftProxy)
    } else {
        null
    }
    val effectiveDraftSettings = mergeEffectiveSettings(currentSettings, accountNetworkOverrideDraft)

    val api = AyniqApi { currentSettings }
    val gateApi = remember { GateApi() }
    val appCheckFetcher = remember { AppCheckFetcher() }
    val bookingEngine = remember(api) { BookingEngine(api) }

    var phone by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var fatherName by rememberSaveable { mutableStateOf("") }
    var grandName by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var motherName by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableIntStateOf(0) }
    var selectedOfficeId by rememberSaveable { mutableStateOf(offices.first().id) }
    var hasFamily by rememberSaveable { mutableStateOf(false) }
    var familyCountText by rememberSaveable { mutableStateOf("1") }
    val familyMembers = remember { mutableStateListOf<FamilyMemberDraft>() }
    var relationMenuIndex by remember { mutableIntStateOf(-1) }

    var officeMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    val week = remember { getUpcomingWeek() }
    var selectedDayIdx by rememberSaveable { mutableIntStateOf(0) }
    var manualRunDate by rememberSaveable { mutableStateOf(normalizeToTomorrowIfPast(week.firstOrNull()?.date.orEmpty())) }
    var selectedPackage by rememberSaveable { mutableStateOf("morning") }
    var useSingleSlot by rememberSaveable { mutableStateOf(false) }
    var sendAllAccounts by rememberSaveable { mutableStateOf(false) }
    var selectedSingleSlot by rememberSaveable { mutableStateOf("") }
    var selectedAccountPhone by rememberSaveable { mutableStateOf("") }
    val accountRunStates = remember { mutableStateMapOf<String, AccountRunState>() }

    val logs = remember { mutableStateListOf<String>() }
    val successRecords = remember { mutableStateListOf<BookingSuccessRecord>() }
    var bookingJob by remember { mutableStateOf<Job?>(null) }

    fun gateBlockMessage(code: String?): String? {
        return when (code) {
            GateApi.BLOCKED_CODE -> "الحساب محظور. يرجى التسجيل بحساب آخر أو التواصل مع الدعم."
            GateApi.DEVICE_BLOCKED_CODE -> "هذا الجهاز محظور لهذا الحساب. استخدم جهاز/حساب آخر أو تواصل مع الدعم."
            else -> null
        }
    }

    fun ensureFamilySize(target: Int) {
        val safeTarget = target.coerceIn(0, 9)
        while (familyMembers.size < safeTarget) {
            familyMembers.add(FamilyMemberDraft(dob = randomDob(isHead = false)))
        }
        while (familyMembers.size > safeTarget) {
            familyMembers.removeAt(familyMembers.lastIndex)
        }
    }

    fun selectedTargetPhones(): List<String> {
        return if (sendAllAccounts) {
            appState.sessions.keys.toList()
        } else {
            listOfNotNull(selectedAccountPhone.takeIf { it.isNotBlank() && it in appState.sessions })
        }
    }

    fun runStateLabel(state: AccountRunState): String {
        return when (state) {
            AccountRunState.RUNNING -> "تشغيل"
            AccountRunState.PAUSED -> "Pause"
            AccountRunState.STOPPED -> "إيقاف"
        }
    }

    LaunchedEffect(appState.sessions.keys) {
        if (selectedAccountPhone !in appState.sessions.keys) {
            selectedAccountPhone = appState.sessions.keys.firstOrNull().orEmpty()
        }
        val liveKeys = appState.sessions.keys
        accountRunStates.keys.filter { it !in liveKeys }.forEach { accountRunStates.remove(it) }
        liveKeys.forEach { key ->
            if (accountRunStates[key] == null) {
                accountRunStates[key] = AccountRunState.STOPPED
            }
        }
    }

    LaunchedEffect(appState.access.token, appState.access.approved) {
        if (!appState.access.approved || appState.access.token.isBlank()) {
            return@LaunchedEffect
        }
        if (appState.access.token == lastValidatedToken || accessValidationRunning) {
            return@LaunchedEffect
        }

        accessValidationRunning = true
        gateApi.validate(appState.access.token)
            .onSuccess { email ->
                blockedNotice = ""
                lastValidatedToken = appState.access.token
                if (email.lowercase() != appState.access.email.lowercase()) {
                    store.clearAccess()
                    snackbarHost.showSnackbar("تم رفض الجلسة: حساب مختلف")
                }
            }
            .onFailure { err ->
                store.clearAccess()
                blockedNotice = gateBlockMessage(err.message).orEmpty()
                snackbarHost.showSnackbar(
                    blockedNotice.ifBlank { "تم إيقاف الوصول من السيرفر" }
                )
            }
        accessValidationRunning = false
    }

    LaunchedEffect(hasFamily, familyCountText) {
        if (!hasFamily) {
            ensureFamilySize(0)
            return@LaunchedEffect
        }
        ensureFamilySize(familyCountText.toIntOrNull() ?: 1)
    }

    LaunchedEffect(selectedPackage) {
        val slots = if (selectedPackage == "morning") morningSlots else eveningSlots
        if (selectedSingleSlot !in slots) {
            selectedSingleSlot = slots.firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(selectedDayIdx) {
        val candidate = week.getOrNull(selectedDayIdx)?.date.orEmpty()
        manualRunDate = normalizeToTomorrowIfPast(candidate)
    }

    DisposableEffect(Unit) {
        onDispose {
            bookingJob?.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ayniq Booking", fontWeight = FontWeight.Bold)
                        StatusPill(if (appState.access.approved) "بوابة الدخول مفعلة" else "تسجيل الدخول مطلوب")
                    }
                },
                actions = {
                    if (appState.access.approved) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    bookingJob?.cancel()
                                    bookingJob = null
                                    lastValidatedToken = ""
                                    blockedNotice = ""
                                    store.clearAccess()
                                    snackbarHost.showSnackbar("تم تسجيل الخروج")
                                }
                            }
                        ) {
                            Text("تسجيل خروج")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            Color(0xFFEFF4FA)
                        )
                    )
                )
                .padding(padding)
        ) {
            val accessAllowed = appState.access.approved &&
                appState.access.token.isNotBlank()

            if (!accessAllowed) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionCard(
                            title = "بوابة الدخول",
                            subtitle = "سجل دخول بحساب مفعل من لوحة التحكم للوصول إلى التطبيق"
                        ) {
                            if (blockedNotice.isNotBlank()) {
                                Text(
                                    blockedNotice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedTextField(
                                value = gateEmail,
                                onValueChange = { gateEmail = it },
                                label = { Text("Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = gatePassword,
                                onValueChange = { gatePassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            PrimaryWideButton(
                                text = "دخول",
                                onClick = {
                                    scope.launch {
                                        if (gateEmail.isBlank() || gatePassword.isBlank()) {
                                            snackbarHost.showSnackbar("املأ بيانات الدخول كاملة")
                                            return@launch
                                        }
                                        val deviceId = Settings.Secure.getString(
                                            context.contentResolver,
                                            Settings.Secure.ANDROID_ID
                                        ) ?: "unknown-device"
                                        gateApi.login(
                                            email = gateEmail,
                                            password = gatePassword,
                                            deviceId = deviceId
                                        ).onSuccess { result ->
                                            blockedNotice = ""
                                            store.updateAccess(
                                                AccessControl(
                                                    serverUrl = GATE_SERVER_URL,
                                                    email = result.email,
                                                    token = result.token,
                                                    approved = true
                                                )
                                            )
                                            gatePassword = ""
                                            snackbarHost.showSnackbar("تم تسجيل الدخول")
                                        }.onFailure { err ->
                                            val blockMsg = gateBlockMessage(err.message)
                                            if (blockMsg != null) {
                                                blockedNotice = blockMsg
                                                snackbarHost.showSnackbar(blockMsg)
                                            } else {
                                                snackbarHost.showSnackbar("رفض تسجيل الدخول: ${err.message}")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                TabRow(
                    selectedTabIndex = currentTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text("الحسابات") })
                    Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text("التشغيل") })
                    Tab(selected = currentTab == 2, onClick = { currentTab = 2 }, text = { Text("المحجوزات") })
                }

                if (currentTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SectionCard(
                            title = "الإعدادات العامة",
                            subtitle = "كل ما يخص AppCheck والبروكسي في مكان واحد واضح"
                        ) {
                            OutlinedTextField(
                                value = appCheck,
                                onValueChange = { appCheck = it },
                                label = { Text("AppCheck JWT") },
                                isError = inlineAppCheckError != null,
                                minLines = 3,
                                supportingText = {
                                    Text(
                                        inlineAppCheckError
                                            ?: "ألصق هنا x-firebase-appcheck الحقيقي. لا تضع Username أو Password الخاص بالبروكسي في هذه الخانة."
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "لو كنت تستخدم SOAX: ضع Host و Port و Username و Password في قسم البروكسي فقط. AppCheck غالبًا يبدأ بـ eyJ...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (fetchAdminKey.isBlank()) {
                                            snackbarHost.showSnackbar("أدخل مفتاح الأدمن أولاً لسحب التوكن")
                                            return@launch
                                        }
                                        fetchingAppCheck = true
                                        appCheckFetcher.fetchAppCheckToken(
                                            customServerUrl = fetchServerUrl.ifBlank { null },
                                            customAdminKey = fetchAdminKey
                                        ).onSuccess { token ->
                                            appCheck = token
                                            snackbarHost.showSnackbar("تم سحب AppCheck بنجاح من السيرفر")
                                        }.onFailure { err ->
                                            snackbarHost.showSnackbar(err.message ?: "فشل سحب التوكن")
                                        }
                                        fetchingAppCheck = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                enabled = !fetchingAppCheck
                            ) {
                                Text(if (fetchingAppCheck) "جاري السحب..." else "سحب AppCheck من السيرفر")
                            }
                            OutlinedTextField(
                                value = fetchAdminKey,
                                onValueChange = { fetchAdminKey = it },
                                label = { Text("مفتاح الأدمن (Admin Key)") },
                                supportingText = { Text("المفتاح الذي وضعته في إعدادات سيرفر التحكم") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = fetchServerUrl,
                                onValueChange = { fetchServerUrl = it },
                                label = { Text("رابط السيرفر (اختياري)") },
                                supportingText = { Text("اتركه فارغ لاستخدام السيرفر الافتراضي") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("البروكسي اليدوي", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                            }
                            Text(
                                if (proxyEnabled) {
                                    inlineProxyError ?: "مثال SOAX: Host = proxy.soax.com ، Port = 5000 ، ثم ضع Username و Password بالأسفل."
                                } else {
                                    "إذا كان البروكسي شغال من إعدادات الهاتف نفسها، اترك هذا الخيار مغلقًا وسيستخدم التطبيق اتصال الجهاز الطبيعي."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (proxyEnabled && inlineProxyError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (proxyEnabled) {
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it },
                                    label = { Text("Proxy Host") },
                                    supportingText = { Text("اكتب اسم الهوست فقط مثل proxy.soax.com بدون http://") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = proxyPort,
                                    onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                                    label = { Text("Proxy Port") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = proxyUser,
                                    onValueChange = { proxyUser = it },
                                    label = { Text("Proxy Username") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = proxyPass,
                                    onValueChange = { proxyPass = it },
                                    label = { Text("Proxy Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            PrimaryWideButton(
                                text = "حفظ الإعدادات",
                                onClick = {
                                    scope.launch {
                                        val saveError = inlineAppCheckError ?: inlineProxyError
                                        if (saveError != null) {
                                            snackbarHost.showSnackbar(saveError)
                                            return@launch
                                        }
                                        store.updateSettings(currentSettings)
                                        proxyHost = currentSettings.proxy.host
                                        snackbarHost.showSnackbar("تم حفظ الإعدادات")
                                    }
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            validateAppCheck(currentSettings.appCheck, required = true)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            store.updateSettings(currentSettings)
                                            store.updateSessions { sessions ->
                                                sessions.mapValues { (_, account) ->
                                                    account.copy(
                                                        networkOverride = account.networkOverride.copy(
                                                            useCustomAppCheck = true,
                                                            appCheck = currentSettings.appCheck.trim()
                                                        )
                                                    )
                                                }
                                            }
                                            snackbarHost.showSnackbar("تم تعميم AppCheck على كل الحسابات")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Text("تعميم AppCheck")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            validateProxyConfig(currentSettings.proxy)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            store.updateSettings(currentSettings)
                                            store.updateSessions { sessions ->
                                                sessions.mapValues { (_, account) ->
                                                    account.copy(
                                                        networkOverride = account.networkOverride.copy(
                                                            useCustomProxy = true,
                                                            proxy = currentSettings.proxy
                                                        )
                                                    )
                                                }
                                            }
                                            snackbarHost.showSnackbar("تم تعميم إعدادات البروكسي على كل الحسابات")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Text("تعميم Proxy")
                                }
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        bookingJob?.cancel()
                                        bookingJob = null
                                        lastValidatedToken = ""
                                        store.clearAccess()
                                        snackbarHost.showSnackbar("تم تسجيل الخروج من بوابة الدخول")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("تسجيل خروج البوابة")
                            }
                        }
                    }

                    item { HorizontalDivider() }

                    item {
                        SectionCard(
                            title = "إضافة أو تعديل حساب",
                            subtitle = "ابدأ بالهاتف وOTP ثم أكمل بيانات صاحب الطلب"
                        ) {
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("OTP") }, modifier = Modifier.fillMaxWidth())

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (phone.isBlank()) {
                                                snackbarHost.showSnackbar("ادخل رقم الهاتف")
                                                return@launch
                                            }
                                            val accountConfigError = inlineAccountAppCheckError ?: inlineAccountProxyError
                                            if (accountConfigError != null) {
                                                snackbarHost.showSnackbar(accountConfigError)
                                                return@launch
                                            }
                                            validateAppCheck(effectiveDraftSettings.appCheck, required = true)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            validateProxyConfig(effectiveDraftSettings.proxy)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            api.requestOtp(phone.trim(), accountNetworkOverrideDraft)
                                                .onSuccess { snackbarHost.showSnackbar(it) }
                                                .onFailure { snackbarHost.showSnackbar("فشل OTP: ${it.message}") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("إرسال OTP")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (phone.isBlank() || otp.isBlank()) {
                                                snackbarHost.showSnackbar("ادخل الهاتف و OTP")
                                                return@launch
                                            }
                                            val accountConfigError = inlineAccountAppCheckError ?: inlineAccountProxyError
                                            if (accountConfigError != null) {
                                                snackbarHost.showSnackbar(accountConfigError)
                                                return@launch
                                            }
                                            validateAppCheck(effectiveDraftSettings.appCheck, required = true)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            validateProxyConfig(effectiveDraftSettings.proxy)?.let {
                                                snackbarHost.showSnackbar(it)
                                                return@launch
                                            }
                                            api.login(phone.trim(), otp.trim(), accountNetworkOverrideDraft)
                                                .onSuccess {
                                                    token = it
                                                    snackbarHost.showSnackbar("تم تسجيل الدخول وحفظ التوكن مؤقتًا")
                                                }
                                                .onFailure { snackbarHost.showSnackbar("فشل تسجيل الدخول: ${it.message}") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("تحقق OTP")
                                }
                            }

                            OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("الاسم الأول") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = fatherName, onValueChange = { fatherName = it }, label = { Text("اسم الأب") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = grandName, onValueChange = { grandName = it }, label = { Text("اسم الجد") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("اللقب") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = motherName, onValueChange = { motherName = it }, label = { Text("اسم الأم الثلاثي") }, modifier = Modifier.fillMaxWidth())
                            DateInputField(
                                value = dob,
                                label = "تاريخ ميلاد صاحب الطلب",
                                onValueChange = { dob = it },
                                autoValue = randomDob(isHead = true),
                                modifier = Modifier.fillMaxWidth()
                            )

                            SectionCard(
                                title = "إعدادات الشبكة لهذا الحساب",
                                subtitle = "اختياري: لو أغلقت الخيارات سيستخدم الحساب الإعدادات العامة"
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AppCheck مخصص للحساب")
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = useAccountAppCheckOverride,
                                        onCheckedChange = { useAccountAppCheckOverride = it }
                                    )
                                }
                                if (useAccountAppCheckOverride) {
                                    OutlinedTextField(
                                        value = accountAppCheckOverride,
                                        onValueChange = { accountAppCheckOverride = it },
                                        label = { Text("AppCheck لهذا الحساب") },
                                        isError = inlineAccountAppCheckError != null,
                                        minLines = 3,
                                        supportingText = {
                                            Text(
                                                inlineAccountAppCheckError
                                                    ?: "سيتم استخدام هذا AppCheck فقط مع هذا الحساب."
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Proxy مخصص للحساب")
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = useAccountProxyOverride,
                                        onCheckedChange = { useAccountProxyOverride = it }
                                    )
                                }
                                if (useAccountProxyOverride) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("تفعيل البروكسي لهذا الحساب")
                                        Spacer(Modifier.weight(1f))
                                        Switch(
                                            checked = accountProxyEnabled,
                                            onCheckedChange = { accountProxyEnabled = it }
                                        )
                                    }
                                    if (accountProxyEnabled) {
                                        OutlinedTextField(
                                            value = accountProxyHost,
                                            onValueChange = { accountProxyHost = it },
                                            label = { Text("Proxy Host للحساب") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = accountProxyPort,
                                            onValueChange = { accountProxyPort = it.filter { c -> c.isDigit() } },
                                            label = { Text("Proxy Port للحساب") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = accountProxyUser,
                                            onValueChange = { accountProxyUser = it },
                                            label = { Text("Proxy Username للحساب") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = accountProxyPass,
                                            onValueChange = { accountProxyPass = it },
                                            label = { Text("Proxy Password للحساب") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (inlineAccountProxyError != null) {
                                        Text(
                                            inlineAccountProxyError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            "اتصال هذا الحساب: ${describeConnectionMode(effectiveDraftSettings)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الجنس", fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(selected = gender == 0, onClick = { gender = 0 }, label = { Text("ذكر") })
                                    FilterChip(selected = gender == 1, onClick = { gender = 1 }, label = { Text("أنثى") })
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("إضافة أفراد أسرة", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = hasFamily, onCheckedChange = { hasFamily = it })
                            }
                            if (hasFamily) {
                                OutlinedTextField(
                                    value = familyCountText,
                                    onValueChange = { familyCountText = it.filter { c -> c.isDigit() } },
                                    label = { Text("عدد الأفراد المرافقين (1-9)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                familyMembers.forEachIndexed { idx, member ->
                                    SectionCard(
                                        title = "الفرد ${idx + 1}",
                                        subtitle = "بيانات فرد الأسرة المرافق"
                                    ) {
                                        OutlinedTextField(
                                            value = member.firstName,
                                            onValueChange = { familyMembers[idx] = member.copy(firstName = it) },
                                            label = { Text("الاسم الأول") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = member.fatherName,
                                            onValueChange = { familyMembers[idx] = member.copy(fatherName = it) },
                                            label = { Text("اسم الأب") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = member.grandName,
                                            onValueChange = { familyMembers[idx] = member.copy(grandName = it) },
                                            label = { Text("اسم الجد") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = member.surname,
                                            onValueChange = { familyMembers[idx] = member.copy(surname = it) },
                                            label = { Text("اللقب") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = member.motherName,
                                            onValueChange = { familyMembers[idx] = member.copy(motherName = it) },
                                            label = { Text("اسم الأم الثلاثي") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DateInputField(
                                            value = member.dob,
                                            onValueChange = { familyMembers[idx] = member.copy(dob = it) },
                                            label = "تاريخ ميلاد الفرد",
                                            autoValue = randomDob(isHead = false),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            FilterChip(
                                                selected = member.gender == 0,
                                                onClick = { familyMembers[idx] = member.copy(gender = 0) },
                                                label = { Text("ذكر") }
                                            )
                                            FilterChip(
                                                selected = member.gender == 1,
                                                onClick = { familyMembers[idx] = member.copy(gender = 1) },
                                                label = { Text("أنثى") }
                                            )
                                        }
                                        val selectedRelation = relations.firstOrNull { it.id == member.relationId } ?: relations[2]
                                        Button(
                                            onClick = { relationMenuIndex = idx },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text("صلة القرابة: ${selectedRelation.name}")
                                        }
                                        DropdownMenu(
                                            expanded = relationMenuIndex == idx,
                                            onDismissRequest = { relationMenuIndex = -1 }
                                        ) {
                                            relations.forEach { relation ->
                                                DropdownMenuItem(
                                                    text = { Text(relation.name) },
                                                    onClick = {
                                                        familyMembers[idx] = member.copy(relationId = relation.id)
                                                        relationMenuIndex = -1
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val selectedOffice = offices.first { it.id == selectedOfficeId }
                            Button(
                                onClick = { officeMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("الدائرة: ${selectedOffice.name}")
                            }
                            DropdownMenu(expanded = officeMenuExpanded, onDismissRequest = { officeMenuExpanded = false }) {
                                offices.forEach { office ->
                                    DropdownMenuItem(
                                        text = { Text(office.name) },
                                        onClick = {
                                            selectedOfficeId = office.id
                                            officeMenuExpanded = false
                                        }
                                    )
                                }
                            }

                            PrimaryWideButton(
                                text = "حفظ الحساب",
                                onClick = {
                                    scope.launch {
                                        if (phone.isBlank() || firstName.isBlank() || fatherName.isBlank() || grandName.isBlank() || surname.isBlank() || motherName.isBlank()) {
                                            snackbarHost.showSnackbar("املأ كل البيانات الأساسية")
                                            return@launch
                                        }
                                        val accountOverrideError = inlineAccountAppCheckError ?: inlineAccountProxyError
                                        if (accountOverrideError != null) {
                                            snackbarHost.showSnackbar(accountOverrideError)
                                            return@launch
                                        }
                                        if (token.isBlank()) {
                                            snackbarHost.showSnackbar("لازم تعمل OTP وتجيب التوكن الأول")
                                            return@launch
                                        }
                                        val headDob = dob.ifBlank { randomDob(isHead = true) }
                                        if (!isValidIsoDate(headDob)) {
                                            snackbarHost.showSnackbar("تاريخ ميلاد صاحب الطلب غير صحيح. استخدم YYYY-MM-DD")
                                            return@launch
                                        }
                                        if (hasFamily) {
                                            val expected = familyCountText.toIntOrNull()?.coerceIn(1, 9) ?: 1
                                            ensureFamilySize(expected)
                                            if (familyMembers.any {
                                                    it.firstName.isBlank() || it.fatherName.isBlank() || it.grandName.isBlank() ||
                                                        it.surname.isBlank() || it.motherName.isBlank()
                                                }) {
                                                snackbarHost.showSnackbar("املأ بيانات أفراد الأسرة بالكامل")
                                                return@launch
                                            }
                                            if (familyMembers.any { !isValidIsoDate(it.dob.ifBlank { randomDob(isHead = false) }) }) {
                                                snackbarHost.showSnackbar("أحد تواريخ ميلاد أفراد الأسرة غير صحيح. استخدم YYYY-MM-DD")
                                                return@launch
                                            }
                                        }

                                        val mappedFamily = if (hasFamily) {
                                            familyMembers.map { item ->
                                                val relationValue = relations.firstOrNull { it.id == item.relationId }?.value ?: 20
                                                PersonPayload(
                                                    citizen_name = item.firstName.trim(),
                                                    citizen_name_2 = item.fatherName.trim(),
                                                    citizen_name_3 = item.grandName.trim(),
                                                    citizen_surname = item.surname.trim(),
                                                    citizen_mother_name = item.motherName.trim(),
                                                    citizen_gender = item.gender,
                                                    citizen_dob = item.dob.ifBlank { randomDob(isHead = false) },
                                                    relationship = relationValue
                                                )
                                            }
                                        } else {
                                            emptyList()
                                        }

                                        val payload = BookingPayload(
                                            citizen_name = firstName.trim(),
                                            citizen_name_2 = fatherName.trim(),
                                            citizen_name_3 = grandName.trim(),
                                            citizen_surname = surname.trim(),
                                            citizen_mother_name = motherName.trim(),
                                            citizen_gender = gender,
                                            citizen_dob = headDob,
                                            citizen_phone_number = phone.trim(),
                                            citizen_family = if (hasFamily) 1 else 0,
                                            family_members = mappedFamily
                                        )
                                        val office = offices.first { it.id == selectedOfficeId }
                                        if (!isValidUuid(office.uuid)) {
                                            snackbarHost.showSnackbar("الدائرة المختارة غير صالحة حاليًا في النظام. اختر دائرة أخرى.")
                                            return@launch
                                        }
                                        val session = AccountSession(
                                            phone = phone.trim(),
                                            token = token.trim(),
                                            officeUuid = office.uuid,
                                            officeName = office.name,
                                            payload = payload,
                                            networkOverride = accountNetworkOverrideDraft
                                        )
                                        store.upsertAccount(session)
                                        selectedAccountPhone = phone.trim()
                                        snackbarHost.showSnackbar("تم حفظ الحساب")
                                    }
                                }
                            )
                        }
                    }

                    item { HorizontalDivider() }

                    item {
                        SectionCard(
                            title = "الحسابات المحفوظة",
                            subtitle = "الحسابات الجاهزة للتعديل أو الإرسال"
                        ) {
                            StatusPill("${appState.sessions.size} حساب")
                        }
                    }

                    if (appState.sessions.isEmpty()) {
                        item { Text("لا توجد حسابات محفوظة") }
                    } else {
                        items(appState.sessions.values.toList()) { account ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val effectiveSettings = mergeEffectiveSettings(currentSettings, account.networkOverride)
                                    Text(account.phone, fontWeight = FontWeight.Bold)
                                    Text("${account.payload.citizen_name} ${account.payload.citizen_surname}")
                                    Text(account.officeName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "AppCheck: ${if (account.networkOverride.useCustomAppCheck) "مخصص" else "عام"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Proxy: ${describeConnectionMode(effectiveSettings)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            phone = account.phone
                                            token = account.token
                                            firstName = account.payload.citizen_name
                                            fatherName = account.payload.citizen_name_2
                                            grandName = account.payload.citizen_name_3
                                            surname = account.payload.citizen_surname
                                            motherName = account.payload.citizen_mother_name
                                            dob = account.payload.citizen_dob
                                            gender = account.payload.citizen_gender
                                            selectedOfficeId = offices.firstOrNull { it.uuid == account.officeUuid }?.id ?: offices.first().id
                                            useAccountAppCheckOverride = account.networkOverride.useCustomAppCheck
                                            accountAppCheckOverride = account.networkOverride.appCheck
                                            useAccountProxyOverride = account.networkOverride.useCustomProxy
                                            accountProxyEnabled = account.networkOverride.proxy.enabled
                                            accountProxyHost = account.networkOverride.proxy.host
                                            accountProxyPort = account.networkOverride.proxy.port.takeIf { it > 0 }?.toString().orEmpty()
                                            accountProxyUser = account.networkOverride.proxy.username
                                            accountProxyPass = account.networkOverride.proxy.password
                                            hasFamily = account.payload.citizen_family == 1
                                            val members = account.payload.family_members
                                            familyCountText = if (members.isNotEmpty()) members.size.toString() else "1"
                                            familyMembers.clear()
                                            familyMembers.addAll(members.map { member ->
                                                val relationId = relations.firstOrNull { it.value == (member.relationship ?: 20) }?.id ?: "3"
                                                FamilyMemberDraft(
                                                    firstName = member.citizen_name,
                                                    fatherName = member.citizen_name_2,
                                                    grandName = member.citizen_name_3,
                                                    surname = member.citizen_surname,
                                                    motherName = member.citizen_mother_name,
                                                    gender = member.citizen_gender,
                                                    dob = member.citizen_dob,
                                                    relationId = relationId
                                                )
                                            })
                                        }) { Text("تحميل للتعديل") }
                                        Button(onClick = {
                                            scope.launch {
                                                store.deleteAccount(account.phone)
                                                snackbarHost.showSnackbar("تم حذف الحساب")
                                            }
                                        }) { Text("حذف") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (currentTab == 1) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("إرسال الحجز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            StatusPill(if (sendAllAccounts) "لكل الحسابات" else "لحساب واحد")
                        }
                    }

                    item {
                        val selectedAccount = appState.sessions[selectedAccountPhone]
                        SectionCard(
                            title = "إعدادات التشغيل",
                            subtitle = "اختيارات سريعة بالسليدر مع إمكانية الكتابة اليدوية"
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (sendAllAccounts) "الإرسال لكل الحسابات" else "الإرسال لحساب واحد")
                                Spacer(Modifier.weight(1f))
                                Switch(checked = sendAllAccounts, onCheckedChange = { sendAllAccounts = it })
                            }
                            if (!sendAllAccounts) {
                                Button(
                                    onClick = { accountMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        if (selectedAccount == null) "اختر حساب"
                                        else "الحساب: ${selectedAccount.phone}"
                                    )
                                }
                                DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                                    appState.sessions.values.forEach { account ->
                                        DropdownMenuItem(
                                            text = { Text("${account.phone} - ${account.payload.citizen_name}") },
                                            onClick = {
                                                selectedAccountPhone = account.phone
                                                accountMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Text("اختيار اليوم", fontWeight = FontWeight.Medium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(week.size) { index ->
                                    val day = week[index]
                                    FilterChip(
                                        selected = selectedDayIdx == index,
                                        onClick = {
                                            selectedDayIdx = index
                                            manualRunDate = normalizeToTomorrowIfPast(day.date)
                                        },
                                        label = { Text(day.label) }
                                    )
                                }
                            }
                            DateInputField(
                                value = manualRunDate,
                                onValueChange = { manualRunDate = it },
                                label = "تاريخ التشغيل يدويًا",
                                autoValue = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE),
                                minDate = LocalDate.now().plusDays(1),
                                maxDate = LocalDate.now().plusYears(2),
                                allowClear = false,
                                manualHint = "اختر تاريخ التشغيل. أي تاريخ أقل من الغد سيتم تعديله تلقائيًا للغد.",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        val currentSlots = if (selectedPackage == "morning") morningSlots else eveningSlots
                        SectionCard(
                            title = "اختيار التوقيت",
                            subtitle = "سليدر أفقي للمواعيد مع إمكانية كتابة الوقت يدويًا"
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(selected = selectedPackage == "morning", onClick = { selectedPackage = "morning" }, label = { Text("باقة الصباح") })
                                FilterChip(selected = selectedPackage == "evening", onClick = { selectedPackage = "evening" }, label = { Text("باقة المساء") })
                            }
                            Text(
                                if (selectedPackage == "morning") "الحزمة الحالية: الصباح (${currentSlots.size} موعد)"
                                else "الحزمة الحالية: المساء (${currentSlots.size} موعد)"
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (useSingleSlot) "وضع: وقت محدد" else "وضع: كل مواعيد الحزمة")
                                Spacer(Modifier.weight(1f))
                                Switch(checked = useSingleSlot, onCheckedChange = { useSingleSlot = it })
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(currentSlots.size) { index ->
                                    val slot = currentSlots[index]
                                    FilterChip(
                                        selected = selectedSingleSlot == slot,
                                        onClick = {
                                            selectedSingleSlot = slot
                                            useSingleSlot = true
                                        },
                                        label = { Text(slot) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = selectedSingleSlot,
                                onValueChange = {
                                    selectedSingleSlot = it
                                    if (it.isNotBlank()) useSingleSlot = true
                                },
                                label = { Text("وقت التشغيل يدويًا") },
                                supportingText = { Text("مثال: 06:00 PM") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        val currentSlots = if (selectedPackage == "morning") morningSlots else eveningSlots
                        RunControlButtons(
                            onStart = {
                                scope.launch {
                                    val targetPhones = selectedTargetPhones()
                                    if (targetPhones.isEmpty()) {
                                        snackbarHost.showSnackbar("لا توجد حسابات صالحة للتشغيل")
                                        return@launch
                                    }
                                    targetPhones.forEach { accountRunStates[it] = AccountRunState.RUNNING }

                                    if (bookingJob?.isActive == true) {
                                        logs.add(0, "تم استئناف التشغيل للحسابات المحددة")
                                        return@launch
                                    }

                                    val slots = if (useSingleSlot) listOf(selectedSingleSlot) else currentSlots
                                    if (slots.firstOrNull().isNullOrBlank()) {
                                        snackbarHost.showSnackbar("اختار توقيت صحيح أو اكتبه يدويًا")
                                        return@launch
                                    }
                                    val accounts = targetPhones.mapNotNull { appState.sessions[it] }
                                    if (accounts.isEmpty()) {
                                        snackbarHost.showSnackbar("لا توجد حسابات محفوظة")
                                        return@launch
                                    }
                                    accounts.forEach { account ->
                                        val effective = mergeEffectiveSettings(currentSettings, account.networkOverride)
                                        val configError = validateAppCheck(effective.appCheck, required = true)
                                            ?: validateProxyConfig(effective.proxy)
                                        if (configError != null) {
                                            snackbarHost.showSnackbar("${account.phone}: $configError")
                                            accountRunStates[account.phone] = AccountRunState.STOPPED
                                            return@launch
                                        }
                                    }

                                    gateApi.validate(appState.access.token)
                                        .onFailure { err ->
                                            store.clearAccess()
                                            blockedNotice = gateBlockMessage(err.message).orEmpty()
                                            snackbarHost.showSnackbar(
                                                blockedNotice.ifBlank { "تم حظر الوصول من السيرفر" }
                                            )
                                            return@launch
                                        }

                                    val normalizedDate = normalizeToTomorrowIfPast(manualRunDate)
                                    val dateAutoAdjusted = normalizedDate != manualRunDate
                                    if (normalizedDate != manualRunDate) {
                                        manualRunDate = normalizedDate
                                    }
                                    val startDate = LocalDate.parse(normalizedDate, DateTimeFormatter.ISO_DATE)
                                    val targetDates = listOf(startDate.format(DateTimeFormatter.ISO_DATE))

                                    logs.clear()
                                    successRecords.clear()
                                    if (dateAutoAdjusted) {
                                        logs.add(0, "تم تعديل تاريخ التشغيل تلقائيًا إلى $normalizedDate لأن التاريخ المدخل قديم")
                                    }
                                    logs.add(0, "وضع الاتصال العام: ${describeConnectionMode(currentSettings)}")
                                    logs.add(0, "بدء الإرسال: ${accounts.size} حساب | ${targetDates.size} تاريخ | ${slots.size} توقيت")
                                    val accountsByPhone = accounts.associateBy { it.phone }
                                    val loginEmail = appState.access.email

                                    bookingJob = scope.launch {
                                        try {
                                            val ok = bookingEngine.runForAccounts(
                                                accounts = accounts,
                                                targetDates = targetDates,
                                                slots = slots,
                                                accountStateProvider = { phone ->
                                                    accountRunStates[phone] ?: AccountRunState.STOPPED
                                                }
                                            ) { line ->
                                                logs.add(0, line)
                                                if (logs.size > 250) logs.removeLast()
                                                parseSuccessRecord(line, accountsByPhone, loginEmail)?.let { record ->
                                                    successRecords.add(0, record)
                                                }
                                            }
                                            if (ok) {
                                                snackbarHost.showSnackbar("انتهى التشغيل للحسابات المحددة")
                                            } else {
                                                snackbarHost.showSnackbar("تم الإيقاف")
                                            }
                                        } catch (t: Throwable) {
                                            logs.add(0, "حدث خطأ داخلي أثناء التشغيل: ${t::class.simpleName}: ${t.message.orEmpty()}")
                                            snackbarHost.showSnackbar("حصل خطأ داخلي أثناء الإرسال")
                                        } finally {
                                            bookingJob = null
                                        }
                                    }
                                }
                            },
                            onPause = {
                                val targetPhones = selectedTargetPhones()
                                if (targetPhones.isNotEmpty()) {
                                    targetPhones.forEach { phone ->
                                        if (accountRunStates[phone] != AccountRunState.STOPPED) {
                                            accountRunStates[phone] = AccountRunState.PAUSED
                                        }
                                    }
                                    logs.add(0, "تم وضع الحسابات المحددة على Pause")
                                }
                            },
                            onStop = {
                                val targetPhones = selectedTargetPhones()
                                if (targetPhones.isNotEmpty()) {
                                    targetPhones.forEach { accountRunStates[it] = AccountRunState.STOPPED }
                                    logs.add(0, "تم إيقاف الحسابات المحددة")
                                    if (accountRunStates.values.none { it == AccountRunState.RUNNING || it == AccountRunState.PAUSED }) {
                                        bookingJob?.cancel()
                                        bookingJob = null
                                    }
                                }
                            }
                        )
                    }

                    item {
                        val targetPhones = selectedTargetPhones()
                        if (targetPhones.isNotEmpty()) {
                            SectionCard(
                                title = "تحكم مباشر بالحسابات",
                                subtitle = "يمكنك تشغيل/Pause/إيقاف كل حساب بشكل منفرد"
                            ) {
                                targetPhones.forEach { phoneNumber ->
                                    val account = appState.sessions[phoneNumber] ?: return@forEach
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(account.phone, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "الحالة: ${runStateLabel(accountRunStates[account.phone] ?: AccountRunState.STOPPED)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        RunControlButtons(
                                            modifier = Modifier.weight(2f),
                                            onStart = { accountRunStates[account.phone] = AccountRunState.RUNNING },
                                            onPause = {
                                                if (accountRunStates[account.phone] != AccountRunState.STOPPED) {
                                                    accountRunStates[account.phone] = AccountRunState.PAUSED
                                                }
                                            },
                                            onStop = {
                                                accountRunStates[account.phone] = AccountRunState.STOPPED
                                                if (accountRunStates.values.none { it == AccountRunState.RUNNING || it == AccountRunState.PAUSED }) {
                                                    bookingJob?.cancel()
                                                    bookingJob = null
                                                }
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("سجل التنفيذ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                val combined = logs.joinToString(separator = "\n")
                                clipboard.setText(AnnotatedString(combined))
                                scope.launch { snackbarHost.showSnackbar("تم نسخ اللوج بالكامل") }
                            }) {
                                Text("نسخ اللوج")
                            }
                            Button(onClick = {
                                val latestIssue = logs.firstOrNull {
                                    it.contains("⛔") || it.contains("❌") || it.contains("🔑") ||
                                        it.contains("🌐") || it.contains("RateLimit")
                                }
                                if (latestIssue.isNullOrBlank()) {
                                    scope.launch { snackbarHost.showSnackbar("لا توجد مشكلة مسجلة حاليًا") }
                                } else {
                                    clipboard.setText(AnnotatedString(latestIssue))
                                    scope.launch { snackbarHost.showSnackbar("تم نسخ آخر مشكلة") }
                                }
                            }) {
                                Text("نسخ آخر مشكلة")
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(logs) { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تفاصيل الحجوزات الناجحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            if (successRecords.isEmpty()) {
                                scope.launch { snackbarHost.showSnackbar("لا توجد نتائج حجز لنسخها") }
                            } else {
                                val text = successRecords.joinToString("\n\n") { record ->
                                    "الإيميل: ${record.gateEmail}\n" +
                                        "الهاتف: ${record.phone}\n" +
                                        "الاسم: ${record.fullName}\n" +
                                        "الدائرة: ${record.officeName}\n" +
                                        "الموعد: ${record.bookingDate} ${record.slot}\n" +
                                        "وقت التسجيل: ${record.processedAt}"
                                }
                                clipboard.setText(AnnotatedString(text))
                                scope.launch { snackbarHost.showSnackbar("تم نسخ تفاصيل الحجوزات") }
                            }
                        }) {
                            Text("نسخ الكل")
                        }
                    }

                    if (successRecords.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                "لا توجد حجوزات ناجحة بعد. عند نجاح أي حجز ستظهر هنا كل التفاصيل.",
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(successRecords) { record ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("✅ ${record.fullName}", fontWeight = FontWeight.Bold)
                                        Text("📧 الإيميل: ${record.gateEmail}")
                                        Text("📱 الحساب: ${record.phone}")
                                        Text("🏢 الدائرة: ${record.officeName}")
                                        Text("📅 الموعد: ${record.bookingDate} ${record.slot}")
                                        Text("🕒 وقت التسجيل: ${record.processedAt}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
