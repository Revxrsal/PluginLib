package pluginlib;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a relocation rule.
 * <p>
 * This class is immutable, hence is safe to share across threads.
 */
public final class Relocation {

    public final String newPattern, pattern;

    private Relocation(String newPattern, String pattern) {
        this.newPattern = newPattern;
        this.pattern = pattern;
    }

    /**
     * Creates a new relocation rule.
     *
     * @param prefix  The prefix of the relocation.
     * @param id      ID of the library.
     * @param pattern Path or pattern to replace all occurences with <code>prefix.id</code>
     * @return The new relocation rule
     */
    public static Relocation of(String prefix, @NotNull String id, @NotNull String pattern) {
        return new Relocation(prefix + "." + id, pattern.replace('#', '.'));
    }

    @Override public String toString() {
        return "Relocation{" +
                "newPattern='" + newPattern + '\'' +
                ", pattern='" + pattern + '\'' +
                '}';
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relocation)) return false;
        Relocation that = (Relocation) o;
        return Objects.equals(newPattern, that.newPattern) &&
                Objects.equals(pattern, that.pattern);
    }

    @Override public int hashCode() {
        return Objects.hash(newPattern, pattern);
    }

}
