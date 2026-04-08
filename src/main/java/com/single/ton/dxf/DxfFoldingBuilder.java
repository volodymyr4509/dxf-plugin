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
import java.util.Set;

/**
 * Adds fold regions to DXF files.
 *
 * <p>DXF is a strictly line-paired format: a numeric group code occupies one line, its value
 * the next. Because of this, every group-code-0 line sits on an <em>even</em> absolute line
 * index (0-based). The builder exploits this parity invariant to avoid false positives that
 * would otherwise occur when an integer value happens to be the string {@code "0"}
 * (e.g. group-code 70 with value 0).</p>
 *
 * <p>Fold regions produced:</p>
 * <ul>
 *   <li><b>Section</b>   – {@code 0/SECTION … 0/ENDSEC}  (HEADER, CLASSES, TABLES, …)</li>
 *   <li><b>Table</b>     – {@code 0/TABLE  … 0/ENDTAB}   (one per symbol table)</li>
 *   <li><b>Table entry</b> – each LAYER, LTYPE, APPID, … entry within a TABLE block</li>
 *   <li><b>Block</b>     – {@code 0/BLOCK  … 0/ENDBLK}   (block definitions)</li>
 *   <li><b>Entity/Object</b> – each entity in ENTITIES and each object in OBJECTS</li>
 * </ul>
 */
public class DxfFoldingBuilder extends FoldingBuilderEx {

    /** Group-code-0 values that delimit blocks and must not become item folds themselves. */
    private static final Set<String> STRUCTURAL = Set.of(
            "SECTION", "ENDSEC", "TABLE", "ENDTAB", "BLOCK", "ENDBLK", "EOF"
    );

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
            // In a valid DXF file group codes are always on even-indexed lines.
            if (i % 2 == 0 && "0".equals(getLine(document, i))) {
                String value = getLine(document, i + 1);

                switch (value) {
                    case "SECTION": {
                        String sectionName = resolveName(document, i + 2, lineCount);
                        int endLine = findEndMarker(document, "ENDSEC", i + 2, lineCount);
                        if (endLine > i + 1) {
                            add(descriptors, node, document, i, endLine, sectionName);
                            // Fold individual entities / objects within content sections
                            if ("ENTITIES".equals(sectionName) || "OBJECTS".equals(sectionName)) {
                                addItemFolds(descriptors, node, document, i, endLine,
                                        Set.of("SECTION", "ENDSEC"));
                            }
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
                            // Fold each LAYER / LTYPE / APPID / … entry within the table
                            addItemFolds(descriptors, node, document, i, endLine,
                                    Set.of("TABLE", "ENDTAB"));
                            i = endLine + 1;
                            continue;
                        }
                        break;
                    }
                    case "BLOCK": {
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

    // ── sub-item folding ──────────────────────────────────────────────────────

    /**
     * Creates one fold per item inside a delimited block (TABLE or section).
     *
     * <p>Items are separated by group-code-0 lines. Values listed in {@code skipValues}
     * (the block's own open/close markers) are excluded so they don't become item folds.</p>
     *
     * @param blockStart first line of the enclosing block (the "0" group-code line)
     * @param endLine    line index of the closing marker <em>value</em> (e.g. "ENDSEC")
     * @param skipValues group-code-0 values to skip (structural markers)
     */
    private static void addItemFolds(List<FoldingDescriptor> out, ASTNode node,
                                     Document document, int blockStart, int endLine,
                                     Set<String> skipValues) {
        // Collect the start line of each foldable item within [blockStart, endLine).
        // The end-marker pair sits at (endLine-1, endLine), so we stop before that.
        List<Integer> starts = new ArrayList<>();
        for (int i = blockStart; i <= endLine - 2; i++) {
            if (i % 2 == 0 && "0".equals(getLine(document, i))) {
                String val = getLine(document, i + 1);
                if (!skipValues.contains(val)) {
                    starts.add(i);
                }
            }
        }

        for (int k = 0; k < starts.size(); k++) {
            int itemStart = starts.get(k);
            // Item ends on the line just before the next item's group-code-0,
            // or on the last data line before the closing marker.
            int itemEnd = (k + 1 < starts.size())
                    ? starts.get(k + 1) - 1
                    : endLine - 2;

            if (itemEnd > itemStart) {
                String typeName   = getLine(document, itemStart + 1);
                String placeholder = resolveItemName(document, itemStart, itemEnd, typeName);
                add(out, node, document, itemStart, itemEnd, placeholder);
            }
        }
    }

    /**
     * Returns {@code typeName} optionally followed by the group-code-2 name, e.g.
     * {@code LAYER "walls"} or {@code INSERT "TitleBlock"}.
     */
    private static String resolveItemName(Document document, int startLine, int endLine, String typeName) {
        // Group codes within the item are on even lines; start one pair after the type pair.
        for (int j = startLine + 2; j <= endLine - 1; j += 2) {
            if ("2".equals(getLine(document, j)) && j + 1 <= endLine) {
                return typeName + " \"" + getLine(document, j + 1) + "\"";
            }
        }
        return typeName;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String getLine(Document document, int lineIndex) {
        if (lineIndex < 0 || lineIndex >= document.getLineCount()) return "";
        int start = document.getLineStartOffset(lineIndex);
        int end   = document.getLineEndOffset(lineIndex);
        return document.getText(new TextRange(start, end)).trim();
    }

    /** Name immediately follows as a group-2 pair (SECTION / TABLE pattern). */
    private static String resolveName(Document document, int fromLine, int lineCount) {
        if (fromLine + 1 < lineCount && "2".equals(getLine(document, fromLine))) {
            return getLine(document, fromLine + 1);
        }
        return "";
    }

    /** Searches ahead for group code 2 (BLOCK name may not be the first pair). */
    private static String resolveNameSearch(Document document, int fromLine, int lineCount, int maxLines) {
        for (int j = fromLine; j < Math.min(fromLine + maxLines, lineCount - 1); j += 2) {
            if ("2".equals(getLine(document, j))) {
                return getLine(document, j + 1);
            }
        }
        return "";
    }

    /**
     * Finds the next {@code 0/marker} pair at or after {@code fromLine} and returns
     * the line index of the marker value. Uses parity to avoid matching value "0".
     */
    private static int findEndMarker(Document document, String marker, int fromLine, int lineCount) {
        for (int i = fromLine; i < lineCount - 1; i++) {
            if (i % 2 == 0 && "0".equals(getLine(document, i))
                    && marker.equals(getLine(document, i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

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
