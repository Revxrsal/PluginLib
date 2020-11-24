package pluginlib;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

public class PluginLib {

    private static final List<PluginLib> toInstall = new ArrayList<>();

    @SuppressWarnings("ConstantConditions")
    private static final Supplier<File> libFile = Suppliers.memoize(() -> {
        Map<?, ?> map = (Map<?, ?>) new Yaml().load(new InputStreamReader(DependantJavaPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")));
        String name = map.get("name").toString();
        String folder = "libs";
        if (map.containsKey("runtime-libraries")) {
            Gson gson = new Gson();
            LibrariesOptions options = gson.fromJson(gson.toJson(map.get("runtime-libraries")), LibrariesOptions.class);
            if (options.librariesFolder != null && !options.librariesFolder.isEmpty())
                folder = options.librariesFolder;
            String prefix = options.relocationPrefix == null ? null : options.relocationPrefix + ".";
            for (Entry<String, RuntimeLib> lib : options.libraries.entrySet()) {
                RuntimeLib runtimeLib = lib.getValue();
                Builder b = runtimeLib.builder();
                if (runtimeLib.relocation != null && !runtimeLib.relocation.isEmpty())
                    for (Entry<String, String> s : runtimeLib.relocation.entrySet()) {
                        b.relocate(Relocation.of(Objects.requireNonNull(prefix, "relocation-prefix must be defined in runtime-libraries!"), s.getKey(), s.getValue()));
                    }
                toInstall.add(b.build());
            }
        }
        File file = new File(Bukkit.getUpdateFolderFile().getParentFile() + File.separator + name, folder);
        file.mkdirs();
        return file;
    })::get;

    public final String groupId, artifactId, version, repository;
    public final ImmutableSet<Relocation> relocationRules;
    private final boolean hasRelocations;

    public PluginLib(String groupId, String artifactId, String version, String repository, ImmutableSet<Relocation> relocationRules) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.repository = repository;
        this.relocationRules = relocationRules;
        hasRelocations = !relocationRules.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public URL asURL() throws MalformedURLException {
        String repo = repository;
        if (!repo.endsWith("/")) {
            repo += "/";
        }
        repo += "%s/%s/%s/%s-%s.jar";

        String url = String.format(repo, groupId.replace(".", "/"), artifactId, version, artifactId, version);
        return new URL(url);
    }

    public static Builder fromURL(@NotNull String url) {
        return new Builder().fromURL(url);
    }

    public static Builder parseXML(@Language("XML") @NotNull String xml) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xml));
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();
            return builder()
                    .groupId(doc.getElementsByTagName("groupId").item(0).getTextContent())
                    .artifactId(doc.getElementsByTagName("artifactId").item(0).getTextContent())
                    .version(doc.getElementsByTagName("version").item(0).getTextContent());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage());
        }
    }

    public void load(Class<? extends JavaPlugin> clazz) {
        String name = artifactId + "-" + version;
        File parent = libFile.get();
        File saveLocation = new File(parent, name + ".jar");
        if (!saveLocation.exists()) {

            try {
                URL url = asURL();
                saveLocation.createNewFile();
                try (InputStream is = url.openStream()) {
                    Files.copy(is, saveLocation.toPath(), REPLACE_EXISTING);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        if (!saveLocation.exists()) {
            throw new RuntimeException("Unable to download dependency: " + artifactId);
        }
        if (hasRelocations) {
            System.out.println(relocationRules);
            File relocated = new File(parent, name + "-relocated.jar");
            if (!relocated.exists()) {
                try {
                    relocated.createNewFile();
                    FileRelocator.remap(saveLocation, new File(parent, name + "-relocated.jar"), relocationRules);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            saveLocation = relocated;
        }

        URLClassLoader classLoader = (URLClassLoader) clazz.getClassLoader();
        try {
            addURL.invoke(classLoader, saveLocation.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Unable to load dependency: " + saveLocation.toString(), e);
        }
    }

    private static Method addURL;

    static {
        try {
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override public String toString() {
        return "PluginLib{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", repository='" + repository + '\'' +
                ", relocationRules=" + relocationRules +
                ", hasRelocations=" + hasRelocations +
                '}';
    }

    public static class Builder {

        private String url = null;
        private String group, artifact, version, repository = "https://repo1.maven.org/maven2/";
        private final ImmutableSet.Builder<Relocation> relocations = ImmutableSet.builder();

        protected Builder() {
        }

        public Builder fromURL(@NotNull String url) {
            this.url = requireNonNull(url);
            return this;
        }

        public Builder groupId(@NotNull String group) {
            this.group = requireNonNull(group);
            return this;
        }

        public Builder artifactId(@NotNull String artifact) {
            this.artifact = requireNonNull(artifact);
            return this;
        }

        public Builder version(@NotNull String version) {
            this.version = requireNonNull(version);
            return this;
        }

        public Builder version(int... numbers) {
            StringJoiner version = new StringJoiner(".");
            for (int i : numbers) version.add(i + "");
            return version(version.toString());
        }

        public Builder repository(@NotNull String repository) {
            this.repository = requireNonNull(repository);
            return this;
        }

        public Builder jitpack() {
            return repository("https://jitpack.io/");
        }

        public Builder relocate(@NotNull Relocation relocation) {
            relocations.add(n(relocation, "relocation is null!"));
            return this;
        }

        public PluginLib build() {
            if (url != null)
                return new StaticURLPluginLib(group, n(artifact, "artifact"), version, repository, relocations.build(), url);
            return new PluginLib(n(group, "group"), n(artifact, "artifact"), n(version, "version"), n(repository, "repository"), relocations.build());
        }

        private static <T> T n(T t, String m) {
            return requireNonNull(t, m);
        }

    }

    public static boolean classExists(@NotNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static void loadLibs() {
        libFile.get();
        for (PluginLib pluginLib : toInstall) {
            pluginLib.load(DependantJavaPlugin.class);
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
    private static class LibrariesOptions {

        @SerializedName("relocation-prefix")
        private String relocationPrefix = null;
        @SerializedName("libraries-folder")
        private String librariesFolder = "libs";
        private Map<String, RuntimeLib> libraries = Collections.emptyMap();
    }

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
    private static class RuntimeLib {

        @Language("XML") private String xml = null;
        private String url = null;
        private String groupId = null, artifactId = null, version = null;
        private Map<String, String> relocation = null;
        private String repository = null;

        Builder builder() {
            Builder b;
            if (url != null)
                b = fromURL(url);
            else if (xml != null)
                b = parseXML(xml);
            else
                b = new Builder();
            if (groupId != null) b.groupId(groupId);
            if (artifactId != null) b.artifactId(artifactId);
            if (version != null) b.version(version);
            if (repository != null) b.repository(repository);
            return b;
        }

    }

    private static class StaticURLPluginLib extends PluginLib {

        private final String url;

        public StaticURLPluginLib(String groupId, String artifactId, String version, String repository, ImmutableSet<Relocation> relocationRules, String url) {
            super(groupId, artifactId, version, repository, relocationRules);
            this.url = url;
        }

        @Override public URL asURL() throws MalformedURLException {
            return new URL(url);
        }
    }

}
