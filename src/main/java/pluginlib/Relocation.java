package pluginlib;

import java.util.Objects;

/**
 * Represents a relocation rule.
 * <p>
 * This class is immutable, hence is safe to share across threads.
 */
public final class Relocation {

    private final String path, newPath;

    /**
     * Creates a new relocation rule
     *
     * @param path    Path to relocate
     * @param newPath New path to replace it
     */
    public Relocation(String path, String newPath) {
        this.path = path.replace('/', '.').replace('#', '.');
        this.newPath = newPath.replace('/', '.').replace('#', '.');
    }

    public String getPath() {
        return path;
    }

    public String getNewPath() {
        return newPath;
    }

    @Override public String toString() {
        return String.format("Relocation{path='%s', newPath='%s'}", path, newPath);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relocation)) return false;
        Relocation that = (Relocation) o;
        return Objects.equals(path, that.path) &&
                Objects.equals(newPath, that.newPath);
    }

    @Override public int hashCode() {
        return Objects.hash(path, newPath);
    }
}
