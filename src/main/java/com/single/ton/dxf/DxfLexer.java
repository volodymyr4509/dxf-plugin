package com.single.ton.dxf;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal lexer for DXF files. Treats the entire file content as a single TEXT token.
 * The real structure analysis (sections, entities) is done in DxfFoldingBuilder
 * using the Document API directly.
 */
public class DxfLexer extends LexerBase {
    public static final IElementType DXF_TEXT = new IElementType("DXF_TEXT", DxfLanguage.INSTANCE);

    private CharSequence myBuffer;
    private int myStart;
    private int myEnd;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer = buffer;
        myStart = startOffset;
        myEnd = endOffset;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public IElementType getTokenType() {
        return myStart < myEnd ? DXF_TEXT : null;
    }

    @Override
    public int getTokenStart() {
        return myStart;
    }

    @Override
    public int getTokenEnd() {
        return myEnd;
    }

    @Override
    public void advance() {
        myStart = myEnd;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return myBuffer;
    }

    @Override
    public int getBufferEnd() {
        return myEnd;
    }
}
