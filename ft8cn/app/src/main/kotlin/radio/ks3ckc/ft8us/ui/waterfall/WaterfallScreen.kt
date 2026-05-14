package radio.ks3ckc.ft8us.ui.waterfall

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.timer.UtcTimer
import com.bg7yoz.ft8cn.ui.ColumnarView
import com.bg7yoz.ft8cn.ui.SpectrumFragment
import com.bg7yoz.ft8cn.ui.WaterfallView
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.TopBar

/**
 * Waterfall screen wrapping the existing Java WaterfallView and ColumnarView
 * via AndroidView. This preserves the optimized bitmap scrolling and JNI FFT
 * rendering while providing a Compose-native chrome around it.
 */
@Composable
fun WaterfallScreen(mainViewModel: MainViewModel) {
    // Track frequency touch state
    var touchedFreqHz by remember { mutableIntStateOf(-1) }
    var frequencyLineTimeout by remember { mutableIntStateOf(0) }

    // Observe spectrum data from the recorder
    val spectrumData by mainViewModel.spectrumListener.mutableDataBuffer.observeAsState()

    // Observe decode state to control message overlay
    val isDecoding by mainViewModel.mutableIsDecoding.observeAsState(false)

    // Noise suppression and message display toggles
    var deNoise by remember { mutableStateOf(mainViewModel.deNoise) }
    var showMessages by remember { mutableStateOf(mainViewModel.markMessage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        // Header
        TopBar(title = "Waterfall") {
            // Frequency display
            val freqText = if (touchedFreqHz > 0) {
                "$touchedFreqHz Hz"
            } else {
                GeneralVariables.getBaseFrequencyStr() + " Hz"
            }
            Text(
                text = freqText,
                color = Signal,
                fontFamily = GeistMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Spectrum strip (columnar view) — live bar chart
        ColumnarStrip(
            mainViewModel = mainViewModel,
            spectrumData = spectrumData,
            deNoise = deNoise,
            touchedFreqHz = touchedFreqHz,
            frequencyLineTimeout = frequencyLineTimeout,
            onTouch = { freqHz, x ->
                touchedFreqHz = freqHz
                frequencyLineTimeout = 60
            },
            onTouchUp = { freqHz ->
                if (freqHz > 0 && !GeneralVariables.synFrequency) {
                    mainViewModel.databaseOpr.writeConfig(
                        "freq",
                        freqHz.toString(),
                        null,
                    )
                    GeneralVariables.setBaseFrequency(freqHz.toFloat())
                }
            },
            onFrequencyTimeout = {
                touchedFreqHz = -1
                frequencyLineTimeout = 0
            },
            onTimeoutTick = { frequencyLineTimeout = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )

        // Frequency ruler labels
        FrequencyRuler(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 2.dp),
        )

        // Main waterfall display
        WaterfallCanvas(
            mainViewModel = mainViewModel,
            spectrumData = spectrumData,
            isDecoding = isDecoding,
            showMessages = showMessages,
            touchedFreqHz = touchedFreqHz,
            frequencyLineTimeout = frequencyLineTimeout,
            onTouch = { freqHz, x ->
                touchedFreqHz = freqHz
                frequencyLineTimeout = 60
            },
            onTouchUp = { freqHz ->
                if (freqHz > 0 && !GeneralVariables.synFrequency) {
                    mainViewModel.databaseOpr.writeConfig(
                        "freq",
                        freqHz.toString(),
                        null,
                    )
                    GeneralVariables.setBaseFrequency(freqHz.toFloat())
                }
            },
            onFrequencyTimeout = {
                touchedFreqHz = -1
                frequencyLineTimeout = 0
            },
            onTimeoutTick = { frequencyLineTimeout = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        // Bottom info strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // UTC time
            Text(
                text = UtcTimer.getTimeStr(UtcTimer.getSystemTime()),
                color = TextMuted,
                fontFamily = GeistMonoFamily,
                fontSize = 10.5.sp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Noise filter toggle chip
            ToggleChip(
                label = "NR",
                active = deNoise,
                onClick = {
                    deNoise = !deNoise
                    mainViewModel.deNoise = deNoise
                },
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Message overlay toggle chip
            ToggleChip(
                label = "MSG",
                active = showMessages,
                onClick = {
                    showMessages = !showMessages
                    mainViewModel.markMessage = showMessages
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            // Live indicator
            Text(
                text = "LIVE",
                color = StatusConfirmed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Columnar spectrum strip (AndroidView wrapper)
// ---------------------------------------------------------------------------

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun ColumnarStrip(
    mainViewModel: MainViewModel,
    spectrumData: FloatArray?,
    deNoise: Boolean,
    touchedFreqHz: Int,
    frequencyLineTimeout: Int,
    onTouch: (freqHz: Int, x: Int) -> Unit,
    onTouchUp: (freqHz: Int) -> Unit,
    onFrequencyTimeout: () -> Unit,
    onTimeoutTick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var columnarViewRef by remember { mutableStateOf<ColumnarView?>(null) }

    AndroidView(
        factory = { context ->
            ColumnarView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF07090F.toInt())
                setShowBlock(true)

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            setTouch_x(event.x.toInt())
                            val freq = getFreq_hz()
                            if (freq > 0) onTouch(freq, event.x.toInt())
                        }
                        MotionEvent.ACTION_UP -> {
                            val freq = getFreq_hz()
                            if (freq > 0) onTouchUp(freq)
                        }
                    }
                    true
                }

                columnarViewRef = this
            }
        },
        update = { view ->
            spectrumData?.let { data ->
                val fft = IntArray(data.size)
                nativeFFT(data, fft, deNoise)

                var timeout = frequencyLineTimeout - 1
                if (timeout < 0) timeout = 0
                onTimeoutTick(timeout)
                if (timeout == 0) {
                    view.setTouch_x(-1)
                    onFrequencyTimeout()
                }

                view.setWaveData(fft)
                view.invalidate()
            }
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) {
        onDispose {
            columnarViewRef = null
        }
    }
}

// ---------------------------------------------------------------------------
// Waterfall canvas (AndroidView wrapper)
// ---------------------------------------------------------------------------

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun WaterfallCanvas(
    mainViewModel: MainViewModel,
    spectrumData: FloatArray?,
    isDecoding: Boolean,
    showMessages: Boolean,
    touchedFreqHz: Int,
    frequencyLineTimeout: Int,
    onTouch: (freqHz: Int, x: Int) -> Unit,
    onTouchUp: (freqHz: Int) -> Unit,
    onFrequencyTimeout: () -> Unit,
    onTimeoutTick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var waterfallViewRef by remember { mutableStateOf<WaterfallView?>(null) }

    AndroidView(
        factory = { context ->
            WaterfallView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF000000.toInt())
                setDrawMessage(showMessages && !isDecoding)

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            setTouch_x(event.x.toInt())
                            val freq = getFreq_hz()
                            if (freq > 0) onTouch(freq, event.x.toInt())
                        }
                        MotionEvent.ACTION_UP -> {
                            val freq = getFreq_hz()
                            if (freq > 0) onTouchUp(freq)
                        }
                    }
                    true
                }

                waterfallViewRef = this
            }
        },
        update = { view ->
            view.setDrawMessage(showMessages && !isDecoding)

            spectrumData?.let { data ->
                val fft = IntArray(data.size)
                nativeFFT(data, fft, mainViewModel.deNoise)

                val messages = if (showMessages) mainViewModel.currentMessages else null
                view.setWaveData(fft, UtcTimer.getNowSequential(), messages)
                view.invalidate()
            }
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) {
        onDispose {
            waterfallViewRef = null
        }
    }
}

// ---------------------------------------------------------------------------
// Frequency ruler (pure Compose)
// ---------------------------------------------------------------------------

@Composable
private fun FrequencyRuler(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(BgSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labels = listOf("0", "500", "1000", "1500", "2000", "2500", "3000")
        labels.forEachIndexed { index, label ->
            if (index > 0) Spacer(modifier = Modifier.weight(1f))
            Text(
                text = label,
                color = TextDim,
                fontFamily = GeistMonoFamily,
                fontSize = 8.sp,
                letterSpacing = 0.02.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Toggle chip for bottom controls
// ---------------------------------------------------------------------------

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) AccentSoft else BgSurface3
    val textColor = if (active) Accent else TextFaint

    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = textColor,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.sp,
    )
}

// ---------------------------------------------------------------------------
// Native FFT bridge — delegates to SpectrumFragment's JNI methods
// ---------------------------------------------------------------------------

/**
 * Singleton FFT bridge. The native methods are bound to SpectrumFragment's
 * class via JNI (Java_com_bg7yoz_ft8cn_ui_SpectrumFragment_*), so we
 * instantiate one SpectrumFragment to call through.
 *
 * SpectrumFragment's static initializer loads the "ft8cn" native library.
 */
private object FFTBridge {
    private val fragment: SpectrumFragment by lazy { SpectrumFragment() }

    fun compute(audioData: FloatArray, fftOut: IntArray, deNoise: Boolean) {
        try {
            if (deNoise) {
                fragment.getFFTDataFloat(audioData, fftOut)
            } else {
                fragment.getFFTDataRawFloat(audioData, fftOut)
            }
        } catch (_: UnsatisfiedLinkError) {
            // Native library not loaded; leave fftOut zeroed
        }
    }
}

private fun nativeFFT(audioData: FloatArray, fftOut: IntArray, deNoise: Boolean) {
    FFTBridge.compute(audioData, fftOut, deNoise)
}
