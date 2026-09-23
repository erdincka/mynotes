package uk.kayalab.mynotes.recognition

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import uk.kayalab.mynotes.data.NoteRepository
import uk.kayalab.mynotes.data.SettingsRepository
import uk.kayalab.mynotes.ui.canvas.StrokeData
import uk.kayalab.mynotes.ui.canvas.StrokeGeometry
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ModelState {
    data object Checking : ModelState
    data object NotDownloaded : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * ML Kit digital ink recognition, fully on device once the language model is downloaded.
 * Recognised text is stored on the note so search can look inside handwriting.
 */
@Singleton
class HandwritingRecognizer @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val modelManager = RemoteModelManager.getInstance()
    private val model: DigitalInkRecognitionModel = DigitalInkRecognitionModel.builder(modelIdentifier()).build()
    private var recognizer: DigitalInkRecognizer? = null
    private val pending = HashMap<Long, Job>()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Checking)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    init {
        scope.launch { refreshModelState() }
    }

    suspend fun refreshModelState() {
        _modelState.value = runCatching { modelManager.isModelDownloaded(model).await() }
            .fold({ if (it) ModelState.Ready else ModelState.NotDownloaded }, { ModelState.Failed(it.message ?: "unknown") })
    }

    suspend fun downloadModel(): Result<Unit> {
        _modelState.value = ModelState.Downloading
        return runCatching { modelManager.download(model, DownloadConditions.Builder().build()).await(); Unit }
            .onSuccess { _modelState.value = ModelState.Ready }
            .onFailure {
                Timber.e(it, "Handwriting model download failed")
                _modelState.value = ModelState.Failed(it.message ?: "download failed")
            }
    }

    suspend fun deleteModel() {
        runCatching { modelManager.deleteDownloadedModel(model).await() }
        recognizer?.close()
        recognizer = null
        _modelState.value = ModelState.NotDownloaded
    }

    /** Debounced background pass after a save; a no-op unless the feature is on and the model is ready. */
    fun scheduleForNote(noteId: Long, strokes: List<StrokeData>) {
        pending[noteId]?.cancel()
        pending[noteId] = scope.launch {
            delay(DEBOUNCE_MS)
            if (!settingsRepository.handwritingSearch.first() || _modelState.value != ModelState.Ready) return@launch
            recognize(strokes).onSuccess { text ->
                runCatching { noteRepository.setRecognizedText(noteId, text) }
                    .onFailure { Timber.e(it, "Storing recognised text failed") }
            }
        }
    }

    /** Runs recognition on the handwriting in [strokes] and appends any typed text lines. */
    suspend fun recognize(strokes: List<StrokeData>): Result<String> = runCatching {
        if (_modelState.value != ModelState.Ready) error("The handwriting model is not downloaded.")
        val handwriting = strokes.filter { it.tool == "pen" || it.tool == "brush" }
        val typed = strokes.filter { it.isText }.sortedBy { it.points[0].y }.map { it.text!! }
        val recognised = if (handwriting.isEmpty()) "" else {
            val builder = Ink.builder()
            for (stroke in handwriting) {
                val ink = Ink.Stroke.builder()
                stroke.points.forEach { ink.addPoint(Ink.Point.create(it.x, it.y)) }
                builder.addStroke(ink.build())
            }
            val bounds = StrokeGeometry.boundingBox(handwriting)
            val context = RecognitionContext.builder()
                .setWritingArea(WritingArea(bounds?.width ?: 1000f, bounds?.height ?: 1000f))
                .build()
            val client = recognizer ?: DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            ).also { recognizer = it }
            client.recognize(builder.build(), context).await().candidates.firstOrNull()?.text.orEmpty()
        }
        (listOf(recognised) + typed).filter { it.isNotBlank() }.joinToString("\n")
    }.onFailure { Timber.e(it, "Recognition failed") }

    private fun modelIdentifier(): DigitalInkRecognitionModelIdentifier =
        runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-GB") }.getOrNull()
            ?: DigitalInkRecognitionModelIdentifier.EN_US

    private companion object {
        const val DEBOUNCE_MS = 4000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
