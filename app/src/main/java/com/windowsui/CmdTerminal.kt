package com.windowsui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private data class TerminalLine(
    val text: String,
    val ansi: Boolean = true
)

@Composable
fun CmdTerminalContent(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val output = remember {
        mutableStateListOf<TerminalLine>()
    }

    val history = remember {
        mutableStateListOf<String>()
    }

    var historyIndex by remember {
        mutableStateOf(-1)
    }

    var input by remember {
        mutableStateOf("")
    }

    var currentDir by remember {
        mutableStateOf(
            File(
                context.filesDir,
                "home/Users/${SystemState.username}"
            )
        )
    }

    var currentDrive by remember {
        mutableStateOf("C:")
    }

    var isRootMode by remember {
        mutableStateOf(false)
    }

    var terminalTitle by remember {
        mutableStateOf("ASUI Terminal")
    }

    var terminalBackground by remember {
        mutableStateOf(Color(0xFF0C0C0C))
    }

    var terminalForeground by remember {
        mutableStateOf(Color(0xFFF2F2F2))
    }

    val envMap = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(
                try {
                    BinaryManager.getEnvironmentVariables(context)
                } catch (_: Throwable) {
                    emptyMap()
                }
            )
        }
    }

    val cRoot = remember {
        context.filesDir
    }

    val dRoot = remember {
        File("/storage/emulated/0")
    }

    var runningProcess by remember {
        mutableStateOf<Process?>(null)
    }

    var job by remember {
        mutableStateOf<Job?>(null)
    }

    val processRunning = remember {
        AtomicBoolean(false)
    }

    var executeCommandRef by remember {
        mutableStateOf<((String, Boolean) -> Unit)?>(null)
    }

    fun windowsPath(file: File, drive: String): String {
        val root = if (drive.equals("D:", true)) dRoot else cRoot
        val rootPath = root.absolutePath.replace('\\', '/')
        val filePath = file.absolutePath.replace('\\', '/')

        val relative = if (
            filePath.equals(rootPath, true)
        ) {
            ""
        } else if (
            filePath.startsWith("$rootPath/")
        ) {
            filePath.removePrefix("$rootPath/")
        } else {
            file.name
        }

        val clean = relative
            .replace("/", "\\")
            .trim('\\')

        return if (clean.isEmpty()) {
            "$drive\\"
        } else {
            "$drive\\$clean"
        }
    }

    fun normalizeDirectory(file: File, drive: String): File {
        val root = if (drive.equals("D:", true)) dRoot else cRoot

        val canonicalRoot = try {
            root.canonicalFile
        } catch (_: Throwable) {
            root.absoluteFile
        }

        val canonicalFile = try {
            file.canonicalFile
        } catch (_: Throwable) {
            file.absoluteFile
        }

        val rootPath = canonicalRoot.path.trimEnd(File.separatorChar)
        val filePath = canonicalFile.path
        val isInsideRoot = filePath.equals(rootPath, ignoreCase = true) ||
            filePath.startsWith(rootPath + File.separator, ignoreCase = true)
        if (!isInsideRoot) return canonicalRoot

        return if (canonicalFile.isDirectory) {
            canonicalFile
        } else {
            canonicalFile.parentFile ?: canonicalRoot
        }
    }

    fun resolveDrivePath(path: String): Pair<String, File> {
        val raw = path.trim()
        var drive = currentDrive

        if (
            raw.length >= 2 &&
            raw[1] == ':' &&
            raw[0].isLetter()
        ) {
            drive = raw.substring(0, 2).uppercase(Locale.getDefault())

            if (drive != "C:" && drive != "D:") {
                return currentDrive to currentDir
            }

            val remaining = raw
                .substring(2)
                .replace('\\', '/')
                .trim('/')

            val root = if (drive == "C:") cRoot else dRoot

            if (remaining.isEmpty()) {
                return drive to root
            }

            return drive to File(root, remaining)
        }

        val normalized = raw.replace('\\', '/')

        if (
            normalized.startsWith("/") ||
            normalized.startsWith("./") ||
            normalized.startsWith("../")
        ) {
            val base = if (drive == "C:") cRoot else dRoot

            return drive to File(
                if (normalized.startsWith("/")) base else currentDir,
                normalized.trimStart('/')
            )
        }

        return drive to File(currentDir, normalized)
    }

    fun variableExpand(text: String): String {
        var result = text
        val pattern = Regex("%([A-Za-z_][A-Za-z0-9_]*)%")

        result = pattern.replace(result) { match ->
            envMap[match.groupValues[1]]
                ?: System.getenv(match.groupValues[1])
                ?: ""
        }

        return result
    }

    fun appendLines(lines: List<String>) {
        lines.forEach {
            output.add(
                TerminalLine(
                    it.replace("\r", "").replace("\b", "")
                )
            )
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "ASUI Terminal",
                text
            )
        )
    }

    fun parseAnsiToAnnotated(text: String): AnnotatedString {
        val result = AnnotatedString.Builder()
        var currentColor = terminalForeground
        val regex = Regex("\u001B\\[([0-9;]*)m")
        var lastIndex = 0

        regex.findAll(text).forEach { match ->
            if (match.range.first > lastIndex) {
                result.withStyle(
                    SpanStyle(color = currentColor)
                ) {
                    append(
                        text.substring(
                            lastIndex,
                            match.range.first
                        )
                    )
                }
            }

            val codes = match.groupValues[1]
                .split(";")
                .mapNotNull { it.toIntOrNull() }

            codes.forEach { code ->
                currentColor = when (code) {
                    31 -> Color(0xFFFF5555)
                    32 -> Color(0xFF55FF55)
                    33 -> Color(0xFFFFFF55)
                    36 -> Color(0xFF55FFFF)
                    37 -> Color.White
                    0 -> terminalForeground
                    else -> currentColor
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            result.withStyle(
                SpanStyle(color = currentColor)
            ) {
                append(text.substring(lastIndex))
            }
        }

        return result.toAnnotatedString()
    }

    fun completeInput(): String {
        val beforeCursor = input
        val lastSpace = beforeCursor.lastIndexOf(' ')
        val prefix = beforeCursor.substring(0, lastSpace + 1)
        val partial = beforeCursor.substring(lastSpace + 1)

        val files = currentDir
            .listFiles()
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ?: emptyList()

        val match = files.firstOrNull {
            it.name.startsWith(
                partial,
                ignoreCase = true
            )
        } ?: return input

        return prefix + match.name +
                if (match.isDirectory) "\\" else ""
    }

    fun splitCommandLine(text: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        for (c in text.trim()) {
            if (c == '"' || c == '\'') {
                if (quote == null) quote = c else if (quote == c) quote = null else token.append(c)
            } else if (c.isWhitespace() && quote == null) {
                if (token.isNotEmpty()) { result += token.toString(); token.clear() }
            } else token.append(c)
        }
        if (token.isNotEmpty()) result += token.toString()
        return result
    }

    suspend fun executeExternal(
        command: String
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val cleanCommand = command.trim().removeSuffix("|").trim()

            // --termux ekinin kontrolü
            val useTermux = cleanCommand.contains("--termux", ignoreCase = true) ||
                    cleanCommand.startsWith("engine termux", ignoreCase = true)

            val finalCommand = cleanCommand
                .replace(Regex("\\s+--termux\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)^engine\\s+termux\\s*"), "")
                .trim()

            if (useTermux) {
                return@withContext TermuxBridge.runCommandWithOutput(
                    context,
                    finalCommand,
                    currentDir.absolutePath
                ).fold(
                    onSuccess = { listOf(it) },
                    onFailure = { listOf("Termux: ${it.message ?: "komut başlatılamadı"}") }
                )
            }

            val split = splitCommandLine(finalCommand)
            val firstWordEarly = split.firstOrNull()?.lowercase(Locale.getDefault()).orEmpty()
            if (firstWordEarly == "adb") {
                return@withContext OtgAdbBridge.execute(
                    context,
                    split.drop(1),
                    root = isRootMode
                )
            }

            val environment = mutableMapOf<String, String>().apply { putAll(envMap) }
            environment["PWD"] = currentDir.absolutePath

            val binDir = try { BinaryManager.getNativeBinDir(context) } catch (_: Throwable) { context.applicationInfo.nativeLibraryDir }

            val firstWord = finalCommand.split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.getDefault()) ?: ""

            // JNI ve Termux Komutlarını Tek Bir Shell Scriptinde Birleştir
            val scriptToRun = buildString {
                append("cd '${currentDir.absolutePath}' && ")
                append("export PATH='$binDir:\$PATH'; ")
                when (firstWord) {
                    "busybox" -> append("'$binDir/libbusybox.so' ${finalCommand.removePrefix(firstWord).trim()}")
                    else -> append(finalCommand)
                }
            }

            // Java'nın error=2 fırlatmasını önlemek için tam yollar
            val shell = if (isRootMode) "/system/bin/su" else "/system/bin/sh"
            val builder = ProcessBuilder(shell, "-c", scriptToRun)
            builder.environment().putAll(environment)
            builder.redirectErrorStream(true)

            val result = mutableListOf<String>()
            try {
                val process = builder.start()
                runningProcess = process
                processRunning.set(true)

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String? = null
                    while (processRunning.get() && reader.readLine().also { line = it } != null) {
                        result.add(line?.replace("\r", "")?.replace("\b", "") ?: "")
                    }
                }
                process.waitFor()
            } catch (e: Exception) {
                result.add("HATA: Komut çalıştırılamadı (${e.message})")
            } finally {
                runningProcess = null
                processRunning.set(false)
            }
            result
        }
    }

    fun killCurrentProcess() {
        processRunning.set(false)
        try { runningProcess?.destroy() } catch (_: Throwable) {}
        try { runningProcess?.destroyForcibly() } catch (_: Throwable) {}
        runningProcess = null
        job?.cancel()
        job = null
        output.add(TerminalLine("^C"))
    }

    fun executeBatchScript(file: File) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (!file.exists() || !file.isFile) return@withContext
                file.readLines().forEach { line ->
                    var command = line.trim()
                    if (command.isEmpty() || command.equals("rem", true) || command.startsWith("rem ", true)) {
                        return@forEach
                    }
                    if (command.equals("@echo off", true)) return@forEach
                    command = variableExpand(command)
                    withContext(Dispatchers.Main) {
                        executeCommandRef?.invoke(command, false)
                    }
                }
            }
        }
    }

    // ==================== YENİ KOMUTLAR ====================

    suspend fun tryExploit(cve: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val result = process.inputStream.bufferedReader().readText()
                process.waitFor()
                if (result.contains("uid=0")) "Başarılı" else "Başarısız"
            } catch (e: Exception) {
                "Hata: ${e.message}"
            }
        }
    }

    fun processCommand(
        rawCommand: String,
        addHistory: Boolean
    ) {
        val command = variableExpand(rawCommand.trim())
        if (command.isEmpty()) return

        if (addHistory) {
            if (history.lastOrNull()?.equals(command, true) != true) {
                history.add(command)
            }
            historyIndex = -1
        }

        // ==================== WINDOWS CMD KOMUTLARI ====================

        if (command.equals("CLS", true) || command.equals("CLEAR", true)) {
            output.clear()
            return
        }

        if (command.equals("EXIT", true)) {
            onClose()
            return
        }

        if (command.equals("VER", true)) {
            appendLines(
                listOf(
                    "ASUI Kernel [Version 0.1.0]",
                    "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"
                )
            )
            return
        }

        if (command.startsWith("TITLE ", true)) {
            terminalTitle = command.substringAfter(' ').trim()
            return
        }

        if (command.equals("TITLE", true)) {
            appendLines(listOf(terminalTitle))
            return
        }

        if (command.startsWith("COLOR ", true)) {
            val code = command.substringAfter(' ').trim().lowercase(Locale.getDefault())
            when (code) {
                "0a" -> {
                    terminalBackground = Color(0xFF000000)
                    terminalForeground = Color(0xFF55FF55)
                }
                "1f" -> {
                    terminalBackground = Color(0xFF000080)
                    terminalForeground = Color.White
                }
                "07" -> {
                    terminalBackground = Color(0xFF0C0C0C)
                    terminalForeground = Color(0xFFF2F2F2)
                }
                "0c" -> {
                    terminalBackground = Color(0xFF000000)
                    terminalForeground = Color(0xFFFF5555)
                }
                else -> {
                    appendLines(listOf("Geçersiz renk kodu: $code"))
                }
            }
            return
        }

        if (command.equals("SET", true)) {
            appendLines(
                envMap.toSortedMap().map { (key, value) -> "$key=$value" }
            )
            return
        }

        if (command.startsWith("SET ", true)) {
            val assignment = command.substringAfter(' ').trim()
            val equals = assignment.indexOf('=')
            if (equals > 0) {
                val key = assignment.substring(0, equals).trim()
                val value = assignment.substring(equals + 1)
                envMap[key] = value
                appendLines(listOf("$key=$value"))
            } else {
                appendLines(listOf("SET kullanımı: SET VAR=DEGER"))
            }
            return
        }

        if (command.startsWith("ECHO", true)) {
            val value = command.replace(Regex("(?i)^ECHO\\s*"), "")
            if (value.equals("OFF", true) || value.equals("ON", true)) return
            appendLines(listOf(variableExpand(value)))
            return
        }

        if (command.equals("C:", true) || command.equals("C:\\", true)) {
            currentDrive = "C:"
            currentDir = cRoot
            return
        }

        if (command.equals("D:", true) || command.equals("D:\\", true)) {
            currentDrive = "D:"
            currentDir = dRoot
            return
        }

        if (command.equals("CD", true) || command.equals("CD\\", true) || command.startsWith("CD ", true)) {
            var argument = command.replace(Regex("(?i)^CD\\s*"), "").trim()
            var switchDrive = false
            if (argument.startsWith("/D ", ignoreCase = true)) {
                switchDrive = true
                argument = argument.substring(3).trim()
            }
            if (argument.isEmpty()) {
                appendLines(listOf(windowsPath(currentDir, currentDrive)))
                return
            }

            var targetDrive = currentDrive
            var target = argument.removeSurrounding("\"")
            val hasDrivePrefix = target.length >= 2 && target[1] == ':' && target[0].isLetter()
            if (hasDrivePrefix) {
                targetDrive = target.substring(0, 2).uppercase(Locale.ROOT)
                target = target.substring(2)
            }
            if (targetDrive != "C:" && targetDrive != "D:") {
                appendLines(listOf("Sürücü bulunamadı: $targetDrive"))
                return
            }

            val targetRoot = if (targetDrive == "C:") cRoot else dRoot
            val cleanTarget = target.replace('\\', '/')
            val base = when {
                cleanTarget.startsWith("/") -> targetRoot
                hasDrivePrefix && targetDrive != currentDrive -> targetRoot
                else -> currentDir
            }
            val candidate = if (cleanTarget.isEmpty()) targetRoot else File(base, cleanTarget.trimStart('/'))
            val normalized = normalizeDirectory(candidate, targetDrive)

            if (normalized.exists() && normalized.isDirectory) {
                currentDrive = targetDrive
                currentDir = normalized
            } else {
                appendLines(listOf("Sistem belirtilen yolu bulamıyor: $argument"))
            }
            return
        }

        if (command.startsWith("DIR", true)) {
            val argument = command.replace(Regex("(?i)^DIR\\s*"), "").trim()
            val resolved = if (argument.isEmpty()) currentDrive to currentDir else resolveDrivePath(argument)
            val directoryDrive = resolved.first
            val directory = resolved.second

            if (!directory.exists() || !directory.isDirectory) {
                appendLines(listOf("Dosya veya klasör bulunamadı."))
                return
            }

            val files = directory.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()

            appendLines(
                listOf(
                    " Volume in drive $currentDrive",
                    "",
                    " Directory of ${windowsPath(directory, directoryDrive)}",
                    ""
                )
            )

            files.forEach { file ->
                val type = if (file.isDirectory) "<DIR>" else file.length().toString()
                appendLines(
                    listOf(
                        String.format(
                            Locale.getDefault(),
                            "%-12s %12s  %s",
                            type,
                            if (file.isDirectory) "" else file.length(),
                            file.name
                        )
                    )
                )
            }

            appendLines(listOf("", "${files.size} File(s)/Directory(s)"))
            return
        }

        if (command.equals("TREE", true)) {
            fun tree(directory: File, prefix: String, depth: Int, lines: MutableList<String>) {
                if (depth > 3) return
                val children = directory.listFiles()?.sortedBy { it.name.lowercase() } ?: return
                children.forEachIndexed { index, file ->
                    val last = index == children.lastIndex
                    lines.add(prefix + (if (last) "└── " else "├── ") + file.name)
                    if (file.isDirectory) {
                        tree(file, prefix + (if (last) "    " else "│   "), depth + 1, lines)
                    }
                }
            }

            val lines = mutableListOf<String>()
            lines.add(windowsPath(currentDir, currentDrive))
            tree(currentDir, "", 1, lines)
            appendLines(lines)
            return
        }

        if (command.startsWith("TYPE ", true)) {
            val name = command.substringAfter(' ').trim()
            val file = resolveDrivePath(name).second
            if (file.exists() && file.isFile) {
                try { appendLines(file.readLines()) } catch (e: Throwable) { appendLines(listOf("Erişim reddedildi: ${e.message}")) }
            } else {
                appendLines(listOf("Dosya bulunamadı: $name"))
            }
            return
        }

        if (command.startsWith("FIND ", true)) {
            val regex = Regex("""FIND\s+"([^"]+)"\s+(.+)""", RegexOption.IGNORE_CASE)
            val match = regex.find(command)
            if (match == null) {
                appendLines(listOf("""Kullanım: FIND "yazı" dosya"""))
                return
            }
            val text = match.groupValues[1]
            val fileName = match.groupValues[2].trim()
            val file = resolveDrivePath(fileName).second
            if (!file.exists()) {
                appendLines(listOf("Dosya bulunamadı."))
                return
            }
            try {
                appendLines(file.readLines().filter { it.contains(text, ignoreCase = true) })
            } catch (e: Throwable) {
                appendLines(listOf("Dosya okunamadı."))
            }
            return
        }

        if (command.matches(Regex("(?i)^(MD|MKDIR)\\s.+"))) {
            val path = command.replace(Regex("(?i)^(MD|MKDIR)\\s+"), "").trim()
            val dir = resolveDrivePath(path).second
            if (dir.mkdirs() || dir.isDirectory) appendLines(listOf("Dizin oluşturuldu: ${windowsPath(dir, currentDrive)}"))
            else appendLines(listOf("Dizin oluşturulamadı: $path"))
            return
        }

        if (command.matches(Regex("(?i)^(RD|RMDIR)\\s.+"))) {
            val path = command.replace(Regex("(?i)^(RD|RMDIR)\\s+"), "").trim().removeSurrounding("\"")
            val dir = resolveDrivePath(path).second
            if (!dir.exists() || !dir.isDirectory) appendLines(listOf("Dizin bulunamadı: $path"))
            else if (dir.deleteRecursively()) appendLines(listOf("Dizin silindi: $path"))
            else appendLines(listOf("Erişim reddedildi: $path"))
            return
        }

        if (command.matches(Regex("(?i)^(DEL|ERASE)\\s.+"))) {
            val path = command.replace(Regex("(?i)^(DEL|ERASE)\\s+"), "").trim().removeSurrounding("\"")
            val file = resolveDrivePath(path).second
            if (!file.exists()) appendLines(listOf("Dosya bulunamadı: $path"))
            else if (file.isDirectory) appendLines(listOf("DEL klasör silemez; RD kullan."))
            else if (file.delete()) appendLines(listOf("Silindi: $path"))
            else appendLines(listOf("Erişim reddedildi: $path"))
            return
        }

        if (command.matches(Regex("(?i)^REN\\s.+"))) {
            val parts = splitCommandLine(command).drop(1)
            if (parts.size != 2) { appendLines(listOf("Kullanım: REN eski yeni")); return }
            val source = resolveDrivePath(parts[0]).second
            val target = File(source.parentFile ?: currentDir, parts[1])
            if (source.exists() && source.renameTo(target)) appendLines(listOf("Yeniden adlandırıldı: ${parts[1]}"))
            else appendLines(listOf("Yeniden adlandırma başarısız."))
            return
        }

        if (command.matches(Regex("(?i)^COPY\\s.+"))) {
            val parts = splitCommandLine(command).drop(1)
            if (parts.size != 2) { appendLines(listOf("Kullanım: COPY kaynak hedef")); return }
            val source = resolveDrivePath(parts[0]).second
            val target = resolveDrivePath(parts[1]).second
            try {
                if (!source.isFile) throw IllegalArgumentException("kaynak dosya değil")
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                appendLines(listOf("        1 file(s) copied."))
            } catch (t: Throwable) { appendLines(listOf("COPY başarısız: ${t.message}")) }
            return
        }

        if (command.matches(Regex("(?i)^MOVE\\s.+"))) {
            val parts = splitCommandLine(command).drop(1)
            if (parts.size != 2) { appendLines(listOf("Kullanım: MOVE kaynak hedef")); return }
            val source = resolveDrivePath(parts[0]).second
            val target = resolveDrivePath(parts[1]).second
            target.parentFile?.mkdirs()
            if (source.renameTo(target)) appendLines(listOf("        1 file(s) moved."))
            else appendLines(listOf("MOVE başarısız."))
            return
        }

        // ==================== YENİ GELİŞMİŞ KOMUTLAR ====================

        if (command.equals("EXPLOIT", true)) {
            appendLines(listOf("Root exploit denemesi başlatılıyor..."))
            scope.launch(Dispatchers.IO) {
                val results = mutableListOf<String>()
                results.add("CVE-2019-2215: ${tryExploit("CVE-2019-2215")}")
                results.add("CVE-2020-0041: ${tryExploit("CVE-2020-0041")}")
                results.add("CVE-2021-0922: ${tryExploit("CVE-2021-0922")}")
                withContext(Dispatchers.Main) { appendLines(results) }
            }
            return
        }

        if (command.startsWith("FASTBOOT ", true)) {
            val fbCmd = command.substringAfter("FASTBOOT ").trim()
            scope.launch(Dispatchers.IO) {
                val result = executeExternal("fastboot $fbCmd")
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("OTG ADB DEVICES", true)) {
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("devices"), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("OTG ADB ROOT", true)) {
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("root"), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.startsWith("OTG ADB SHELL ", true)) {
            val shellCmd = command.substringAfter("OTG ADB SHELL ").trim()
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("shell", shellCmd), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.startsWith("OTG ADB PUSH ", true)) {
            val args = command.substringAfter("OTG ADB PUSH ").trim().split(" ")
            if (args.size < 2) {
                appendLines(listOf("Kullanım: OTG ADB PUSH <kaynak> <hedef>"))
                return
            }
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("push", args[0], args[1]), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.startsWith("OTG ADB PULL ", true)) {
            val args = command.substringAfter("OTG ADB PULL ").trim().split(" ")
            if (args.size < 2) {
                appendLines(listOf("Kullanım: OTG ADB PULL <hedef> <kaynak>"))
                return
            }
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("pull", args[0], args[1]), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("OTG ADB BACKUP", true)) {
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("backup", "-apk", "-shared", "-all", "-system", "-f", "/sdcard/backup.ab"), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("OTG ADB RESTORE", true)) {
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("restore", "/sdcard/backup.ab"), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("OTG ADB SCREENRECORD", true)) {
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, listOf("shell", "screenrecord", "/sdcard/record.mp4"), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.startsWith("VIRTUAL DESKTOP", true)) {
            val action = command.substringAfter("VIRTUAL DESKTOP ").trim()
            when {
                action.equals("NEW", true) -> {
                    val id = System.currentTimeMillis().toString()
                    SystemState.virtualDesktops.add(id)
                    appendLines(listOf("Yeni sanal masaüstü oluşturuldu: $id"))
                }
                action.startsWith("SWITCH ", true) -> {
                    val id = action.substringAfter("SWITCH ").trim()
                    if (SystemState.virtualDesktops.contains(id)) {
                        SystemState.currentDesktop = id
                        appendLines(listOf("Masaüstüne geçildi: $id"))
                    } else {
                        appendLines(listOf("Masaüstü bulunamadı: $id"))
                    }
                }
                else -> appendLines(listOf("Kullanım: VIRTUAL DESKTOP NEW | SWITCH <id>"))
            }
            return
        }

        if (command.startsWith("WIDGET ADD", true)) {
            val widgetName = command.substringAfter("WIDGET ADD ").trim()
            DesktopState.addWidget(widgetName, context = context)
            appendLines(listOf("Widget eklendi: $widgetName"))
            return
        }

        if (command.startsWith("THEME SET", true)) {
            val theme = command.substringAfter("THEME SET ").trim()
            when (theme) {
                "dark" -> { SystemState.theme = "dark"; appendLines(listOf("Koyu tema aktif")) }
                "light" -> { SystemState.theme = "light"; appendLines(listOf("Açık tema aktif")) }
                else -> appendLines(listOf("Geçersiz tema: $theme"))
            }
            return
        }

        if (command.equals("SYSINFO", true)) {
            val info = DeviceInfo.getFullInfo(context)
            appendLines(info)
            return
        }

        if (command.equals("SCREENSHOT", true)) {
            scope.launch(Dispatchers.IO) {
                val result = executeExternal("screencap -p /sdcard/screenshot.png")
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("ROOTGUIDE", true)) {
            appendLines(
                listOf(
                    "",
                    "=== ASUI Desktop ile OTG Üzerinden Root Atma Rehberi ===",
                    "",
                    "1. Hedef cihazın bootloader'ını açık olmalı (OEM kilidi açık).",
                    "2. OTG ile hedef cihazı ASUI cihazına bağla.",
                    "3. 'OTG ADB DEVICES' komutuyla cihazı gör.",
                    "4. 'OTG ADB ROOT' komutuyla root modunu etkinleştir (eğer izin veriyorsa).",
                    "5. 'OTG ADB PUSH magisk_patched_boot.img /sdcard/' ile boot.img gönder.",
                    "6. 'OTG ADB SHELL su -c \"dd if=/sdcard/magisk_patched_boot.img of=/dev/block/bootdevice/by-name/boot\"'",
                    "7. 'OTG ADB SHELL reboot' ile cihazı yeniden başlat.",
                    "",
                    "NOT: Her cihazın boot bölümü farklıdır. Doğru bölümü bulmak için:",
                    "'OTG ADB SHELL ls -l /dev/block/bootdevice/by-name/'",
                    "",
                    "Bu işlemler root gerektirir. ASUI Desktop size sadece araçları sunar.",
                    "Sorumluluk size aittir."
                )
            )
            return
        }

        // ==================== ANDROID SHELL KOMUTLARI (root ile) ====================

        if (command.equals("WHOAMI", true)) {
            scope.launch(Dispatchers.IO) {
                val lines = executeExternal("id")
                withContext(Dispatchers.Main) { appendLines(lines) }
            }
            return
        }

        if (command.equals("IPCONFIG", true) || command.equals("IPCONFIG /ALL", true)) {
            scope.launch(Dispatchers.IO) {
                val lines = executeExternal("ip addr")
                withContext(Dispatchers.Main) { appendLines(lines) }
            }
            return
        }

        if (command.equals("GETPROP", true)) {
            scope.launch(Dispatchers.IO) {
                val lines = executeExternal("getprop")
                withContext(Dispatchers.Main) { appendLines(lines) }
            }
            return
        }

        if (command.equals("OTG", true) || command.equals("OTG DEVICES", true)) {
            val devices = UsbOtgManager.getConnectedDevices(context)
            if (devices.isEmpty()) appendLines(listOf("OTG: USB cihazı bulunamadı."))
            else {
                appendLines(listOf("OTG USB cihazları:"))
                devices.forEachIndexed { index, device ->
                    appendLines(listOf("[$index] ${device.deviceName}  VID=${device.vendorId.toString(16)} PID=${device.productId.toString(16)} ADB=${UsbOtgManager.isAdbDevice(device)} permission=${(context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager).hasPermission(device)}"))
                }
            }
            return
        }

        if (command.matches(Regex("(?i)^OTG\\s+PERMISSION\\s+\\d+"))) {
            val index = splitCommandLine(command).getOrNull(2)?.toIntOrNull()
            val device = index?.let { UsbOtgManager.getConnectedDevices(context).getOrNull(it) }
            if (device == null) appendLines(listOf("OTG cihaz numarası geçersiz."))
            else { UsbOtgManager.requestUsbPermission(context, device); appendLines(listOf("Android USB erişim onayı istendi: [$index]")) }
            return
        }

        if (command.startsWith("OTG ADB ", true)) {
            val adbCmd = command.substringAfter("OTG ADB ").trim()
            scope.launch(Dispatchers.IO) {
                val result = OtgAdbBridge.execute(context, splitCommandLine(adbCmd), isRootMode)
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.matches(Regex("(?i)^TERMUX\\s.+"))) {
            val termuxCommand = command.replace(Regex("(?i)^TERMUX\\s+"), "").trim()
            val result = TermuxBridge.runCommand(context, termuxCommand, currentDir.absolutePath)
            appendLines(listOf(result.fold({ it }, { "Termux: ${it.message}" })))
            return
        }

        if (command.equals("TASKLIST", true)) {
            scope.launch(Dispatchers.IO) {
                val lines = executeExternal("ps -A")
                withContext(Dispatchers.Main) { appendLines(lines) }
            }
            return
        }

        if (command.startsWith("TASKKILL", true)) {
            val pidMatch = Regex("""(?i)/PID\s+(\d+)""").find(command)
            if (pidMatch == null) {
                appendLines(listOf("Kullanım: TASKKILL /PID <no>"))
                return
            }
            val pid = pidMatch.groupValues[1]
            scope.launch(Dispatchers.IO) {
                val result = executeExternal("kill -9 $pid")
                withContext(Dispatchers.Main) { appendLines(result) }
            }
            return
        }

        if (command.equals("SU", true)) {
            scope.launch(Dispatchers.IO) {
                val result = try {
                    val process =
                        ProcessBuilder(
                            "/system/bin/su",
                            "-c",
                            "id"
                        )
                            .redirectErrorStream(true)
                            .start()
                    val text = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    text
                } catch (e: Throwable) { "" }

                withContext(Dispatchers.Main) {
                    if (result.contains("uid=0")) {
                        isRootMode = true
                        appendLines(listOf("Root erişimi başarılı.", "ASUI Terminal artık root modunda."))
                    } else {
                        appendLines(listOf("Root erişimi alınamadı."))
                    }
                }
            }
            return
        }

        if (command.equals("HELP", true)) {
            appendLines(
                listOf(
                    "",
                    "ASUI Terminal komutları:",
                    "",
                    "=== Windows CMD ===",
                    "CLS / CLEAR       Ekranı temizle",
                    "VER               Sürüm",
                    "TITLE <metin>     Başlığı değiştir",
                    "COLOR <kod>       Konsol rengi",
                    "SET               Ortam değişkenleri",
                    "SET A=B           Değişken tanımla",
                    "ECHO <metin>      Metin yazdır",
                    "CD <klasör>       Klasör değiştir",
                    "C: / D:           Sürücü değiştir",
                    "DIR               Klasör içeriği",
                    "TREE              Klasör ağacı",
                    "TYPE <dosya>      Dosya göster",
                    """FIND "metin" dosya Dosyada ara""",
                    "TASKLIST          Süreçleri göster",
                    "TASKKILL /PID N   Süreç sonlandır",
                    "MD/MKDIR <dizin>  Dizin oluştur",
                    "RD/RMDIR <dizin>  Dizin sil",
                    "DEL/ERASE <dosya> Dosya sil",
                    "COPY kaynak hedef Dosya kopyala",
                    "MOVE kaynak hedef Dosya taşı",
                    "REN eski yeni     Yeniden adlandır",
                    "",
                    "=== Gelişmiş / Root ===",
                    "EXPLOIT           Root exploit dene",
                    "SU                Root modu (mevcutsa)",
                    "WHOAMI            Kullanıcı/UID",
                    "IPCONFIG          Ağ bilgisi",
                    "GETPROP           Android özellikleri",
                    "SYSINFO           Sistem bilgileri",
                    "SCREENSHOT        Ekran görüntüsü al",
                    "FASTBOOT ...      Fastboot komutları",
                    "",
                    "=== OTG / ADB ===",
                    "OTG DEVICES       USB cihazlarını listele",
                    "OTG PERMISSION N  USB erişim iste",
                    "OTG ADB DEVICES   ADB cihazlarını listele",
                    "OTG ADB ROOT      ADB root modu",
                    "OTG ADB SHELL     ADB shell",
                    "OTG ADB PUSH      Dosya gönder",
                    "OTG ADB PULL      Dosya al",
                    "OTG ADB BACKUP    Yedek al",
                    "OTG ADB RESTORE   Yedeği geri yükle",
                    "OTG ADB SCREENRECORD Ekran kaydı",
                    "",
                    "=== Sanal Masaüstü / Widget ===",
                    "VIRTUAL DESKTOP NEW        Yeni masaüstü",
                    "VIRTUAL DESKTOP SWITCH <id> Masaüstü değiştir",
                    "WIDGET ADD <ad>            Widget ekle",
                    "THEME SET dark/light       Tema değiştir",
                    "",
                    "=== Diğer ===",
                    "ROOTGUIDE         Root atma rehberi",
                    "TERMUX ...        Termux RUN_COMMAND köprüsü",
                    "EXIT              Terminali kapat",
                    "",
                    "Android shell komutları da çalıştırılabilir:",
                    "ping, netstat, mount, chmod, dd, tar, zip, unzip, nano, vi, busybox, ls, ps, top, df, du, cat, grep, sed, awk ...",
                    "Örnek: adb devices",
                    "Örnek: OTG ADB devices",
                    "Örnek: TERMUX pkg list-installed",
                    "Örnek: ls -la",
                    ""
                )
            )
            return
        }

        if (command.endsWith(".bat", true) || command.endsWith(".cmd", true)) {
            val file = resolveDrivePath(command).second
            if (file.exists()) executeBatchScript(file) else appendLines(listOf("Batch dosyası bulunamadı."))
            return
        }

        if (command.endsWith(".exe", true)) {
            val file = resolveDrivePath(command).second
            if (!file.isFile) {
                appendLines(listOf("EXE dosyası bulunamadı: ${file.absolutePath}"))
            } else {
                appendLines(listOf(
                    "EXE bulundu: ${file.name}",
                    "Bu sürümde yerleşik Wine/Box64 çalıştırma motoru bulunmuyor.",
                    "Dosyayı Windows Uygulamaları penceresinden içe aktarabilirsin; içe aktarma tek başına çalıştırma anlamına gelmez."
                ))
                WindowState.openWindow(
                    id = "windows_apps_${System.currentTimeMillis()}",
                    title = "Windows Uygulamaları",
                    contentId = "windows_apps",
                    width = 620,
                    height = 440
                )
            }
            return
        }

        val pipeParts = command.split(Regex("\\s*\\|\\s*"))
        if (pipeParts.size >= 2 && pipeParts.last().equals("CLIP", true)) {
            val source = pipeParts.dropLast(1).joinToString(" | ")
            scope.launch {
                val result = executeExternal(source)
                copyToClipboard(result.joinToString("\n"))
                withContext(Dispatchers.Main) { appendLines(listOf("Çıktı panoya kopyalandı.")) }
            }
            return
        }

        if (processRunning.get()) {
            scope.launch(Dispatchers.IO) {
                try {
                    runningProcess?.outputStream?.bufferedWriter()?.apply {
                        write(command)
                        newLine()
                        flush()
                    }
                } catch (_: Throwable) {}
            }
            return
        }

        // ==================== GENEL ANDROID SHELL KOMUTLARI ====================
        scope.launch {
            val result = executeExternal(command)
            withContext(Dispatchers.Main) { appendLines(result) }
        }
    }

    executeCommandRef = { command, addHistory -> processCommand(command, addHistory) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        appendLines(
            listOf(
                "ASUI Desktop [Version 0.1.0]",
                "(c) ASUI. Tüm hakları saklıdır.",
                "",
                "ASUI Terminal hazır.",
                "HELP yazarak komutları görebilirsin.",
                ""
            )
        )
    }

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) listState.animateScrollToItem(output.lastIndex)
    }

    val prompt = if (isRootMode) {
        "root@asui:${windowsPath(currentDir, currentDrive)}# "
    } else {
        "${windowsPath(currentDir, currentDrive)}> "
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(terminalBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(Color(0xFF202020))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(">_", color = Color(0xFF55FFFF), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(terminalTitle, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                text = "Pano Kopyala",
                color = Color(0xFF75BFFF),
                fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable {
                    copyToClipboard(output.joinToString("\n") { it.text })
                }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("×", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { onClose() }.padding(5.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TerminalButton(text = "[Ctrl+C]", color = Color(0xFFFF5555), onClick = { killCurrentProcess() })
            TerminalButton(text = "[Tab ⇥]", onClick = { input = completeInput(); focusRequester.requestFocus() })
            TerminalButton(text = "[↑]", onClick = {
                if (history.isNotEmpty()) {
                    if (historyIndex == -1) historyIndex = history.lastIndex else if (historyIndex > 0) historyIndex--
                    input = history[historyIndex]
                }
            })
            TerminalButton(text = "[↓]", onClick = {
                if (history.isNotEmpty()) {
                    if (historyIndex >= 0 && historyIndex < history.lastIndex) {
                        historyIndex++
                        input = history[historyIndex]
                    } else {
                        historyIndex = -1
                        input = ""
                    }
                }
            })
            TerminalButton(text = "[C:]", onClick = { currentDrive = "C:"; currentDir = cRoot })
            TerminalButton(text = "[D:]", onClick = { currentDrive = "D:"; currentDir = dRoot })
            TerminalButton(text = "[DIR]", onClick = { processCommand("DIR", true) })
            TerminalButton(text = "[CLS]", onClick = { processCommand("CLS", true) })
            TerminalButton(text = "[SU]", onClick = { processCommand("SU", true) })
            TerminalButton(text = "[HELP]", onClick = { processCommand("HELP", true) })
            TerminalButton(text = "[SYSINFO]", onClick = { processCommand("SYSINFO", true) })
            TerminalButton(text = "[EXPLOIT]", onClick = { processCommand("EXPLOIT", true) })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
        ) {
            itemsIndexed(output) { _, line ->
                Text(
                    text = parseAnsiToAnnotated(line.text),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = prompt, color = terminalForeground, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = terminalForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val command = input
                        if (command.isNotBlank()) {
                            appendLines(listOf(prompt + command))
                            input = ""
                            executeCommandRef?.invoke(command, true)
                        }
                        keyboard?.hide()
                        focusRequester.requestFocus()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .padding(start = 3.dp)
            )
        }
    }
}

@Composable
private fun TerminalButton(
    text: String,
    color: Color = Color(0xFFD0D0D0),
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}
