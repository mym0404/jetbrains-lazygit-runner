@file:Suppress("ktlint:standard:no-wildcard-imports")

package happy.mjstudio.jetbrains.lazygit.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import happy.mjstudio.jetbrains.lazygit.terminal.LazyGitShellTerminalRunner
import org.jetbrains.plugins.terminal.AbstractTerminalRunner
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.util.*

class LazygitAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val workingDirectory = project.basePath ?: return

        ToolWindowManager.getInstance(project)

        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val terminalToolWindow = terminalManager.toolWindow

        // Check if terminal tool window was initially visible
        val wasTerminalVisible = terminalToolWindow?.isVisible == true
        println("wasTerminalVisible $wasTerminalVisible")

        val terminalWidget = createFastTerminal(project, terminalManager, workingDirectory, "Lazygit")

        // Execute lazygit command
        terminalWidget.executeCommand("lazygit")
        terminalManager.toolWindow.show {
            // Wait a moment for the terminal to be ready, then move to editor
            terminalWidget.requestFocus()
            moveTerminalToEditor(terminalWidget.component) // If terminal wasn't visible initially, close the tool window after moving
            if (!wasTerminalVisible) {
                terminalToolWindow?.hide()
            }
        }
    }

    private fun moveTerminalToEditor(terminalComponent: java.awt.Component) {
        val actionManager = ActionManager.getInstance()
        val moveToEditorAction = actionManager.getAction("Terminal.MoveToEditor")

        if (moveToEditorAction != null) {
            actionManager.tryToExecute(
                moveToEditorAction,
                null,
                terminalComponent,
                null,
                true,
            )
        }
    }

    private fun createFastTerminal(
        project: Project,
        terminalManager: TerminalToolWindowManager,
        workingDirectory: String,
        tabName: String,
    ): ShellTerminalWidget {
        val tabState = TerminalTabState()
        tabState.myTabName = tabName
        tabState.myWorkingDirectory = workingDirectory

        val widget = openWithRunnerAndGetWidget(terminalManager, LazyGitShellTerminalRunner(project), tabState)
        return Objects.requireNonNull(JBTerminalWidget.asJediTermWidget(widget)) as ShellTerminalWidget
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun openWithRunnerAndGetWidget(
        terminalManager: TerminalToolWindowManager,
        runner: AbstractTerminalRunner<*>,
        tabState: TerminalTabState?,
    ): TerminalWidget {
        terminalManager.createNewSession(runner, tabState)

        val tw = terminalManager.toolWindow
        val cm = tw.contentManager

        for (c in cm.contents) {
            val r = TerminalToolWindowManager.getRunnerByContent(c)
            if (r === runner) {
                return Objects.requireNonNull<TerminalWidget?>(
                    TerminalToolWindowManager.findWidgetByContent(c),
                    "TerminalWidget not found for content",
                )
            }
        }

        val selected = cm.selectedContent
        return Objects.requireNonNull<TerminalWidget?>(
            TerminalToolWindowManager.findWidgetByContent(selected!!),
            "Selected TerminalWidget not found",
        )
    }
}
