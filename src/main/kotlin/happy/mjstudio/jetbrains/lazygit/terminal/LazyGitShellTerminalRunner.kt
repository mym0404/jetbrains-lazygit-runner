@file:Suppress("ktlint:standard:no-wildcard-imports")

package happy.mjstudio.jetbrains.lazygit.terminal

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.wsl.WslPath
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.intellij.util.EnvironmentRestorer
import com.intellij.util.SystemProperties
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.unix.UnixPtyProcess
import org.jetbrains.plugins.terminal.AbstractTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.util.TerminalEnvironment
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.*
import java.util.stream.*

enum class ShellType(
    val shellPath: String,
    val displayName: String,
) {
    SH("/bin/sh", "sh"),
    BASH("/bin/bash", "bash"),
    ZSH("/bin/zsh", "zsh"),
    FISH("/usr/local/bin/fish", "fish"),
    ;

    companion object {
        fun getDefault(): ShellType =
            when {
                SystemInfo.isMac -> ZSH
                else -> BASH
            }

        fun fromShellPath(shellPath: String): ShellType? = entries.find { it.shellPath == shellPath }
    }
}

@Suppress("UnstableApiUsage")
class LazyGitShellTerminalRunner(
    project: Project,
    private val shellType: ShellType = ShellType.getDefault(),
) : AbstractTerminalRunner<PtyProcess>(project) {
    private val defaultCharset = StandardCharsets.UTF_8

    companion object {
        private val LOG = Logger.getInstance(LazyGitShellTerminalRunner::class.java)
        private const val BASH_NAME = "bash"
        private const val SH_NAME = "sh"
        private const val ZSH_NAME = "zsh"
        private const val FISH_NAME = "fish"
    }

    override fun createProcess(options: ShellStartupOptions): PtyProcess {
        // Use configured shell with optimized startup options
        val shellCommand = options.shellCommand ?: buildShellCommand()
        val envs = options.envVariables
        val initialTermSize = options.initialTermSize
        val workingDir =
            options.workingDirectory
                ?: throw IllegalStateException("Working directory must not be null")

        try {
            val startNano = System.nanoTime()
            val builder =
                PtyProcessBuilder(shellCommand.toTypedArray())
                    .setEnvironment(envs)
                    .setDirectory(workingDir)
                    .setUseWinConPty(LocalPtyOptions.shouldUseWinConPty())

            initialTermSize?.let {
                builder.setInitialColumns(it.columns)
                builder.setInitialRows(it.rows)
            }

            val process = builder.start()
            LOG.info("Started fast ${shellType.displayName} terminal in ${TimeoutUtil.getDurationMillis(startNano)} ms")
            return process
        } catch (e: Exception) {
            throw ExecutionException("Failed to start fast ${shellType.displayName} terminal", e)
        }
    }

    private fun buildShellCommand(): List<String> {
        val command = mutableListOf(shellType.shellPath)

        when (shellType) {
            ShellType.BASH -> {
                // Don't add login options to avoid slow startup
                // Add interactive option for better user experience
                if (isInteractiveOptionAvailable(shellType.displayName)) {
                    command.add("-i")
                }
            }
            ShellType.ZSH -> {
                // Use -f flag to skip loading .zshrc to avoid slow startup
                command.add("--no-rcs")
                if (isInteractiveOptionAvailable(shellType.displayName)) {
                    command.add("-i")
                }
            }
            ShellType.FISH -> {
                // Fish is generally fast, but we can add --no-config for even faster startup
                command.add("--no-config")
            }
            ShellType.SH -> {
                // sh is the fastest, minimal options
                if (isInteractiveOptionAvailable(shellType.displayName)) {
                    command.add("-i")
                }
            }
        }

        return command
    }

    private fun isInteractiveOptionAvailable(shellName: String): Boolean = isBashZshFish(shellName)

    private fun isBashZshFish(shellName: String): Boolean =
        shellName == BASH_NAME ||
            (SystemInfo.isMac && shellName == SH_NAME) ||
            shellName == ZSH_NAME ||
            shellName == FISH_NAME

    override fun createTtyConnector(process: PtyProcess): TtyConnector =
        object : PtyProcessTtyConnector(process, defaultCharset) {
            override fun close() {
                if (process is UnixPtyProcess) {
                    process.hangup()
                    AppExecutorUtil.getAppScheduledExecutorService().schedule({
                        if (process.isAlive) {
                            LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination")
                            process.destroy()
                        }
                    }, 1000L, TimeUnit.MILLISECONDS)
                } else {
                    process.destroy()
                }
            }

            override fun resize(termSize: TermSize) {
                if (LOG.isDebugEnabled) {
                    LOG.debug("resize to $termSize")
                }
                super.resize(termSize)
            }
        }

    override fun getDefaultTabTitle(): String = "FastShell"

    override fun configureStartupOptions(baseOptions: ShellStartupOptions): ShellStartupOptions {
        val workingDir = getWorkingDirectory(baseOptions.workingDirectory)
        val envs = getTerminalEnvironment(workingDir)
        // Use the configured shell with optimized startup options
        val shellCommand = buildShellCommand()

        return baseOptions
            .builder()
            .shellCommand(shellCommand)
            .workingDirectory(workingDir)
            .envVariables(envs)
            .build()
    }

    private fun getWorkingDirectory(directory: String?): String {
        if (directory != null && isDirectory(directory)) {
            return directory
        }

        val terminalOptions = TerminalProjectOptionsProvider.getInstance(myProject)

        val configuredWorkingDirectory = terminalOptions.startingDirectory
        if (configuredWorkingDirectory != null && isDirectory(configuredWorkingDirectory)) {
            return configuredWorkingDirectory
        }

        val defaultWorkingDirectory = terminalOptions.defaultStartingDirectory
        if (defaultWorkingDirectory != null && isDirectory(defaultWorkingDirectory)) {
            return defaultWorkingDirectory
        }

        val projectDir = myProject.baseDir
        return projectDir?.let { VfsUtilCore.virtualToIoFile(it).absolutePath }
            ?: SystemProperties.getUserHome()
    }

    private fun isDirectory(directory: String): Boolean =
        try {
            val path =
                java.nio.file.Path
                    .of(directory)
            val ok =
                java.nio.file.Files
                    .isDirectory(path)
            if (!ok) {
                LOG.info("Cannot start terminal in $directory: no such directory")
            }
            ok
        } catch (e: Exception) {
            LOG.info("Cannot start terminal in $directory: invalid path", e)
            false
        }

    private fun getTerminalEnvironment(workingDir: String): MutableMap<String, String> {
        val envs =
            (if (SystemInfo.isWindows) CollectionFactory.createCaseInsensitiveStringMap<Any?>() else HashMap<Any?, Any?>())
                as MutableMap<String, String>
        val envData = TerminalProjectOptionsProvider.getInstance(this.myProject).getEnvData()
        if (envData.isPassParentEnvs) {
            envs.putAll(System.getenv())
            EnvironmentRestorer.restoreOverriddenVars(envs)
        }

        // Add lazygit paths to PATH environment variable
        addLazygitPathsToEnvironment(envs)

        // Set config directory environment variables for lazygit
        setLazygitConfigEnvironment(envs)

        if (!SystemInfo.isWindows) {
            envs["TERM"] = "xterm-256color"
        }

        envs["TERMINAL_EMULATOR"] = "JetBrains-JediTerm"
        envs["TERM_SESSION_ID"] = UUID.randomUUID().toString()
        envs["FIG_JETBRAINS_SHELL_INTEGRATION"] = "1"
        if (Registry.`is`("terminal.new.ui", false)) {
            envs["INTELLIJ_TERMINAL_COMMAND_BLOCKS"] = "1"
        }

        TerminalEnvironment.setCharacterEncoding(envs)
        if (this.myProject.isTrusted()) {
            val macroManager = PathMacroManager.getInstance(this.myProject)

            for (env in envData.envs.entries) {
                envs[env.key] = macroManager.expandPath(env.value)
            }

            if (WslPath.isWslUncPath(workingDir)) {
                setupWslEnv(envData.envs, envs as MutableMap<String?, String?>)
            }
        }

        return envs
    }

    private fun addLazygitPathsToEnvironment(envs: MutableMap<String, String>) {
        val currentPath = envs["PATH"] ?: System.getenv("PATH") ?: ""
        val lazygitPaths = getLazygitInstallationPaths()

        val existingPaths = currentPath.split(if (SystemInfo.isWindows) ";" else ":")
        val pathsToAdd =
            lazygitPaths.filter { path ->
                !existingPaths.contains(path) &&
                    java.nio.file.Files
                        .exists(
                            java.nio.file.Path
                                .of(path),
                        )
            }

        if (pathsToAdd.isNotEmpty()) {
            val pathSeparator = if (SystemInfo.isWindows) ";" else ":"
            val newPath = pathsToAdd.joinToString(pathSeparator) + pathSeparator + currentPath
            envs["PATH"] = newPath
        }
    }

    private fun setLazygitConfigEnvironment(envs: MutableMap<String, String>) {
        when {
            SystemInfo.isWindows -> {
                // Set APPDATA if not already set
                if (envs["APPDATA"] == null && System.getenv("APPDATA") == null) {
                    val userProfile = System.getProperty("user.home")
                    envs["APPDATA"] = "$userProfile\\AppData\\Roaming"
                }

                // Set LOCALAPPDATA if not already set
                if (envs["LOCALAPPDATA"] == null && System.getenv("LOCALAPPDATA") == null) {
                    val userProfile = System.getProperty("user.home")
                    envs["LOCALAPPDATA"] = "$userProfile\\AppData\\Local"
                }
            }
            SystemInfo.isMac || SystemInfo.isLinux -> {
                // Set XDG_CONFIG_HOME if not already set (follows XDG Base Directory Specification)
                if (envs["XDG_CONFIG_HOME"] == null && System.getenv("XDG_CONFIG_HOME") == null) {
                    val userHome = System.getProperty("user.home")
                    envs["XDG_CONFIG_HOME"] = "$userHome/.config"
                }

                // Set XDG_DATA_HOME if not already set
                if (envs["XDG_DATA_HOME"] == null && System.getenv("XDG_DATA_HOME") == null) {
                    val userHome = System.getProperty("user.home")
                    envs["XDG_DATA_HOME"] = "$userHome/.local/share"
                }

                // Set XDG_CACHE_HOME if not already set
                if (envs["XDG_CACHE_HOME"] == null && System.getenv("XDG_CACHE_HOME") == null) {
                    val userHome = System.getProperty("user.home")
                    envs["XDG_CACHE_HOME"] = "$userHome/.cache"
                }
            }
        }
    }

    private fun getLazygitInstallationPaths(): List<String> =
        when {
            SystemInfo.isWindows -> {
                listOf(
                    // Scoop installation paths
                    System.getProperty("user.home") + "\\scoop\\apps\\lazygit\\current",
                    System.getProperty("user.home") + "\\scoop\\shims",
                    // Chocolatey installation paths
                    "C:\\ProgramData\\chocolatey\\bin",
                    "C:\\tools\\lazygit",
                    // Manual installation paths
                    "C:\\Program Files\\lazygit",
                    "C:\\Program Files (x86)\\lazygit",
                    // User local paths
                    System.getProperty("user.home") + "\\AppData\\Local\\Programs\\lazygit",
                    System.getProperty("user.home") + "\\bin",
                )
            }
            SystemInfo.isMac -> {
                listOf(
                    // Homebrew paths
                    "/opt/homebrew/bin",
                    "/usr/local/bin",
                    // MacPorts paths
                    "/opt/local/bin",
                    // User local paths
                    System.getProperty("user.home") + "/bin",
                    System.getProperty("user.home") + "/.local/bin",
                    // Manual installation paths
                    "/usr/local/lazygit",
                    "/Applications/lazygit",
                )
            }
            SystemInfo.isLinux -> {
                listOf(
                    // Standard system paths
                    "/usr/bin",
                    "/usr/local/bin",
                    "/bin",
                    // User local paths
                    System.getProperty("user.home") + "/bin",
                    System.getProperty("user.home") + "/.local/bin",
                    // Snap paths
                    "/snap/bin",
                    // Flatpak paths
                    "/var/lib/flatpak/exports/bin",
                    System.getProperty("user.home") + "/.local/share/flatpak/exports/bin",
                    // AppImage paths
                    System.getProperty("user.home") + "/Applications",
                    System.getProperty("user.home") + "/appimages",
                    // Distribution-specific paths
                    "/opt/lazygit/bin",
                    // Go installation path (if installed via go install)
                    System.getProperty("user.home") + "/go/bin",
                )
            }
            else -> emptyList()
        }

    private fun setupWslEnv(
        userEnvs: MutableMap<String?, String?>,
        resultEnvs: MutableMap<String?, String?>,
    ) {
        val wslEnv =
            userEnvs.keys
                .stream()
                .map { name -> "$name/u" }
                .collect(Collectors.joining(":"))
        if (wslEnv.isNotEmpty()) {
            var prevValue = userEnvs["WSLENV"]
            if (prevValue == null) {
                prevValue = System.getenv("WSLENV")
            }

            val newWslEnv = if (prevValue != null) StringUtil.trimEnd(prevValue, ':') + ":" + wslEnv else wslEnv
            resultEnvs["WSLENV"] = newWslEnv
        }
    }
}
