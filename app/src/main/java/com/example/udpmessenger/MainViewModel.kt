package com.example.udpmessenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

// ── 상태 열거 ──────────────────────────────────────────────────────────────────
enum class ImeiStatus { IDLE, SHOCK_PENDING, SHOCK_DONE, COMPLETE, ERROR }

// ── 각 IMEI 스케줄 정보 ────────────────────────────────────────────────────────
data class ImeiSchedule(
    val imei: String,
    val index: Int,
    val status: ImeiStatus = ImeiStatus.IDLE,
    val shockAt: Long  = 0L,   // System.currentTimeMillis()
    val sensorAt: Long = 0L,
    val shockDone: Boolean  = false,
    val sensorDone: Boolean = false,
    val batRaw: Int  = 0,      // round(x,2)×100 정수  예) 3.72 → 372
    val loadRaw: Int = 0,      // round(x,2)×100 정수  예) 1.75 → 175
    val lastMsg: String = "",
    val remainMs: Long = 0L
)

// ── 로그 항목 ──────────────────────────────────────────────────────────────────
data class LogEntry(
    val id: Long = System.nanoTime(),
    val time: String,
    val imei: String,
    val type: String,          // "SHOCK" | "SENSOR" | "ERROR" | "SYSTEM"
    val message: String,
    val detail: String = ""
)

