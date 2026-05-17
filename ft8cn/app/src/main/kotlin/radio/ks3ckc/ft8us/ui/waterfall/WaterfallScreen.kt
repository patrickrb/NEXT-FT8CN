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
import androidx.lifecycle.Observer
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bg7yoz.ft8cn.timer.UtcTimer
import com.bg7yoz.ft8cn.ui.ColumnarView
import com.bg7yoz.ft8cn.ui.SpectrumFragment
import com.bg7yoz.ft8cn.ui.WaterfallView
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.TopBar

/**
 * Holder for view references using plain @Volatile fields.
 * Avoids Compose snapshot system overhead when accessed from callbacks.
 */
private class ViewHolder {
    @Volatile var columnar: ColumnarView? = null
    @Volatile var waterfall: WaterfallView? = null
    var frequencyLineTimeout: Int = 0 // plain field, only accessed from main thread
}

/**
 * Waterfall screen wrapping the existing Java WaterfallView and ColumnarView
 * via AndroidView.
 *
 * Audio data is fed to the views via observeForever on the existing
 * SpectrumListener LiveData. The observer runs on the main thread (LiveData
 * dispatches via Handler) and directly calls setWaveData + invalidate on the
 * views — exactly matching the old SpectrumFragment's drawSpectrum() pattern.
 */
@Composable
fun WaterfallScreen(mainViewModel: MainViewModel) {
    var touchedFreqHz by remember { mutableIntStateOf(-1) }
    var updateCount by remember { mutableIntStateOf(0) }

    val isDecoding by mainViewModel.mutableIsDecoding.observeAsState(false)
    var deNoise by remember { mutableStateOf(mainViewModel.deNoise) }
    var showMessages by remember { mutableStateOf(mainViewModel.markMessage) }

    // Plain volatile refs — no Compose snapshot overhead
    val viewHolder = remember { ViewHolder() }

    // Observe SpectrumListener's LiveData with observeForever.
    // The observer fires on the main thread every ~160ms, directly updating
    // both views. This is the exact same pattern as the old SpectrumFragment:
    //   spectrumListener.mutableDataBuffer.observe(...) { drawSpectrum(it) }
    DisposableEffect(Unit) {
        val observer = Observer<FloatArray> { data ->
            // Runs on MAIN THREAD (setValue dispatched via Handler.post)
            updateCount++
            val fft = IntArray(data.size / 2)
            nativeFFT(data, fft, mainViewModel.deNoise)

            viewHolder.columnar?.let { cView ->
                if (viewHolder.frequencyLineTimeout > 0) {
                    viewHolder.frequencyLineTimeout--
                }
                if (viewHolder.frequencyLineTimeout == 0) {
                    cView.setTouch_x(-1)
                    viewHolder.waterfall?.setTouch_x(-1)
                    touchedFreqHz = -1
                }
                cView.setWaveData(fft)
                cView.invalidate()
            }

            viewHolder.waterfall?.let { wView ->
                val currentlyDecoding = mainViewModel.mutableIsDecoding.value ?: false
                wView.setDrawMessage(mainViewModel.markMessage && !currentlyDecoding)
                val messages = if (mainViewModel.markMessage) mainViewModel.currentMessages else null
                wView.setWaveData(fft, UtcTimer.getNowSequential(), messages)
                wView.invalidate()
            }
        }
        mainViewModel.spectrumListener.mutableDataBuffer.observeForever(observer)
        onDispose {
            mainViewModel.spectrumListener.mutableDataBuffer.removeObserver(observer)
            viewHolder.columnar = null
            viewHolder.waterfall = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        TopBar(title = "Waterfall") {
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

        // Spectrum strip (columnar view)
        ColumnarStrip(
            onViewCreated = { viewHolder.columnar = it },
            onTouch = { freqHz, _ ->
                touchedFreqHz = freqHz
                viewHolder.frequencyLineTimeout = 60
            },
            onTouchUp = { freqHz ->
                if (freqHz > 0 && !GeneralVariables.synFrequency) {
                    mainViewModel.databaseOpr.writeConfig("freq", freqHz.toString(), null)
                    GeneralVariables.setBaseFrequency(freqHz.toFloat())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )

        FrequencyRuler(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 2.dp),
        )

        // Main waterfall display
        WaterfallCanvas(
            showMessages = showMessages,
            isDecoding = isDecoding,
            onViewCreated = { viewHolder.waterfall = it },
            onTouch = { freqHz, _ ->
                touchedFreqHz = freqHz
                viewHolder.frequencyLineTimeout = 60
            },
            onTouchUp = { freqHz ->
                if (freqHz > 0 && !GeneralVariables.synFrequency) {
                    mainViewModel.databaseOpr.writeConfig("freq", freqHz.toString(), null)
                    GeneralVariables.setBaseFrequency(freqHz.toFloat())
                }
            },
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
            Text(
                text = UtcTimer.getTimeStr(UtcTimer.getSystemTime()),
                color = TextMuted,
                fontFamily = GeistMonoFamily,
                fontSize = 10.5.sp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            ToggleChip(
                label = "NR",
                active = deNoise,
                onClick = {
                    deNoise = !deNoise
                    mainViewModel.deNoise = deNoise
                },
            )

            Spacer(modifier = Modifier.width(8.dp))

            ToggleChip(
                label = "MSG",
                active = showMessages,
                onClick = {
                    showMessages = !showMessages
                    mainViewModel.markMessage = showMessages
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "$updateCount",
                color = TextDim,
                fontFamily = GeistMonoFamily,
                fontSize = 9.sp,
            )

            Spacer(modifier = Modifier.width(6.dp))

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
    onViewCreated: (ColumnarView) -> Unit,
    onTouch: (freqHz: Int, x: Int) -> Unit,
    onTouchUp: (freqHz: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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

                onViewCreated(this)
            }
        },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Waterfall canvas (AndroidView wrapper)
// ---------------------------------------------------------------------------

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun WaterfallCanvas(
    showMessages: Boolean,
    isDecoding: Boolean,
    onViewCreated: (WaterfallView) -> Unit,
    onTouch: (freqHz: Int, x: Int) -> Unit,
    onTouchUp: (freqHz: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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

                onViewCreated(this)
            }
        },
        update = { view ->
            view.setDrawMessage(showMessages && !isDecoding)
        },
        modifier = modifier,
    )
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

private fun wfLog(msg: String) {
    try {
        val ctx = GeneralVariables.getMainContext() ?: return
        val dir = ctx.getExternalFilesDir(null) ?: return
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        File(dir, "debug.log").appendText("$ts $msg\n")
    } catch (_: Exception) {}
}

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
        } catch (e: UnsatisfiedLinkError) {
            wfLog("waterfall.FFT ERROR: native library not loaded! ${e.message}")
        }
    }
}

private fun nativeFFT(audioData: FloatArray, fftOut: IntArray, deNoise: Boolean) {
    FFTBridge.compute(audioData, fftOut, deNoise)
}
