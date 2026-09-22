package com.danielribeiro.scrcpystudio.lifecycle

import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.danielribeiro.scrcpystudio.ui.ScrcpyToolWindowController
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class ScrcpyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.getService(ScrcpyToolWindowController::class.java)
        project.getService(ScrcpySessionService::class.java).startMonitoring()
    }
}
