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

package org.jetbrains.kotlin.idea.scratch.output

import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ui.scratchTopPanel

object ProgressBarOutputHandler : ScratchOutputHandlerAdapter() {
    override fun onStart(file: ScratchFile) {
        val progressBar = file.scratchTopPanel?.progressBar ?: return
        progressBar.isVisible = true
        progressBar.value = 0
        progressBar.minimum = 0
        progressBar.maximum = file.getExpressions().count()
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        // todo expressions without output are skipped
        // todo expression with multiple output should increment progress only once
        val progressBar = file.scratchTopPanel?.progressBar ?: return
        progressBar.value++
    }

    override fun onFinish(file: ScratchFile) {
        val progressBar = file.scratchTopPanel?.progressBar ?: return
        progressBar.isVisible = false
    }
}