package com.example.udpmessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ── 색상 팔레트 ───────────────────────────────────────────────────────────────
private val BgDeep   = Color(0xFF0A0C0F)
private val BgSurf   = Color(0xFF111318)
private val BgPanel  = Color(0xFF161B22)
private val Border   = Color(0xFF21262D)
private val Green    = Color(0xFF39D353)
private val Blue     = Color(0xFF58A6FF)
private val Amber    = Color(0xFFFFA657)
private val Purple   = Color(0xFFBC8CFF)
private val Red      = Color(0xFFF85149)
private val TextPri  = Color(0xFFE6EDF3)
private val TextMut  = Color(0xFF7D8590)
private val Gold     = Color(0xFFFFD700)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UdpMessengerApp() }
    }
}

@Composable
fun UdpMessengerApp(vm: MainViewModel = viewModel()) {
    val state  by vm.state.collectAsStateWithLifecycle()
    val snack  = remember { SnackbarHostState() }
    val scope  = rememberCoroutineScope()

    LaunchedEffect(state.snack) {
        state.snack?.let {
            scope.launch { snack.showSnackbar(it, duration = SnackbarDuration.Long) }
            vm.clearSnack()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Green.copy(.12f), contentColor = Green,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(data.visuals.message, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // 상단 고정: 헤더 + 연결설정 + 버튼
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDeep)
                    .padding(horizontal = 14.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppHeader(state)
                ConnectionRow(state, vm)
                StatusBar(state)
                ActionButtons(state, vm)
                InfoChips()
                HorizontalDivider(color = Border, thickness = 1.dp)
            }

            // 하단 스크롤: 스케줄 목록 + 로그
            var tab by remember { mutableIntStateOf(0) }
            TabRow(
                selectedTabIndex = tab,
                containerColor = BgSurf,
                contentColor = Green,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("스케줄  (${state.schedules.size})", fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("로그  (${state.logs.size})", fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            }

            when (tab) {
                0 -> ScheduleList(state.schedules)
                1 -> LogList(state.logs, onClear = vm::clearLogs)
            }
        }
    }
}

// ── 헤더 ──────────────────────────────────────────────────────────────────────
@Composable
fun AppHeader(state: UiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Green, Color(0xFF26A641)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Send, null, tint = Color(0xFF0A0C0F), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("UDP Messenger", color = TextPri, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            Text("자동 스케줄러  v5.0  |  38 IMEI", color = TextMut,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        // 진행 카운터
        if (state.isRunning) {
            Surface(shape = RoundedCornerShape(8.dp), color = Amber.copy(.12f),
                border = BorderStroke(1.dp, Amber.copy(.3f))) {
                Text(
                    "⚡ ${state.shockDoneCount}/38  ✓ ${state.sensorDoneCount}/38",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Amber, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── 연결 설정 ─────────────────────────────────────────────────────────────────
@Composable
fun ConnectionRow(state: UiState, vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DarkField(
            modifier = Modifier.weight(2f), value = state.host,
            onValueChange = vm::onHostChange, label = "IP 주소",
            placeholder = "192.168.0.1", error = state.hostError,
            keyboardType = KeyboardType.Uri
        )
        DarkField(
            modifier = Modifier.weight(1f), value = state.port,
            onValueChange = vm::onPortChange, label = "포트",
            placeholder = "9999", error = state.portError,
            keyboardType = KeyboardType.Number
        )
    }
}

// ── 상태 바 ───────────────────────────────────────────────────────────────────
@Composable
fun StatusBar(state: UiState) {
    val (bg, fg, icon, text) = when {
        state.isRunning && state.shockDoneCount < 38 ->
            listOf(Amber.copy(.08f), Amber, "⚡", "실행 중 — SHOCK 대기: ${38 - state.shockDoneCount}건")
        state.isRunning ->
            listOf(Purple.copy(.08f), Purple, "⏱", "센서 전송 대기: ${38 - state.sensorDoneCount}건 (24h 1m 37s)")
        state.sensorDoneCount == 38 ->
            listOf(Green.copy(.08f), Green, "✓", "전체 완료 — 38개 IMEI 발송 완료")
        else ->
            listOf(BgPanel, TextMut, "●", "대기 중 — IP/포트 입력 후 [시작] 버튼을 누르세요")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg as Color,
        border = BorderStroke(1.dp, (fg as Color).copy(.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon as String, color = fg, fontSize = 14.sp)
            Text(text as String, color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── 액션 버튼 ─────────────────────────────────────────────────────────────────
@Composable
fun ActionButtons(state: UiState, vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = vm::startSchedule,
            enabled = !state.isRunning,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green, contentColor = Color(0xFF0A0C0F),
                disabledContainerColor = Green.copy(.3f), disabledContentColor = Color(0xFF0A0C0F).copy(.5f)
            )
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("자동 스케줄 시작", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        OutlinedButton(
            onClick = vm::cancelAll,
            modifier = Modifier.height(46.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Red.copy(.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) {
            Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("초기화", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

// ── 정보 칩 ───────────────────────────────────────────────────────────────────
@Composable
fun InfoChips() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        InfoChip("SHOCK: 0~60분 랜덤", Amber)
        InfoChip("+24h 1m 37s → SENSOR", Purple)
        InfoChip("LOAD: 1.10~2.20kg", Blue)
        InfoChip("BAT: ±0 or -0.10V", Green)
        InfoChip("HEX = ×100", TextMut)
    }
}

@Composable
fun InfoChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(.08f),
        border = BorderStroke(1.dp, color.copy(.25f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── 스케줄 목록 ───────────────────────────────────────────────────────────────
@Composable
fun ScheduleList(schedules: List<ImeiSchedule>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(schedules, key = { it.imei }) { sch ->
            ScheduleRow(sch)
        }
    }
}

@Composable
fun ScheduleRow(sch: ImeiSchedule) {
    val (accent, statusText) = when (sch.status) {
        ImeiStatus.IDLE          -> TextMut to "대기"
        ImeiStatus.SHOCK_PENDING -> Amber   to "SHOCK 대기"
        ImeiStatus.SHOCK_DONE    -> Gold    to "SHOCK ✓"
        ImeiStatus.COMPLETE      -> Green   to "완료 ✓"
        ImeiStatus.ERROR         -> Red     to "오류"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, accent.copy(.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 인덱스 + 상태 바
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d".format(sch.index + 1), color = TextMut,
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(2.dp))
                Box(modifier = Modifier.width(3.dp).height(32.dp)
                    .clip(CircleShape).background(accent))
            }

            // IMEI + 상태
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sch.imei, color = Blue, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(4.dp), color = accent.copy(.12f)) {
                        Text(statusText, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold)
                    }
                }

                // 시각 정보
                if (sch.status != ImeiStatus.IDLE) {
                    val shockStr  = if (sch.shockAt > 0) formatTime(sch.shockAt) else "—"
                    val sensorStr = if (sch.sensorAt > 0) formatTime(sch.sensorAt) else "—"
                    Text("⚡ $shockStr  →  ✓ $sensorStr",
                        color = TextMut, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                // 센서값 (완료 시)
                if (sch.status == ImeiStatus.COMPLETE && sch.batRaw > 0) {
                    val batF  = "%.2f".format(sch.batRaw  / 100.0)
                    val loadF = "%.2f".format(sch.loadRaw / 100.0)
                    Text("BAT ${batF}V  LOAD ${loadF}kg  |  ${sch.lastMsg.take(30)}…",
                        color = Purple, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // 남은 시간
            if (sch.remainMs > 0 && !sch.sensorDone) {
                Text(
                    formatRemain(sch.remainMs),
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── 로그 목록 ─────────────────────────────────────────────────────────────────
@Composable
fun LogList(logs: List<LogEntry>, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        if (logs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("로그 초기화", color = Red, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center) {
                        Text("로그가 없습니다", color = TextMut, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                items(logs, key = { it.id }) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
fun LogRow(entry: LogEntry) {
    val (color, label) = when (entry.type) {
        "SHOCK"  -> Green  to "SHOCK"
        "SENSOR" -> Purple to "SENSOR"
        "ERROR"  -> Red    to "ERROR"
        else     -> Blue   to "SYS"
    }
    Surface(shape = RoundedCornerShape(6.dp), color = BgPanel,
        border = BorderStroke(1.dp, color.copy(.15f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.width(3.dp).height(40.dp).clip(CircleShape).background(color))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("[$label]", color = color, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(entry.imei, color = Blue, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text(entry.time, color = TextMut, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Text(entry.message, color = TextPri, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.detail.isNotEmpty())
                    Text(entry.detail, color = TextMut, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── 공용 컴포넌트 ─────────────────────────────────────────────────────────────
@Composable
fun DarkField(
    modifier: Modifier = Modifier, value: String, onValueChange: (String) -> Unit,
    label: String, placeholder: String, error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMut, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextMut, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            singleLine = true, isError = error != null,
            supportingText = error?.let { { Text(it, color = Red, fontSize = 10.sp, fontFamily = FontFamily.Monospace) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue, unfocusedBorderColor = Border, errorBorderColor = Red,
                focusedTextColor = TextPri, unfocusedTextColor = TextPri, errorTextColor = TextPri,
                cursorColor = Blue, focusedContainerColor = BgPanel, unfocusedContainerColor = BgPanel,
                errorContainerColor = BgPanel
            ),
            shape = RoundedCornerShape(8.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )
    }
}

// ── 시간 포맷 헬퍼 ────────────────────────────────────────────────────────────
private val timeFmtDisp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
private val dateFmtDisp = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())

fun formatTime(ms: Long): String {
    val diff = ms - System.currentTimeMillis()
    return if (diff > 86_400_000L) dateFmtDisp.format(java.util.Date(ms))
    else timeFmtDisp.format(java.util.Date(ms))
}

fun formatRemain(ms: Long): String {
    if (ms <= 0) return "완료"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0  -> "%dh%02dm" .format(h, m)
        m > 0  -> "%dm%02ds" .format(m, sec)
        else   -> "%ds"      .format(sec)
    }
}
