package com.tonic.deobfuscation.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.util.AccessBuilder;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("patching a decrypted string leaves other constants that shared its text alone")
class ConstantPoolPatcherTest
{

    @Test
    @DisplayName("a field named like the encrypted string keeps its name")
    void sharedUtf8IsNotRewritten() throws Exception
    {
        ClassFile cf = ClassFactory.createClass(new ClassPool(true), "test/Enc", new AccessBuilder().setPublic().build());
        cf.createNewField(new AccessBuilder().setPrivate().build(), "a", "I", new ArrayList<>());
        ConstPool cp = cf.getConstPool();
        StringRefItem constant = cp.findOrAddString("a");
        int constantIndex = cp.getIndexOf(constant);

        new ConstantPoolPatcher().patchString(cf, constantIndex, "hello");

        assertEquals("hello", ((Utf8Item) cp.getItems().get(constant.getValue())).getValue());
        assertEquals("a", cf.getFields().get(0).getName());
        ClassFile reread = new ClassFile(new ByteArrayInputStream(cf.write()));
        assertEquals("a", reread.getFields().get(0).getName());
    }
}
