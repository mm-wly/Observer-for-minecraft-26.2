package dev.observer.core;

/** Integer, half-open bounds, matching Minecraft AABB conventions. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box { if (minX >= maxX || minY >= maxY || minZ >= maxZ) throw new IllegalArgumentException("Empty box"); }
    public static Box block(Position p) { return new Box(p.x(), p.y(), p.z(), p.x()+1, p.y()+1, p.z()+1); }
    public Box union(Box b) { return new Box(Math.min(minX,b.minX), Math.min(minY,b.minY), Math.min(minZ,b.minZ), Math.max(maxX,b.maxX), Math.max(maxY,b.maxY), Math.max(maxZ,b.maxZ)); }
    public Box expand(int n) { return new Box(minX-n,minY-n,minZ-n,maxX+n,maxY+n,maxZ+n); }
    public Position center() { return new Position(Math.floorDiv(minX+maxX-1,2),Math.floorDiv(minY+maxY-1,2),Math.floorDiv(minZ+maxZ-1,2)); }
    public Position nearest(double x, double y, double z) {
        return new Position(clamp((int)Math.floor(x),minX,maxX-1),clamp((int)Math.floor(y),minY,maxY-1),clamp((int)Math.floor(z),minZ,maxZ-1));
    }
    private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
    public int span() { return Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ)); }
}
