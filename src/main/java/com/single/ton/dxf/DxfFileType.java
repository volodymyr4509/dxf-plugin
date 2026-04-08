package com.single.ton.dxf;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DxfFileType extends LanguageFileType {
    public static final DxfFileType INSTANCE = new DxfFileType();

    private DxfFileType() {
        super(DxfLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "DXF";
    }

    @Override
    public @NotNull String getDescription() {
        return "DXF CAD file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "dxf";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
