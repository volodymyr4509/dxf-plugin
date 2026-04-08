package com.single.ton.dxf;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds fold regions to DXF files.
 *
 * <p>DXF is a line-pair format: each "group" consists of a numeric group code on one line
 * followed by its value on the next line. This builder recognises the structural markers:</p>
 * <ul>
 *   <li>{@code 0 / SECTION … 0 / ENDSEC} – top-level sections (HEADER, TABLES, ENTITIES, …)</li>
 *   <li>{@code 0 / TABLE … 0 / ENDTAB}  – symbol tables inside the TABLES section</li>
 *   <li>{@code 0 / BLOCK … 0 / ENDBLK}  – block definitions inside the BLOCKS section</li>
 * </ul>
 *
 * <p>Each fold button appears at the first line of the block and, when collapsed, shows the
 * block's name (e.g. {@code ENTITIES}, {@code TABLE LAYER}, {@code BLOCK *Model_Space}).</p>
 */
public class DxfFoldingBuilder extends FoldingBuilderEx {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(
            @NotNull PsiElement root,
            @NotNull Document document,
            boolean quick) {

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        ASTNode node = root.getNode();
        int lineCount = document.getLineCount();

        int i = 0;
        while (i < lineCount - 1) {
            String groupCode = getLine(document, i);

            if ("0".equals(groupCode)) {
                String value = getLine(document, i + 1);

                switch (value) {
                    case "SECTION": {
                        String sectionName = resolveName(document, i + 2, lineCount);
                        int endLine = findEndMarker(document, "ENDSEC", i + 2, lineCount);
                        if (endLine > i + 1) {
                            add(descriptors, node, document, i, endLine, sectionName);
                            i = endLine + 1;
                            continue;
                        }
                        break;
                    }
                    case "TABLE": {
                        String tableName = "TABLE " + resolveName(document, i + 2, lineCount);
                        int endLine = findEndMarker(document, "ENDTAB", i + 2, lineCount);
                        if (endLine > i + 1) {
                            add(descriptors, node, document, i, endLine, tableName);
                            i = endLine + 1;
                            continue;
                        }
                        break;
                    }
                    case "BLOCK": {
                        // The block name lives at group code 2 somewhere after the BLOCK marker.
                        String blockName = "BLOCK " + resolveNameSearch(document, i + 2, lineCount, 20);
                        int endLine = findEndMarker(document, "ENDBLK", i + 2, lineCount);
                        if (endLine > i + 1) {
                            add(descriptors, node, document, i, endLine, blockName);
                            i = endLine + 1;
                            continue;
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            i++;
        }

        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Reads the trimmed text of a single document line. */
    private static String getLine(Document document, int lineIndex) {
        if (lineIndex < 0 || lineIndex >= document.getLineCount()) return "";
        int start = document.getLineStartOffset(lineIndex);
        int end   = document.getLineEndOffset(lineIndex);
        return document.getText(new TextRange(start, end)).trim();
    }

    /**
     * Returns the value at group code 2 if the very next group is "2/value",
     * otherwise returns an empty string.
     * Used for SECTION and TABLE where the name always immediately follows.
     */
    private static String resolveName(Document document, int fromLine, int lineCount) {
        if (fromLine + 1 < lineCount && "2".equals(getLine(document, fromLine))) {
            return getLine(document, fromLine + 1);
        }
        return "";
    }

    /**
     * Searches up to {@code maxLines} lines ahead for a group code "2" and returns
     * its value. Used for BLOCK definitions where other groups may appear first.
     */
    private static String resolveNameSearch(Document document, int fromLine, int lineCount, int maxLines) {
        for (int j = fromLine; j < Math.min(fromLine + maxLines, lineCount - 1); j++) {
            if ("2".equals(getLine(document, j))) {
                return getLine(document, j + 1);
            }
        }
        return "";
    }

    /**
     * Scans forward from {@code fromLine} for a group-code-0 / {@code marker} pair
     * and returns the line index of {@code marker} (i.e. the closing marker line).
     * Returns -1 if not found.
     */
    private static int findEndMarker(Document document, String marker, int fromLine, int lineCount) {
        for (int i = fromLine; i < lineCount - 1; i++) {
            if ("0".equals(getLine(document, i)) && marker.equals(getLine(document, i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Creates and registers a FoldingDescriptor spanning from startLine to endLine. */
    private static void add(List<FoldingDescriptor> out, ASTNode node,
                            Document document, int startLine, int endLine,
                            String placeholder) {
        int start = document.getLineStartOffset(startLine);
        int end   = document.getLineEndOffset(endLine);
        if (end > start) {
            out.add(new FoldingDescriptor(node, new TextRange(start, end), null, placeholder));
        }
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
