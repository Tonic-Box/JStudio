package com.tonic.ui.browser.details;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

public class DetailContext {

    private final ConstPool constPool;
    private final boolean showHex;

    public DetailContext(ConstPool constPool, boolean showHex) {
        this.constPool = constPool;
        this.showHex = showHex;
    }

    public ConstPool getConstPool() {
        return constPool;
    }

    public boolean isShowHex() {
        return showHex;
    }

    public String getUtf8(int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof Utf8Item) {
                return ((Utf8Item) item).getValue();
            }
        } catch (Exception e) {
            // Item not found or wrong type
        }
        return "#" + index;
    }

    public Item<?> getItem(int index) {
        try {
            return constPool.getItem(index);
        } catch (Exception e) {
            return null;
        }
    }
}
