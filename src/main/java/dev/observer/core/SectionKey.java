package dev.observer.core;

public record SectionKey(int x, int y, int z) {
    public SectionKey offset(int dx, int dy, int dz) { return new SectionKey(x + dx, y + dy, z + dz); }
    /** Lower bound to any block center in this section, for exact nearest-K pruning. */
    public double minBlockDistanceSquared(double px,double py,double pz) {
        double dx=axis(px,x),dy=axis(py,y),dz=axis(pz,z);return dx*dx+dy*dy+dz*dz;
    }
    private static double axis(double point,int section) {double low=section*16.0+.5,high=low+15;return point<low?low-point:point>high?point-high:0;}
}
