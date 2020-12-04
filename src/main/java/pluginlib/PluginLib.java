package pluginlib;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.bukkit.Bukkit;
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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Represents a runtime-downloaded plugin library.
 * <p>
 * This class is immutable, hence is thread-safe. However, certain methods like {@link #load(Class)} are
 * most likely <em>not thread-safe</em>.
 */
public class PluginLib {

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

    /**
     * Creates a standard builder
     *
     * @return The newly created builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new {@link Builder} that downloads its dependency from a URL.
     *
     * @param url URL to download
     * @return The newly created builder
     */
    public static Builder fromURL(@NotNull String url) {
        return new Builder().fromURL(url);
    }

    /**
     * Returns a new {@link Builder}
     *
     * @param xml XML to parse. Must be exactly like the one in maven.
     * @return A new {@link Builder} instance, derived from the XML.
     * @throws IllegalArgumentException If the specified XML cannot be parsed.
     */
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

    /**
     * Loads this library and handles any relocations if any.
     *
     * @param clazz Class to use its {@link ClassLoader} to load.
     */
    public void load(Class<? extends DependentJavaPlugin> clazz) {
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

        try {
            URLClassLoader classLoader = (URLClassLoader) clazz.getClassLoader();
            addURL.invoke(classLoader, saveLocation.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Unable to load dependency: " + saveLocation.toString(), e);
        }
    }

    /**
     * Creates a download {@link URL} for this library.
     *
     * @return The dependency URL
     * @throws MalformedURLException If the URL is malformed.
     */
    public URL asURL() throws MalformedURLException {
        String repo = repository;
        if (!repo.endsWith("/")) {
            repo += "/";
        }
        repo += "%s/%s/%s/%s-%s.jar";

        String url = String.format(repo, groupId.replace(".", "/"), artifactId, version, artifactId, version);
        return new URL(url);
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

    private static Method addURL;

    static {
        try {
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static class Builder {

        private String url = null;
        private String group, artifact, version, repository = "https://repo1.maven.org/maven2/";
        private final ImmutableSet.Builder<Relocation> relocations = ImmutableSet.builder();

        protected Builder() {
        }

        /**
         * Sets the builder to create a static URL dependency.
         *
         * @param url URL of the dependency.
         * @return This builder
         */
        public Builder fromURL(@NotNull String url) {
            this.url = n(url, "provided URL is null!");
            return this;
        }

        /**
         * Sets the group ID of the dependency
         *
         * @param group New group ID to set
         * @return This builder
         */
        public Builder groupId(@NotNull String group) {
            this.group = n(group, "groupId is null!");
            return this;
        }

        /**
         * Sets the artifact ID of the dependency
         *
         * @param artifact New artifact ID to set
         * @return This builder
         */
        public Builder artifactId(@NotNull String artifact) {
            this.artifact = n(artifact, "artifactId is null!");
            return this;
        }

        /**
         * Sets the version of the dependency
         *
         * @param version New version to set
         * @return This builder
         */
        public Builder version(@NotNull String version) {
            this.version = n(version, "version is null!");
            return this;
        }

        /**
         * Sets the version of the dependency, by providing the major, minor, build numbers
         *
         * @param numbers An array of numbers to join using "."
         * @return This builder
         */
        public Builder version(int... numbers) {
            StringJoiner version = new StringJoiner(".");
            for (int i : numbers) version.add(Integer.toString(i));
            return version(version.toString());
        }

        /**
         * Sets the repository to download the dependency from
         *
         * @param repository New repository to set
         * @return This builder
         */
        public Builder repository(@NotNull String repository) {
            this.repository = requireNonNull(repository);
            return this;
        }

        /**
         * A convenience method to set the repository to <em>JitPack</em>
         *
         * @return This builder
         */
        public Builder jitpack() {
            return repository("https://jitpack.io/");
        }

        /**
         * A convenience method to set the repository to <em>Bintray - JCenter</em>
         *
         * @return This builder
         */
        public Builder jcenter() {
            return repository("https://jcenter.bintray.com/");
        }

        /**
         * A convenience method to set the repository to <em>Maven Central</em>
         *
         * @return This builder
         */
        public Builder mavenCentral() {
            return repository("https://repo1.maven.org/maven2/");
        }

        /**
         * A convenience method to set the repository to <em>Aikar's Repository</em>
         *
         * @return This builder
         */
        public Builder aikar() {
            return repository("https://repo.aikar.co/content/groups/aikar/");
        }

        /**
         * Adds a new relocation rule
         *
         * @param relocation New relocation rule to add
         * @return This builder
         */
        public Builder relocate(@NotNull Relocation relocation) {
            relocations.add(n(relocation, "relocation is null!"));
            return this;
        }

        /**
         * Constructs a {@link PluginLib} from the provided values
         *
         * @return A new, immutable {@link PluginLib} instance.
         * @throws NullPointerException if any of the required properties is not provided.
         */
        public PluginLib build() {
            if (url != null)
                return new StaticURLPluginLib(group, n(artifact, "artifactId"), n(version, "version"), repository, relocations.build(), url);
            return new PluginLib(n(group, "groupId"), n(artifact, "artifactId"), n(version, "version"), n(repository, "repository"), relocations.build());
        }

        private static <T> T n(T t, String m) {
            return requireNonNull(t, m);
        }

    }

    /**
     * A convenience method to check whether a class exists at runtime or not.
     *
     * @param className Class name to check for
     * @return true if the class exists, false if otherwise.
     */
    public static boolean classExists(@NotNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final List<PluginLib> toInstall = new ArrayList<>();

    static void loadLibs() {
        libFile.get();
        for (PluginLib pluginLib : toInstall) {
            pluginLib.load(DependentJavaPlugin.class);
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
    private static class LibrariesOptions {

        @SerializedName("relocation-prefix")
        private String relocationPrefix = null;
        @SerializedName("libraries-folder")
        private String librariesFolder = "libs";
        @SerializedName("global-relocations")
        private Map<String, String> globalRelocations = Collections.emptyMap();
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

    private static final Supplier<File> libFile = Suppliers.memoize(() -> {
        Map<?, ?> map = (Map<?, ?>) new Yaml().load(new InputStreamReader(requireNonNull(DependentJavaPlugin.class.getClassLoader().getResourceAsStream("plugin.yml"), "Jar does not contain plugin.yml")));
        String name = map.get("name").toString();
        String folder = "libs";
        if (map.containsKey("runtime-libraries")) {
            Gson gson = new Gson();
            LibrariesOptions options = gson.fromJson(gson.toJson(map.get("runtime-libraries")), LibrariesOptions.class);
            if (options.librariesFolder != null && !options.librariesFolder.isEmpty())
                folder = options.librariesFolder;
            String prefix = options.relocationPrefix == null ? null : options.relocationPrefix;
            requireNonNull(prefix, "relocation-prefix must be defined in runtime-libraries!");
            Set<Relocation> globalRelocations = new HashSet<>();
            for (Entry<String, String> global : options.globalRelocations.entrySet()) {
                globalRelocations.add(new Relocation(global.getKey(), prefix + "." + global.getValue()));
            }
            for (Entry<String, RuntimeLib> lib : options.libraries.entrySet()) {
                RuntimeLib runtimeLib = lib.getValue();
                Builder b = runtimeLib.builder();
                if (runtimeLib.relocation != null && !runtimeLib.relocation.isEmpty())
                    for (Entry<String, String> s : runtimeLib.relocation.entrySet()) {
                        b.relocate(new Relocation(s.getKey(), prefix + "." + s.getValue()));
                    }
                for (Relocation relocation : globalRelocations) {
                    b.relocate(relocation);
                }
                toInstall.add(b.build());
            }
        }
        File file = new File(Bukkit.getUpdateFolderFile().getParentFile() + File.separator + name, folder);
        file.mkdirs();
        return file;
    });
}
