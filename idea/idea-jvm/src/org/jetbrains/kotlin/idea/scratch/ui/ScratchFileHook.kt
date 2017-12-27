/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider

class ScratchFileHook(project: Project) : AbstractProjectComponent(project) {

    override fun projectOpened() {
        myProject.messageBus.connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, ScratchEditorListener())
    }

    override fun projectClosed() {
        val fileEditorManager = FileEditorManager.getInstance(myProject)
        val editors = fileEditorManager.allEditors
        editors.forEach {
            val panel = it.scratchTopPanel
            if (panel != null) {
                fileEditorManager.removeTopComponent(it, panel)
            }
        }
    }

    private inner class ScratchEditorListener : FileEditorManagerListener {
        private fun isPluggable(file: VirtualFile): Boolean {
            if (!file.isValid) return false
            if (!ScratchFileService.isInScratchRoot(file)) return false
            val psiFile = PsiManager.getInstance(myProject).findFile(file) ?: return false
            return ScratchFileLanguageProvider.get(psiFile.fileType) != null
        }

        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            if (!isPluggable(file)) return

            val fileEditorManager = FileEditorManager.getInstance(myProject)
            val editors = fileEditorManager.getAllEditors(file).filter { it.scratchTopPanel == null }
            editors.forEach {
                val newPanel = ScratchTopPanel(myProject)
                it.scratchTopPanel = newPanel
                fileEditorManager.addTopComponent(it, newPanel)
            }
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            if (!isPluggable(file)) return

            val fileEditorManager = FileEditorManager.getInstance(myProject)
            val editors = fileEditorManager.getAllEditors(file)
            editors.forEach { editor ->
                editor.scratchTopPanel?.let { panel ->
                    fileEditorManager.removeTopComponent(editor, panel)
                }
            }
        }
    }
}
