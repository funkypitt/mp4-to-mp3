package app.mp4tomp3.core

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class Status { QUEUED, CONVERTING, DONE, FAILED, CANCELLED }

data class ConversionItem(
    val id: Long,
    val source: Uri,
    val sourceName: String,
    val outputName: String,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val outputUri: Uri? = null,
    val error: String? = null,
)

/**
 * The single source of truth for the current batch, shared between the
 * conversion service and the UI. Deliberately process-scoped and in-memory:
 * a batch is a one-shot thing, there is nothing worth persisting.
 */
object ConversionState {

    private val nextId = AtomicLong(1)

    private val _items = MutableStateFlow<List<ConversionItem>>(emptyList())
    val items: StateFlow<List<ConversionItem>> = _items.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun enqueue(entries: List<Pair<Uri, String>>) {
        if (entries.isEmpty()) return
        _items.update { current ->
            current + entries.map { (uri, name) ->
                ConversionItem(
                    id = nextId.getAndIncrement(),
                    source = uri,
                    sourceName = name,
                    outputName = Downloads.toMp3Name(name),
                )
            }
        }
    }

    fun nextQueued(): ConversionItem? = _items.value.firstOrNull { it.status == Status.QUEUED }

    fun update(id: Long, transform: (ConversionItem) -> ConversionItem) {
        _items.update { current ->
            current.map { if (it.id == id) transform(it) else it }
        }
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    /** Marks everything not yet finished as cancelled. */
    fun cancelPending() {
        _items.update { current ->
            current.map {
                if (it.status == Status.QUEUED || it.status == Status.CONVERTING) {
                    it.copy(status = Status.CANCELLED, progress = 0f)
                } else {
                    it
                }
            }
        }
    }

    fun clear() {
        _items.value = emptyList()
    }

    fun converted(): List<ConversionItem> =
        _items.value.filter { it.status == Status.DONE && it.outputUri != null }
}
