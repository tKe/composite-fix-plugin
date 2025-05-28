package com.github.tke.compositefixplugin

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@Order(ExternalSystemConstants.UNORDERED + 10)
class CompositeBuildJvmOptionsFix : GradleTaskManagerExtension {
    override fun configureTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: GradleExecutionSettings,
        gradleVersion: GradleVersion?
    ) {
        settings.fixKotlinRunForCompositeBuild()
        settings.fixJvmOptionsForCompositeBuild()
    }

    private fun GradleExecutionSettings.fixKotlinRunForCompositeBuild() {
        getUserData(GradleTaskManager.INIT_SCRIPT_KEY)?.let { content ->
            val updated = content.replace(
                "project.rootProject.name + project.path == gradleProjectId",
                "project.buildTreePath == gradleProjectId.substring(gradleProjectId.indexOf(':'))",
            )
            putUserData(GradleTaskManager.INIT_SCRIPT_KEY, updated)
        }
    }

    private fun GradleExecutionSettings.fixJvmOptionsForCompositeBuild() {
        for (initScript in initScripts()) {
            if (initScript.nameWithoutExtension.startsWith("ijJvmOptions")) {
                val content = initScript.readText()
                    .replace(
                        "Properties.tasks.contains(task.path)",
                        "Properties.tasks.contains(task.project.buildTreePath + task.path)"
                    )
                addInitScript("tkeJvmOptions", content)
            }
        }
    }

    fun GradleExecutionSettings.initScripts() = Iterable {
        val src = arguments.iterator()
        iterator {
            while (src.hasNext()) {
                if (src.next() == GradleConstants.INIT_SCRIPT_CMD_OPTION && src.hasNext()) {
                    val path = Path.of(src.next())
                    if (path.exists()) yield(path)
                }
            }
        }
    }
}
