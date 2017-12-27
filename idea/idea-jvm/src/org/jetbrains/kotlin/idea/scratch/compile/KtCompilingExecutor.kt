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

package org.jetbrains.kotlin.idea.scratch.compile

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.compiler.ex.CompilerPathsEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.CompilationErrorHandler
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.filterClassFiles
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.console.KotlinConsoleKeeper
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFullyAndGetResult
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils
import org.jetbrains.kotlin.idea.refactoring.getLineNumber
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputType
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

class KtCompilingExecutor(file: ScratchFile) : ScratchCompilingExecutor(file) {
    private val log = Logger.getInstance(this.javaClass)

    override fun execute() {
        handlers.forEach { it.onStart(file) }

        val module = file.module
        if (module == null) {
            error("Module should be selected")
            return
        }

        if (checkForErrors(file.psiFile as KtFile) == null) {
            error("Compilation Error")
            return
        }

        val result = KtScratchSourceFileProcessor().process(file)
        when (result) {
            is KtScratchSourceFileProcessor.Result.Error -> return error(result.message)
            is KtScratchSourceFileProcessor.Result.OK -> {
                val modifiedScratchSourceFile = KtPsiFactory(file.psiFile.project).createFileWithLightClassSupport("tmp.kt", result.code, file.psiFile)

                try {
                    val analysisResult = checkForErrors(modifiedScratchSourceFile) ?: return
                    val tempDir = compileFileToTempDir(modifiedScratchSourceFile, analysisResult)

                    try {
                        val handler = CapturingProcessHandler(createCommandLine(module, result.mainClassName, tempDir.path))
                        ProcessOutputParser().parse(handler.runProcess())
                    }
                    finally {
                        tempDir.delete()
                    }
                }
                catch (e: Throwable) {
                    log.info(result.code, e)
                    handlers.forEach { it.error(file, e.message ?: "Couldn't compile ${file.psiFile.name}") }
                }
                finally {
                    handlers.forEach { it.onFinish(file) }
                }
            }
        }
    }

    private fun compileFileToTempDir(psiFile: KtFile, analysisResult: ExtendedAnalysisResult): File {
        val (bindingContext, moduleDescriptor, files) = analysisResult

        val generateClassFilter = object : GenerationState.GenerateClassFilter() {
            override fun shouldGeneratePackagePart(ktFile: KtFile) = ktFile == psiFile
            override fun shouldAnnotateClass(processingClassOrObject: KtClassOrObject) = true
            override fun shouldGenerateClass(processingClassOrObject: KtClassOrObject) = processingClassOrObject.containingKtFile == psiFile
            override fun shouldGenerateScript(script: KtScript) = false
        }

        val state = GenerationState.Builder(
                file.psiFile.project,
                ClassBuilderFactories.binaries(false),
                moduleDescriptor,
                bindingContext,
                files,
                CompilerConfiguration.EMPTY
        ).generateDeclaredClassFilter(generateClassFilter).build()

        KotlinCodegenFacade.compileCorrectFiles(state, CompilationErrorHandler.THROW_EXCEPTION)

        return writeClassFilesToTempDir(state)
    }

    private fun writeClassFilesToTempDir(state: GenerationState): File {
        val classFiles = state.factory.asList().filterClassFiles()

        val dir = FileUtil.createTempDirectory("compile", "scratch")
        for (classFile in classFiles) {
            val tmpOutFile = File(dir, classFile.relativePath)
            tmpOutFile.parentFile.mkdirs()
            tmpOutFile.createNewFile()
            tmpOutFile.writeBytes(classFile.asByteArray())
        }
        return dir
    }

    private fun createCommandLine(module: Module, mainClassName: String, tempOutDir: String): GeneralCommandLine {
        val javaParameters = KotlinConsoleKeeper.createJavaParametersWithSdk(module)
        javaParameters.mainClass = mainClassName

        val compiledModulePath = CompilerPathsEx.getOutputPaths(arrayOf(module)).toList()
        val moduleDependencies = OrderEnumerator.orderEntries(module).recursively().pathsList.pathList

        javaParameters.classPath.add(tempOutDir)
        javaParameters.classPath.addAll(compiledModulePath)
        javaParameters.classPath.addAll(moduleDependencies)

        val toCommandLine = javaParameters.toCommandLine()
        println(toCommandLine.commandLineString)
        return toCommandLine
    }

