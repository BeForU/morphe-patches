import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

/** Checks the emitted DEX, including every unchanged method in the two touched classes. */
public class VerifyDex {
    static final Map<String, Set<String>> TARGETS = Map.of(
        "LX/02Yd;", Set.of("A09", "A04"), "LX/04QD;", Set.of("GwE"));
    static Map<String, ClassDef> load(File file) throws Exception {
        var result = new HashMap<String, ClassDef>();
        var container = DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
        for (var entry : container.getDexEntryNames())
            for (var cls : container.getEntry(entry).getDexFile().getClasses())
                if (TARGETS.containsKey(cls.getType())) result.put(cls.getType(), cls);
        return result;
    }
    static byte[] unchanged(ClassDef cls) throws Exception {
        var methods = new ArrayList<Method>();
        for (var method : cls.getMethods())
            if (!TARGETS.get(cls.getType()).contains(method.getName())) methods.add(method);
        var filtered = new ImmutableClassDef(cls.getType(), cls.getAccessFlags(), cls.getSuperclass(),
            cls.getInterfaces(), cls.getSourceFile(), cls.getAnnotations(), cls.getFields(), methods);
        var pool = new DexPool(Opcodes.getDefault());
        pool.internClass(filtered);
        var store = new MemoryDataStore();
        pool.writeTo(store);
        return store.getData();
    }
    public static void main(String[] args) throws Exception {
        var original = load(new File(args[0]));
        var patched = load(new File(args[1]));
        if (original.size() != 2 || patched.size() != 2) throw new AssertionError("Missing classes");
        int verified = 0;
        for (var type : TARGETS.keySet()) {
            if (!Arrays.equals(unchanged(original.get(type)), unchanged(patched.get(type))))
                throw new AssertionError("Unrelated methods or class metadata changed: " + type);
            for (var method : patched.get(type).getMethods()) {
                if (!TARGETS.get(type).contains(method.getName())) continue;
                var instructions = method.getImplementation().getInstructions().iterator();
                var first = instructions.next();
                if (method.getName().equals("A09")) {
                    if (first.getOpcode() != Opcode.CONST_4 ||
                        ((NarrowLiteralInstruction) first).getNarrowLiteral() != 1 ||
                        ((OneRegisterInstruction) first).getRegisterA() != 0)
                        throw new AssertionError("Expected true in v0");
                    var second = instructions.next();
                    if (second.getOpcode() != Opcode.RETURN ||
                        ((OneRegisterInstruction) second).getRegisterA() != 0)
                        throw new AssertionError("Expected return v0");
                } else if (first.getOpcode() != Opcode.RETURN_VOID) {
                    throw new AssertionError("Expected animation no-op: " + method);
                }
                System.out.println("VERIFIED: " + method);
                verified++;
            }
            System.out.println("UNCHANGED: all other methods and class metadata in " + type);
        }
        if (verified != 3) throw new AssertionError("Expected exactly three patched methods");
    }
}
