package com.tonic.deobfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.builder.ClassBuilder;
import com.tonic.deobfuscation.detection.DecryptorDetector;
import com.tonic.deobfuscation.model.DecryptorCandidate;
import com.tonic.deobfuscation.model.DeobfuscationResult;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.service.ProjectService;
import com.tonic.type.AccessFlags;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("decryptors run with the arguments each call site passes")
class CallSiteDecryptionTest implements AccessFlags
{

    private static final int T_BYTE = 8;

    private ClassFile cf;

    @BeforeEach
    void setUp()
    {
        ClassPool pool = new ClassPool(true);
        ProjectModel project = ProjectService.getInstance().createProject("decryption");
        project.setClassPool(pool);
        cf = ClassBuilder.create("a/Enc").access(ACC_PUBLIC)
                .addMethod(ACC_PUBLIC | ACC_STATIC, "choose", "(Ljava/lang/String;I)Ljava/lang/String;").code().iload(1).bipush(7).if_icmpne("wrong").aload(0).areturn().label("wrong").ldc("wrong").areturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "fromBytes", "([B)Ljava/lang/String;").code().aload(0).iconst(1).baload().bipush(42).if_icmpne("bad").ldc("ok").areturn().label("bad").ldc("bad").areturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "index", "(I)Ljava/lang/String;").code().iload(0).iconst(5).if_icmpne("other").ldc("five").areturn().label("other").ldc("other").areturn().end().end()
                .addMethod(ACC_PUBLIC | ACC_STATIC, "use", "(I)V").code()
                .ldc("secret").bipush(7).invokestatic("a/Enc", "choose", "(Ljava/lang/String;I)Ljava/lang/String;").pop()
                .iconst(2).newarray(T_BYTE).dup().iconst(1).bipush(42).bastore().invokestatic("a/Enc", "fromBytes", "([B)Ljava/lang/String;").pop()
                .iconst(5).invokestatic("a/Enc", "index", "(I)Ljava/lang/String;").pop()
                .ldc("secret").iload(0).invokestatic("a/Enc", "choose", "(Ljava/lang/String;I)Ljava/lang/String;").pop()
                .vreturn().end().end()
                .build();
        project.addClass(cf);
        DeobfuscationService.getInstance().initialize();
    }

    @AfterEach
    void tearDown()
    {
        ProjectService.getInstance().closeProject();
    }

    private DecryptorCandidate candidate(String name)
    {
        MethodEntry method = cf.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        return new DecryptorCandidate(cf, method, DecryptorCandidate.DecryptorType.fromDescriptor(method.getDesc()), 1.0);
    }

    @Test
    @DisplayName("a string and an int are passed as recovered; a non-constant argument is reported, not guessed")
    void stringAndIntArguments()
    {
        List<DeobfuscationResult> results = DeobfuscationService.getInstance().decryptCallSites(cf, candidate("choose"));

        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess(), results.get(0).getErrorMessage());
        assertEquals("secret", results.get(0).getDecryptedValue());
        assertFalse(results.get(0).isApplicable());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(1).getErrorMessage().contains("argument 2"));
    }

    @Test
    @DisplayName("a byte array literal built before the call is passed element by element")
    void byteArrayLiteral()
    {
        List<DeobfuscationResult> results = DeobfuscationService.getInstance().decryptCallSites(cf, candidate("fromBytes"));

        assertEquals(1, results.size());
        assertEquals("ok", results.get(0).getDecryptedValue(), results.get(0).getErrorMessage());
    }

    @Test
    @DisplayName("an int decryptor receives the pushed int, not a constant-pool index")
    void intArgument()
    {
        List<DeobfuscationResult> results = DeobfuscationService.getInstance().decryptCallSites(cf, candidate("index"));

        assertEquals(1, results.size());
        assertEquals("five", results.get(0).getDecryptedValue(), results.get(0).getErrorMessage());
    }

    @Test
    @DisplayName("the detector sees backward branches as loops and only String-producing calls as creating strings")
    void detectorIndicators()
    {
        ClassFile detect = ClassBuilder.create("a/Detect").access(ACC_PUBLIC)
                .addMethod(ACC_PRIVATE | ACC_STATIC, "decrypt", "(Ljava/lang/String;)Ljava/lang/String;").code().label("top").iinc(0, 0).iconst(0).ifeq("top").aload(0).invokevirtual("java/lang/String", "intern", "()Ljava/lang/String;").areturn().end().end()
                .addMethod(ACC_PRIVATE | ACC_STATIC, "decode", "(Ljava/lang/String;)Ljava/lang/String;").code().iconst(1).invokestatic("java/lang/Math", "abs", "(I)I").pop().aload(0).areturn().end().end()
                .build();

        List<DecryptorCandidate> candidates = new DecryptorDetector().scan(detect);
        DecryptorCandidate looping = candidates.stream().filter(c -> c.getMethodName().equals("decrypt")).findFirst().orElse(null);
        DecryptorCandidate plain = candidates.stream().filter(c -> c.getMethodName().equals("decode")).findFirst().orElse(null);

        assertNotNull(looping);
        assertTrue(looping.getIndicators().contains("Contains loops"));
        assertNotNull(plain);
        assertFalse(plain.getIndicators().contains("Creates String object"));
    }
}
