package dev.observer.core;

public record Position(int x, int y, int z) implements Comparable<Position> {
    public double distanceSquared(double px, double py, double pz) {
        double dx = x + .5 - px, dy = y + .5 - py, dz = z + .5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }
    public Position offset(int dx, int dy, int dz) { return new Position(x + dx, y + dy, z + dz); }
    public SectionKey section() { return new SectionKey(x >> 4, y >> 4, z >> 4); }
    @Override public int compareTo(Position b) {
        int c = Integer.compare(x, b.x); if (c == 0) c = Integer.compare(y, b.y);
        return c == 0 ? Integer.compare(z, b.z) : c;
    }
}
