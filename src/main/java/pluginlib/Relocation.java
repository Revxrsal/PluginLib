package pluginlib;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Relocation {

    public final String newPattern, pattern;

    private Relocation(String newPattern, String pattern) {
        this.newPattern = newPattern;
        this.pattern = pattern;
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

    public static Relocation of(String prefix, @NotNull String id, @NotNull String pattern) {
        return new Relocation(prefix + id, pattern.replace('#', '.'));
    }

    public static String without(@NotNull String pattern) {
        return pattern.replace('#', '.');
    }

    public static String without(@NotNull char... pattern) {
        return new String(pattern);
    }

}
