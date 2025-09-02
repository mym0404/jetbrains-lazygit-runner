@file:Suppress("ktlint:standard:no-wildcard-imports")

package happy.mjstudio.jetbrains.lazygit.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class LazygitAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ToolWindowManager.getInstance(project)

        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val terminalToolWindow = terminalManager.toolWindow

        // Check if terminal tool window was initially visible
        val wasTerminalVisible = terminalToolWindow?.isVisible == true

        val terminalWidget =
            terminalManager.createNewSession().apply {
                sendCommandToExecute("lazygit")
            }

        // Execute lazygit command
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
}
