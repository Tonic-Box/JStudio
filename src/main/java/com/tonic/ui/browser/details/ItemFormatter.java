package com.tonic.ui.browser.details;

import com.tonic.parser.constpool.Item;

@FunctionalInterface
public interface ItemFormatter<T extends Item<?>> {
    void format(T item, StringBuilder sb, DetailContext ctx);
}