// ── 전체 UI 상태 ───────────────────────────────────────────────────────────────
data class UiState(
    val host: String = "192.168.0.1",
    val port: String = "9999",
    val hostError: String? = null,
    val portError: String? = null,
    val isRunning: Boolean = false,
    val schedules: List<ImeiSchedule> = ImeiData.list.mapIndexed { i, imei ->
        ImeiSchedule(imei, i)
    },
    val logs: List<LogEntry> = emptyList(),
    val shockDoneCount: Int  = 0,
    val sensorDoneCount: Int = 0,
    val snack: String? = null
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    // 이전 배터리값 저장 (imei → batRaw)
    private val prevBat = mutableMapOf<String, Int>()

    // 각 IMEI 별 Job
    private val jobs = mutableMapOf<String, Job>()
    // 카운트다운 갱신 Job
    private var tickJob: Job? = null

    fun onHostChange(v: String) = _state.update { it.copy(host = v, hostError = null) }
    fun onPortChange(v: String) = _state.update { it.copy(port = v, portError = null) }
    fun clearSnack()            = _state.update { it.copy(snack = null) }
    fun clearLogs()             = _state.update { it.copy(logs = emptyList()) }

    // ── 스케줄 시작 ────────────────────────────────────────────────────────────
    fun startSchedule() {
        val s = _state.value
        val hostErr = if (s.host.isBlank()) "호스트를 입력하세요" else null
        val port    = s.port.toIntOrNull()
        val portErr = if (port == null || port !in 1..65535) "1 ~ 65535" else null
        if (hostErr != null || portErr != null) {
            _state.update { it.copy(hostError = hostErr, portError = portErr) }
            return
        }

        cancelAll()
        prevBat.clear()

        val now = System.currentTimeMillis()
        val newSchedules = ImeiData.list.mapIndexed { i, imei ->
            val delayMs  = Random.nextLong(0, ImeiData.SHOCK_MAX_DELAY_MS + 1)
            val shockAt  = now + delayMs
            val sensorAt = shockAt + ImeiData.SENSOR_DELAY_MS
            ImeiSchedule(
                imei     = imei,
                index    = i,
                status   = ImeiStatus.SHOCK_PENDING,
                shockAt  = shockAt,
                sensorAt = sensorAt,
                remainMs = delayMs
            )
        }

        _state.update { it.copy(
            isRunning       = true,
            schedules       = newSchedules,
            shockDoneCount  = 0,
            sensorDoneCount = 0,
            logs            = listOf(LogEntry(
                time    = timeFmt.format(Date()),
                imei    = "SYSTEM",
                type    = "SYSTEM",
                message = "스케줄 시작: 38개 IMEI 등록",
                detail  = "SHOCK: 0~60분 랜덤 | SENSOR: +24h 1m 37s"
            ))
        )}

        // 각 IMEI 별 코루틴 실행
        newSchedules.forEach { sch -> launchImeiJob(sch, s.host, port!!) }

        // 카운트다운 갱신 (1초마다)
        startTick()
    }

    private fun launchImeiJob(sch: ImeiSchedule, host: String, port: Int) {
        val job = viewModelScope.launch {
            // 1) SHOCK 대기
            val shockDelay = sch.shockAt - System.currentTimeMillis()
            if (shockDelay > 0) delay(shockDelay)

            val shockMsg = ImeiData.shockMessage(sch.imei)
            val shockResult = UdpRepository.send(host, port, shockMsg)

            updateSchedule(sch.imei) { it.copy(
                status    = if (shockResult.success) ImeiStatus.SHOCK_DONE else ImeiStatus.ERROR,
                shockDone = shockResult.success
            )}
            addLog(LogEntry(
                time    = timeFmt.format(Date()),
                imei    = sch.imei,
                type    = if (shockResult.success) "SHOCK" else "ERROR",
                message = shockMsg,
                detail  = if (shockResult.success) "${shockResult.bytesSent}B" else shockResult.error
            ))
            _state.update { it.copy(shockDoneCount = it.shockDoneCount + 1) }

            if (!shockResult.success) return@launch

            // 2) SENSOR 대기 (shockAt 기준으로 정확히 86497초 후)
            val sensorDelay = sch.sensorAt - System.currentTimeMillis()
            if (sensorDelay > 0) delay(sensorDelay)

            val sensorMsg = buildSensorMessage(sch.imei)
            val sensorResult = UdpRepository.send(host, port, sensorMsg)

            val batRaw  = prevBat[sch.imei] ?: 0
            val loadRaw = extractLoadRaw(sensorMsg)

            updateSchedule(sch.imei) { it.copy(
                status     = if (sensorResult.success) ImeiStatus.COMPLETE else ImeiStatus.ERROR,
                sensorDone = sensorResult.success,
                batRaw     = batRaw,
                loadRaw    = loadRaw,
                lastMsg    = sensorMsg
            )}
            addLog(LogEntry(
                time    = timeFmt.format(Date()),
                imei    = sch.imei,
                type    = if (sensorResult.success) "SENSOR" else "ERROR",
                message = sensorMsg,
                detail  = if (sensorResult.success) {
                    val bF = "%.2f".format(batRaw / 100.0)
                    val lF = "%.2f".format(loadRaw / 100.0)
                    "BAT=${bF}V LOAD=${lF}kg  ${sensorResult.bytesSent}B"
                } else sensorResult.error
            ))
            _state.update { it.copy(sensorDoneCount = it.sensorDoneCount + 1) }
        }
        jobs[sch.imei] = job
    }

    // ── 센서 메시지 생성 ─────────────────────────────────────────────────────
    private fun buildSensorMessage(imei: String): String {
        // BAT: 이전값 있으면 같거나 -0.10V (50%), 없으면 3.40~4.00 랜덤
        val batRaw = if (prevBat.containsKey(imei)) {
            val prev = prevBat[imei]!!
            val drop = if (Random.nextBoolean()) 10 else 0   // -0.10 = -10raw
            (prev - drop).coerceAtLeast(300)                  // 최솟값 3.00V
        } else {
            Random.nextInt(340, 401)   // 3.40~4.00 × 100
        }
        prevBat[imei] = batRaw

        // LOAD: 1.10~2.20 랜덤 (0.01 단위)  → ×100 = 110~220
        val loadRaw = Random.nextInt(110, 221)

        val batHex  = "%08X".format(batRaw)
        val loadHex = "%08X".format(loadRaw)
        return "$imei 0001 $batHex BAT_LEVEL 0012 $loadHex LOAD_CELL"
    }

    private fun extractLoadRaw(msg: String): Int {
        return try {
            val parts = msg.split(" ")
            parts[5].toInt(16)
        } catch (e: Exception) { 0 }
    }

    // ── 카운트다운 갱신 ────────────────────────────────────────────────────────
    private fun startTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(1000L)
                val now = System.currentTimeMillis()
                _state.update { s ->
                    val updated = s.schedules.map { sch ->
                        val rem = when {
                            sch.sensorDone || sch.status == ImeiStatus.ERROR -> 0L
                            !sch.shockDone -> (sch.shockAt - now).coerceAtLeast(0L)
                            else           -> (sch.sensorAt - now).coerceAtLeast(0L)
                        }
                        sch.copy(remainMs = rem)
                    }
                    val allDone = updated.all { it.sensorDone || it.status == ImeiStatus.ERROR }
                    s.copy(
                        schedules = updated,
                        isRunning = !allDone,
                        snack     = if (allDone) "✓ 전체 완료! 38개 IMEI 발송 완료" else null
                    )
                }
            }
        }
    }

    // ── 전체 취소 ──────────────────────────────────────────────────────────────
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        tickJob?.cancel()
        prevBat.clear()
        _state.update { it.copy(
            isRunning  = false,
            schedules  = ImeiData.list.mapIndexed { i, imei -> ImeiSchedule(imei, i) },
            shockDoneCount  = 0,
            sensorDoneCount = 0
        )}
    }

    private fun updateSchedule(imei: String, transform: (ImeiSchedule) -> ImeiSchedule) {
        _state.update { s ->
            s.copy(schedules = s.schedules.map { if (it.imei == imei) transform(it) else it })
        }
    }

    private fun addLog(entry: LogEntry) {
        _state.update { it.copy(logs = listOf(entry) + it.logs.take(199)) }
    }

    override fun onCleared() {
        super.onCleared()
        jobs.values.forEach { it.cancel() }
        tickJob?.cancel()
    }
}
