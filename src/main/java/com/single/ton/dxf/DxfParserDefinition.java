package com.single.ton.dxf;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileBase;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class DxfParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(DxfLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new DxfLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            com.intellij.lang.PsiBuilder.Marker file = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            file.done(root);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull PsiElement createElement(@NotNull ASTNode node) {
        throw new UnsupportedOperationException("No custom PSI elements in DXF");
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, DxfLanguage.INSTANCE) {
            @Override
            public @NotNull com.intellij.openapi.fileTypes.FileType getFileType() {
                return DxfFileType.INSTANCE;
            }
        };
    }
}
