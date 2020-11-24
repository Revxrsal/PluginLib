package pluginlib;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class FileRelocator {

    private static final PluginLib asm = PluginLib.builder()
            .groupId("org.ow2.asm")
            .artifactId("asm")
            .version("7.1")
            .build();

    private static final PluginLib asm_commons = PluginLib.builder()
            .groupId("org.ow2.asm")
            .artifactId("asm-commons")
            .version("7.1")
            .build();

    private static final PluginLib jarRelocator = PluginLib.builder()
            .groupId("me.lucko")
            .artifactId("jar-relocator")
            .version("1.4")
            .build();

    private static Constructor<?> relocatorConstructor;
    private static Method relocateMethod;

    public static void load(Class<? extends DependantJavaPlugin> clazz) {
        asm.load(clazz);
        asm_commons.load(clazz);
        jarRelocator.load(clazz);

        try {
            Class<?> reloc = Class.forName("me.lucko.jarrelocator.JarRelocator");
            relocatorConstructor = reloc.getDeclaredConstructor(File.class, File.class, Map.class);
            relocatorConstructor.setAccessible(true);
            relocateMethod = reloc.getDeclaredMethod("run");
            relocateMethod.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static void remap(File input, File output, Set<Relocation> relocations) throws Exception {
        Map<String, String> mappings = new HashMap<>();
        for (Relocation relocation : relocations) {
            mappings.put(relocation.pattern, relocation.newPattern);
        }

        // create and invoke a new relocator
        Object relocator = relocatorConstructor.newInstance(input, output, mappings);
        relocateMethod.invoke(relocator);
    }
}
