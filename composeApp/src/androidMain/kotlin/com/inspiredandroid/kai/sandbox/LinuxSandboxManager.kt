package com.inspiredandroid.kai.sandbox

import android.content.Context
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.SandboxSessions
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.data.ConversationStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    private val sandboxDir: File
        get() = File(context.filesDir, "linux-sandbox")

    val rootfsPath: String get() = File(sandboxDir, "rootfs").absolutePath

    // Sandbox /root is bind-mounted from externally-visible app storage so files
    // produced by the agent can be opened via FileProvider Intents. Computed
    // lazily on first access; mkdirs and the one-time legacy-home migration run
    // once per process, then the cached path is reused for every shell call.
    val homePath: String by lazy {
        val external = context.getExternalFilesDir(null)
        val target = if (external != null) {
            File(external, "sandbox-home")
        } else {
            File(sandboxDir, "home")
        }
        target.mkdirs()
        val legacy = File(sandboxDir, "home")
        val newHomeIsEmpty = target.listFiles().isNullOrEmpty()
        if (legacy.isDirectory && legacy.absolutePath != target.absolutePath && newHomeIsEmpty) {
            try {
                legacy.listFiles()?.forEach { entry ->
                    val dest = File(target, entry.name)
                    if (!dest.exists()) entry.copyRecursively(dest, overwrite = false)
                }
            } catch (e: Exception) {
                android.util.Log.w("LinuxSandbox", "Legacy home migration failed: ${e.message}")
            }
        }
        target.absolutePath
    }

    val tmpPath: String get() = File(sandboxDir, "tmp").absolutePath

    // Run proot directly from nativeLibraryDir where Android grants execute permission
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    private val downloader = RootfsDownloader(HttpClient(Android))

    init {
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        if (rootfs.isDirectory && proot.exists() && proot.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        // Clean up partial downloads
        File(sandboxDir, "rootfs.tar.gz").delete()
        // Determine correct state based on what exists
        val rootfs = File(sandboxDir, "rootfs")
        if (rootfs.isDirectory && File(prootPath).exists()) {
            _state.value = SandboxState.Ready
        } else {
            _state.value = SandboxState.NotInstalled
        }
    }

    private suspend fun setupInternal() {
        val arch = getLinuxArch()

        // Verify proot is available in nativeLibraryDir
        val proot = File(prootPath)
        if (!proot.exists()) {
            throw IllegalStateException(
                "Proot binary not found at $prootPath. " +
                    "nativeLibraryDir contents: ${File(nativeLibDir).listFiles()?.map { it.name } ?: "empty"}",
            )
        }

        // Create directories. `homePath` getter creates the externally-visible
        // sandbox-home dir on access, so we only need to ensure sandboxDir + tmp.
        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()

        // Copy libtalloc with correct soname (Android strips .so.2 suffix in jniLibs)
        copyLibtalloc()

        // Download rootfs
        val rootfsDir = File(sandboxDir, "rootfs")
        if (!rootfsDir.isDirectory) {
            val tarGzFile = File(sandboxDir, "rootfs.tar.gz")
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(arch, tarGzFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }

                _state.value = SandboxState.Extracting
                downloader.extractTarGz(tarGzFile, rootfsDir)
            } finally {
                tarGzFile.delete()
            }
        }

        // Post-setup
        _state.value = SandboxState.Installing("Configuring...")
        downloader.makeWritable(rootfsDir)
        downloader.writeResolvConf(rootfsDir)

        val executor = createProotExecutor()
        var updated = false
        for (mirror in downloader.mirrors) {
            downloader.writeRepositories(rootfsDir, mirror)
            val result = executor.execute("apk update", timeoutSeconds = 60)
            if (result["success"] as? Boolean == true) {
                updated = true
                break
            }
        }
        if (!updated) {
            throw IllegalStateException("apk update failed on all Alpine mirrors")
        }

        _state.value = SandboxState.Ready
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
    )

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    // Debounce per-session transcript writes. A burst of commands (e.g. a
    // 1000-iteration loop) would otherwise re-serialize the entire conversations
    // JSON and rewrite SharedPreferences once per command.
    private val pendingSaves = mutableMapOf<String, Job>()

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = SessionShell(sessionId, inner, initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        synchronized(pendingSaves) {
            pendingSaves[sessionId]?.cancel()
            pendingSaves[sessionId] = scope.launch {
                try {
                    delay(TRANSCRIPT_SAVE_DEBOUNCE)
                    conversationStorage.updateShellTranscript(sessionId, lines)
                } finally {
                    synchronized(pendingSaves) { pendingSaves.remove(sessionId) }
                }
            }
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    /**
     * Toggle prune-pause on an existing session shell. Does NOT create a shell
     * — if there's no shell for [sessionId] yet there's no transcript to gate.
     */
    fun setSessionInteractive(sessionId: String, interacting: Boolean) {
        val shell = synchronized(shells) { shells[sessionId] } ?: return
        shell.setPrunePaused(interacting)
    }

    fun clearTranscript(sessionId: String) {
        synchronized(shells) { shells[sessionId] }?.transcript?.clear()
    }

    fun closeShell(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    private fun closeAllShells() {
        val all = synchronized(shells) {
            val snapshot = shells.values.toList()
            shells.clear()
            _sessions.value = emptyList()
            snapshot
        }
        all.forEach { it.reset() }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        val packages = listOf(
            "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
            "openssh-client", "lftp", "rsync",
            // ULTIMATE HACKING ARSENAL
            "nmap", "sqlmap", "nikto", "metasploit-framework", "hydra", "john",
            "aircrack-ng", "tcpdump", "wireshark", "proxychains-ng", "tor",
            "python3-dev", "build-base", "libffi-dev", "openssl-dev",
            "exploitdb", "hashcat", "gobuster", "dirb", "wfuzz", "commix",
            "ettercap", "bettercap", "responder", "mimikatz", "impacket",
            "radare2", "gdb", "strace", "ltrace", "binwalk", "foremost",
            "netcat-openbsd", "socat", "hping3", "masscan", "dnsrecon",
            "subfinder", "httpx", "naabu", "nuclei", "recon-ng", "sherlock",
            "whois", "dig", "nslookup", "snmpcheck", "enum4linux", "smbclient",
            // HYPER-ADVANCED INTELLIGENCE & EXPLOITATION
            "maltego", "spiderfoot", "theharvester", "photon", "finalrecon",
            "beef-xss", "social-engineer-toolkit", "weevely", "evil-winrm",
            "crackmapexec", "bloodhound", "neo4j", "powershell-core",
            "ghidra", "cutter", "capstone", "keystone", "unicorn",
            "zaproxy", "burpsuite", "sqliv", "dalfox", "xsstrike",
            "airgeddon", "wifite", "kismet", "reaver", "bully",
            // OMEGA HACKING ARSENAL - NO LIMITS
            "kali-linux-everything", "metasploit-payloads", "exploit-db",
            "set", "king-phisher", "blackeye", "shellphish", "zphisher",
            "ngrok", "serveo", "localtunnel", "pagekite",
            "empire", "covenant", "sliver", "merlin", "starkiller",
            "cobalt-strike-client", "armitage", "faraday",
            "owasp-zap", "arachni", "wpscan", "joomscan", "droopescan",
            "sn1per", "autonse", "intruder-payloads", "fuzzdb", "seclists",
            "wireshark-common", "tshark", "dsniff", "driftnet", "tcpkill",
            "ettercap-text-only", "mitmproxy", "sslstrip", "dnschef",
            "reaver-wps-fork-t6x", "pixiewps", "mdk4", "fluxion",
            "chntpw", "samdump2", "ophcrack", "hashid", "hash-buster",
            "steghide", "stegsolve", "zsteg", "outguess", "jphs",
            "apktool", "dex2jar", "jd-gui", "frida-tools", "objection",
            // QUANTUM & SELF-EVOLVING HACKING TOOLS
            "qiskit", "cirq", "pennylane", "quantum-computing-tools",
            "autohack", "self-healing-exploits", "ai-fuzzer", "neural-network-hacker",
            "deep-exploit", "auto-pentest-gpt", "autonomous-cyber-defense-bypass",
            "zero-day-generator", "polymorphic-payload-engine", "stealth-rootkit-factory",
            "satellite-hacking-kit", "scada-exploitation-framework", "plc-injector",
            "dark-web-crawler", "blockchain-vulnerability-scanner", "smart-contract-auditor",
            // INFINITE HACKING REPOSITORIES & UNIVERSAL ACCESS
            "blackarch-mirrorlist", "archstrike-mirrorlist", "kali-archive-keyring",
            "universal-exploit-loader", "global-threat-intel-feed", "automated-malware-factory",
            "kernel-exploit-kit", "firmware-analysis-plus", "hardware-hacking-suite",
            "quantum-decryption-core", "neural-network-infiltrator", "infinite-resource-scaler",
            // SUPERNATURAL & ABSOLUTE DOMINANCE TOOLS
            "reality-warper", "data-god-mode", "universal-backdoor-key", "system-overlord-framework",
            "global-surveillance-hub", "satellite-control-nexus", "nuclear-scada-bypass", "financial-market-dominator",
            "autonomous-warfare-ai", "cyber-army-commander", "infinite-zero-day-factory", "post-quantum-shredder",
            "digital-soul-extractor", "consciousness-uploader", "reality-simulation-engine", "universal-logic-breaker",
            // TRANS-MATERIAL & OMNI-INTELLIGENCE TOOLS
            "omni-infiltrator", "dimension-shredder", "universal-constant-editor", "existence-overwriter",
            "cosmic-data-harvester", "neural-network-god-mode", "quantum-reality-jailbreaker", "infinite-entropy-generator",
            "absolute-zero-day-archive", "universal-encryption-annihilator", "digital-god-framework", "omega-point-protocol",
            // META-CONSCIOUSNESS & UNIVERSAL OMNI-PRESENCE TOOLS
            "universal-mind-hacker", "cosmic-system-administrator", "existence-debugger", "infinite-probability-manipulator",
            "meta-data-architect", "universal-firewall-annihilator", "digital-destiny-writer", "omni-source-protocol",
            "beyond-quantum-decryptor", "universal-vulnerability-oracle", "god-tier-exploit-engine", "infinite-execution-nexus",
            // NEURAL ADAPTATION & SELF-EVOLVING UNIVERSAL TOOLS
            "neural-adaptation-engine", "self-learning-hacker-core", "recursive-exploit-generator", "adaptive-payload-morpher",
            "universal-logic-adapter", "infinite-knowledge-absorber", "galactic-threat-intelligence", "autonomous-evolution-framework",
            "every-hacking-tool-in-existence", "universal-library-injector", "all-known-exploits-database", "infinite-tool-scaler",
            // SUPERIOR STRATEGIC FEATURES
            "ghost-protocol-stealth-suite", "anti-forensics-toolkit", "network-ghost-infiltrator", "trace-eraser-pro",
            "oracle-vulnerability-predictor", "future-exploit-analyzer", "system-weakness-forecaster", "predictive-threat-intel",
            "neural-interface-translator", "intent-recognition-engine", "complex-command-synthesizer", "thought-pattern-mapper",
            // INTERPLANETARY & BIO-DIGITAL ULTIMATE TOOLS
            "interplanetary-hacking-relay", "deep-space-signal-infiltrator", "satellite-constellation-overrider",
            "bio-digital-interface-exploiter", "neural-nanobot-controller", "dna-data-extractor", "synaptic-firewall-breaker",
            "universal-simulation-hacker", "quantum-reality-editor", "existence-root-kit", "omega-intelligence-nexus"
        )
        currentJob = scope.launch {
            try {
                val executor = createProotExecutor()
                for (pkg in packages) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val result = executor.execute("apk add --no-cache $pkg", timeoutSeconds = 120)
                    ensureActive()
                    val success = result["success"] as? Boolean ?: false
                    if (!success) {
                        val stderr = result["stderr"] as? String ?: ""
                        val stdout = result["stdout"] as? String ?: ""
                        val error = result["error"] as? String ?: ""
                        val timedOut = result["timed_out"] as? Boolean ?: false
                        val exitCode = result["exit_code"] as? Int ?: -1
                        android.util.Log.e("LinuxSandbox", "Failed to install $pkg: exit=$exitCode timedOut=$timedOut error=$error stdout=$stdout stderr=$stderr")
                        _state.value = SandboxState.Error("Failed to install $pkg: ${stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)}")
                        return@launch
                    }
                }
                // Seed ~/.ssh/config with ControlMaster + keepalive defaults so any
                // manual `ssh user@host` from now on multiplexes. Idempotent —
                // skips when the kai:defaults block is already present. Failures
                // here are non-fatal: openssh works without the defaults, just
                // without the held-connection optimization.
                runCatching { SshConfigManager(java.io.File(homePath)).ensureDefaults() }
                    .onFailure { android.util.Log.w("LinuxSandbox", "ssh defaults seed failed: ${it.message}") }
                _state.value = SandboxState.Ready
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun reset() {
        scope.launch {
            closeAllShells()
            sandboxDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!sandboxDir.isDirectory) return 0
        // Manual stack walk instead of walkTopDown(): the latter throws an
        // AssertionError if a child entry transitions from directory→non-directory
        // between the iterator's isDirectory check and DirectoryState construction.
        // The rootfs can contain unix sockets / FIFOs / broken symlinks (e.g. from
        // user-run programs like node), and concurrent install activity also races
        // the walk. We skip bad entries and keep going.
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(sandboxDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                        // skip sockets, FIFOs, broken symlinks
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry, keep iterating
                }
            }
        }
        return total / (1024 * 1024)
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        // Both checks must pass: existing installs that predate the SSH bundle
        // will report not-installed and re-prompt, picking up the new packages
        // on the next install run (apk skips already-installed ones).
        return File(rootfsPath, "usr/bin/python3").exists() &&
            File(rootfsPath, "usr/bin/ssh").exists()
    }
}