    private fun checkForErrors(psiFile: KtFile): ExtendedAnalysisResult? {
        return runReadAction {
            AnalyzingUtils.checkForSyntacticErrors(psiFile)

            val analysisResult = psiFile.analyzeFullyAndGetResult()

            if (analysisResult.isError()) {
                throw analysisResult.error
            }

            val bindingContext = analysisResult.bindingContext
            val diagnostics = bindingContext.diagnostics.filter { it.severity == Severity.ERROR }
            if (diagnostics.isNotEmpty()) {
                diagnostics.forEach { diagnostic ->
                    if (diagnostic.psiElement.containingFile == psiFile) {
                        val start = diagnostic.psiElement.getLineNumber(true)
                        val end = diagnostic.psiElement.getLineNumber(false)
                        handlers.forEach { handler ->
                            handler.handle(file,
                                           file.findExpression(start, end),
                                           ScratchOutput(DefaultErrorMessages.render(diagnostic), ScratchOutputType.ERROR))
                        }
                    }
                }
                return@runReadAction null
            }

            val (newBindingContext, files) = DebuggerUtils.analyzeInlinedFunctions(psiFile.getResolutionFacade(), psiFile, false)
            ExtendedAnalysisResult(newBindingContext, analysisResult.moduleDescriptor, files)
        }
    }

    private data class ExtendedAnalysisResult(val bindingContext: BindingContext, val moduleDescriptor: ModuleDescriptor, val files: List<KtFile>)

    private fun error(message: String) {
        handlers.forEach { it.error(file, message) }
        handlers.forEach { it.onFinish(file) }
    }

    private fun ScratchFile.findExpression(lineStart: Int, lineEnd: Int): ScratchExpression {
        return getExpressions().first { it.lineStart == lineStart && it.lineEnd == lineEnd }
    }

    private inner class ProcessOutputParser {
        fun parse(processOutput: ProcessOutput) {
            val out = processOutput.stdout
            val err = processOutput.stderr
            if (err.isNotBlank()) {
                handlers.forEach { it.error(file, err) }
            }
            if (out.isNotBlank()) {
                parseStdOut(out)
            }
        }

        private fun parseStdOut(out: String) {
            var results = StringBuilder()
            var userOutput = StringBuilder()
            for (line in out.split("\n")) {
                if (isOutputEnd(line)) {
                    return
                }

                if (line.startsWith(KtScratchSourceFileProcessor.GENERATED_OUTPUT_PREFIX)) {
                    val lineWoPrefix = line.removePrefix(KtScratchSourceFileProcessor.GENERATED_OUTPUT_PREFIX)
                    if (isResultEnd(lineWoPrefix)) {
                        val (startLine, endLine) = extractLineInfoFrom(lineWoPrefix) ?: continue
                        val scratchExpression = file.findExpression(startLine, endLine)
                        if (userOutput.isNotBlank()) {
                            handlers.forEach { it.handle(file, scratchExpression, ScratchOutput(userOutput.toString(), ScratchOutputType.OUTPUT)) }
                            userOutput = StringBuilder()
                        }
                        if (results.isNotBlank()) {
                            handlers.forEach { it.handle(file, scratchExpression, ScratchOutput(results.toString(), ScratchOutputType.RESULT)) }
                            results = StringBuilder()
                        }
                        continue
                    }

                    if (lineWoPrefix != Unit.toString()) {
                        results.appendWithSeparator(lineWoPrefix)
                    }
                }
                else {
                    userOutput.appendWithSeparator(line)
                }
            }
        }

        private fun StringBuilder.appendWithSeparator(str: String) = if (isNotBlank()) append("; ").append(str) else append(str)

        private fun isOutputEnd(line: String) = line.removeSuffix("\n") == KtScratchSourceFileProcessor.END_OUTPUT_MARKER
        private fun isResultEnd(line: String) = line.startsWith(KtScratchSourceFileProcessor.LINES_INFO_MARKER)

        private fun extractLineInfoFrom(encoded: String): Pair<Int, Int>? {
            val nums = encoded.removePrefix(KtScratchSourceFileProcessor.LINES_INFO_MARKER).removeSuffix("\n").split('|')
            if (nums.size == 2) {
                try {
                    val (a, b) = nums[0].toInt() to nums[1].toInt()
                    if (a > -1 && b > -1) {
                        return a to b
                    }
                }
                catch (e: NumberFormatException) {
                }
            }
            return null
        }
    }
}

