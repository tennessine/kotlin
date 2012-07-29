/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package org.jetbrains.jet.lang.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JetParser implements PsiParser {
    private JetParserDefinition parserDefinition;

    public JetParser(JetParserDefinition parserDefinition) {
        this.parserDefinition = parserDefinition;
    }

    @Override
    @NotNull
    public ASTNode parse(IElementType iElementType, PsiBuilder psiBuilder) {
        throw new IllegalStateException("use another parse");
    }

    // we need this method because we need psiFile
    @NotNull
    public ASTNode parse(IElementType iElementType, PsiBuilder psiBuilder, PsiFile psiFile) {
        JetParsing jetParsing = JetParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        if (parserDefinition.isScript(psiFile)) {
            jetParsing.parseScript();
        }
        else {
            jetParsing.parseFile();
        }
        return psiBuilder.getTreeBuilt();
    }
}
