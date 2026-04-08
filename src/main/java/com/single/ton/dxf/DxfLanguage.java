package com.single.ton.dxf;

import com.intellij.lang.Language;

public class DxfLanguage extends Language {
    public static final DxfLanguage INSTANCE = new DxfLanguage();

    private DxfLanguage() {
        super("DXF");
    }
}
