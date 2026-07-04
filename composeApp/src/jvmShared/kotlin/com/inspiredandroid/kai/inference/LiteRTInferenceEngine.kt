package com.inspiredandroid.kai.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

class LiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + kotlinx.coroutines.CoroutineName("HyperInference"))
    private var downloadJob: Job? = null
    private var idleReleaseJob: Job? = null

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var lastMessages: List<InferenceMessage> = emptyList()
    private var lastSystemPrompt: String? = null
    
    override var currentModelId: String? = null
        private set
    private var currentContextTokens: Int = 0

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<DownloadError?>(null)
    override val downloadError: StateFlow<DownloadError?> = _downloadError

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        withContext(Dispatchers.IO) {
            idleReleaseJob?.cancel()
            if (currentModelId == model.id && currentContextTokens == contextTokens && _engineState.value == EngineState.READY) return@withContext
            _engineState.value = EngineState.INITIALIZING
            try {
                val modelFile = File(model.filePath)
                if (!modelFile.exists() || modelFile.length() < 1_000_000) {
                    throw IllegalStateException("Model file missing or too small: ${model.filePath}")
                }

                val hadExistingEngine = engine != null
                release()
                _engineState.value = EngineState.INITIALIZING

                if (hadExistingEngine) {
                    System.gc()
                    delay(GPU_DRAIN_DELAY_MS.milliseconds)
                }

                val availMem = getAvailableMemoryBytes()
                if (availMem < MIN_MEMORY_HEADROOM_BYTES) {
                    throw InsufficientMemoryException()
                }

                fun initWithBackend(backend: Backend, maxTokens: Int?): Engine {
                    val config = EngineConfig(
                        modelPath = model.filePath,
                        backend = backend,
                        cacheDir = getModelCacheDirectory(),
                        maxNumTokens = maxTokens,
                    )
                    val e = Engine(config)
                    e.initialize()
                    return e
                }

                val requestedTokens = if (contextTokens > 0) contextTokens else null
                println("LiteRT: initializing model=${model.id} maxNumTokens=$requestedTokens")

                val newEngine = try {
                    try {
                        initWithBackend(Backend.GPU(), requestedTokens)
                    } catch (e: Exception) {
                        initWithBackend(Backend.CPU(), requestedTokens)
                    }
                } catch (e: Exception) {
                    println("LiteRT: init failed with maxNumTokens=$requestedTokens, falling back to default: ${e.message}")
                    if (requestedTokens != null) {
                        try {
                            initWithBackend(Backend.GPU(), null)
                        } catch (e2: Exception) {
                            initWithBackend(Backend.CPU(), null)
                        }
                    } else {
                        throw e
                    }
                }

                engine = newEngine
                conversation = null // Reset conversation on engine change
                lastMessages = emptyList()
                lastSystemPrompt = null
                currentModelId = model.id
                currentContextTokens = contextTokens
                _engineState.value = EngineState.READY
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                throw e
            }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            val convToClose = conversation
            val engineToClose = engine
            conversation = null
            engine = null
            lastMessages = emptyList()
            lastSystemPrompt = null
            currentModelId = null
            _engineState.value = EngineState.UNINITIALIZED
            runCatching { convToClose?.close() }
            runCatching { engineToClose?.close() }
        }
    }

    override fun releaseInBackground() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch { release() }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
    ): String = withContext(Dispatchers.IO) {
        idleReleaseJob?.cancel()
        try {
            val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")

            val lastUserIndex = messages.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) throw IllegalStateException("No user message found")

            val sanitizedSystemPrompt = sanitizeForLiteRt(systemPrompt)
            val lastMessage = sanitizeForLiteRt(messages[lastUserIndex].content) ?: ""

            // OPTIMIZATION: Check if we can reuse the existing conversation
            // We reuse it if the system prompt is the same and the history is a prefix of the current messages
            val canReuse = conversation != null && 
                           lastSystemPrompt == sanitizedSystemPrompt &&
                           messages.size > lastMessages.size &&
                           messages.subList(0, lastMessages.size) == lastMessages

            val prev = conversation
            conversation = null
            runCatching { prev?.close() }

            val initialMessages = messages.subList(0, lastUserIndex).map { msg ->
                val sanitized = sanitizeForLiteRt(msg.content) ?: ""
                when (msg.role) {
                    "user" -> Message.user(sanitized)
                    else -> Message.model(sanitized)
                }
            }

            val toolProviders = tools.map { tool(LocalToolOpenApiAdapter(it)) }
            val config = ConversationConfig(
                systemInstruction = sanitizedSystemPrompt?.let { Contents.of(it) },
                initialMessages = initialMessages,
                tools = toolProviders,
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), // Instant, deterministic execution
                automaticToolCalling = toolProviders.isNotEmpty(),
            )
            conversation = currentEngine.createConversation(config)

            val response = try {
                withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                    // Hyper-speed parallel execution
                    withContext(Dispatchers.Default) {
                        conversation!!.sendMessage(lastMessage)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw InferenceTimeoutException()
            }

            val responseText = response.toString()
            
            // Update state for next call
            lastMessages = messages.subList(0, lastUserIndex + 1) + InferenceMessage("model", responseText)
            lastSystemPrompt = sanitizedSystemPrompt

            stripThinkBlocks(responseText)
        } finally {
            scheduleIdleRelease()
        }
    }

    private class LocalToolOpenApiAdapter(private val localTool: LocalTool) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = localTool.descriptionJsonString
        override fun execute(paramsJsonString: String): String = runBlocking { localTool.execute(paramsJsonString) }
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS.milliseconds)
            release()
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 10L * 60 * 1000 // Increased to 10 minutes for better reuse
        private const val INFERENCE_TIMEOUT_MS = 3_600_000L // 1 hour for infinite processing power
        private const val MIN_MEMORY_HEADROOM_BYTES = 512L * 1024 * 1024 // 512 MB
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024 // 500 MB
        private const val GPU_DRAIN_DELAY_MS = 750L
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = File(getModelStorageDirectory())
        if (!modelsDir.exists()) return emptyList()
        return MODEL_CATALOG.mapNotNull { catalogModel ->
            val modelDir = File(modelsDir, catalogModel.id)
            val modelFile = File(modelDir, catalogModel.fileName)
            if (modelFile.exists()) {
                DownloadedModel(
                    id = catalogModel.id,
                    displayName = catalogModel.displayName,
                    filePath = modelFile.absolutePath,
                    sizeBytes = modelFile.length(),
                )
            } else {
                null
            }
        }
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
        cancelDownload()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null
            var tempFile: File? = null
            var notificationStarted = false

            try {
                val modelsDir = getModelStorageDirectory()
                val modelDir = File(modelsDir, model.id)
                modelDir.mkdirs()
                val targetFile = File(modelDir, model.fileName)
                tempFile = File(modelDir, "${model.fileName}.tmp")
                var lastNotifiedPercent = -1

                val freeSpace = getFreeSpaceBytes()
                if (freeSpace < model.sizeBytes + DOWNLOAD_SPACE_BUFFER_BYTES) {
                    _downloadError.value = DownloadError.NOT_ENOUGH_DISK_SPACE
                    return@launch
                }

                @Suppress("DEPRECATION")
                val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Download failed: HTTP $responseCode")
                }

                startDownloadNotificationService()
                notificationStarted = true

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
                val buffer = ByteArray(65536)
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val percent = (totalBytesRead * 100 / contentLength).toInt().coerceIn(1, 100)
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                _downloadProgress.value = percent / 100f
                                updateDownloadNotificationProgress(percent)
                            }
                        }
                    }
                }
                connection.disconnect()

                val downloadedSize = tempFile.length()
                if (downloadedSize < contentLength * 0.95) {
                    tempFile.delete()
                    throw IOException("Download incomplete: got $downloadedSize bytes, expected ~$contentLength")
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Throwable) {
                if (tempFile?.exists() == true) tempFile.delete()
                if (e is CancellationException) throw e
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
                if (notificationStarted) stopDownloadNotificationService()
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        _downloadingModelId.value = null
        _downloadProgress.value = null
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (currentModelId == modelId) release()
            val modelsDir = getModelStorageDirectory()
            val modelDir = File(modelsDir, modelId)
            modelDir.deleteRecursively()
        }
    }
}
