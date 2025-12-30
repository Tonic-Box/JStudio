package com.tonic.ui.browser.details;

import com.tonic.parser.attribute.Attribute;

@FunctionalInterface
public interface AttributeFormatter<T extends Attribute> {
    void format(T attr, StringBuilder sb, DetailContext ctx);
}
