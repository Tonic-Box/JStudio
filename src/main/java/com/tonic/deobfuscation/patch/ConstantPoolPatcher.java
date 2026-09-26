package com.tonic.deobfuscation.patch;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.deobfuscation.model.DeobfuscationResult;
import com.tonic.service.ConsoleLogService;

import java.util.List;

/** Writes decrypted strings back into a class's constant pool by rewriting the UTF8 entry a string constant points to. */
public class ConstantPoolPatcher
{

    /**
     * Replaces the text of a string constant.
     *
     * @param classFile the class to patch
     * @param cpIndex the constant-pool index of the string constant
     * @param newValue the new text
     * @throws IllegalArgumentException if the index is out of range, is not a string constant, or the constant does not point to a valid UTF8 entry
     */
    public void patchString(ClassFile classFile, int cpIndex, String newValue)
    {
        ConstPool cp = classFile.getConstPool();
        List<Item<?>> items = cp.getItems();

        if (cpIndex < 0 || cpIndex >= items.size())
        {
            throw new IllegalArgumentException("CP index " + cpIndex + " is out of range");
        }

        Item<?> item = items.get(cpIndex);
        if (!(item instanceof StringRefItem))
        {
            throw new IllegalArgumentException("CP index " + cpIndex + " is not a StringRef");
        }

        StringRefItem stringRef = (StringRefItem) item;
        int utf8Index = stringRef.getValue();

        if (utf8Index < 0 || utf8Index >= items.size())
        {
            throw new IllegalArgumentException("StringRef points to invalid UTF8 index");
        }

        Item<?> utf8Item = items.get(utf8Index);
        if (!(utf8Item instanceof Utf8Item))
        {
            throw new IllegalArgumentException("StringRef points to non-UTF8 item");
        }

        Utf8Item utf8 = (Utf8Item) utf8Item;
        utf8.setValue(newValue);
    }

    /**
     * Patches every successful, not yet applied result that belongs to this class and marks it applied; failures are logged and skipped.
     *
     * @param classFile the class to patch
     * @param results the decryption results, possibly for several classes
     * @return how many results were applied
     */
    public int applyResults(ClassFile classFile, List<DeobfuscationResult> results)
    {
        int applied = 0;

        for (DeobfuscationResult result : results)
        {
            if (!result.isSuccess() || result.isApplied())
            {
                continue;
            }

            if (!result.getClassName().equals(classFile.getClassName()))
            {
                continue;
            }

            try
            {
                patchString(classFile, result.getConstantPoolIndex(), result.getDecryptedValue());
                result.setApplied(true);
                applied++;
            }
            catch (Exception e)
            {
                ConsoleLogService.getInstance().error("[ConstantPoolPatcher] Failed to apply patch at CP#" + result.getConstantPoolIndex() + ": " + e.getMessage());
            }
        }

        return applied;
    }

}
