package dev.observer.core;

public record Target(String key, Position position, Box box, boolean approximate, String evidence, int entityId,
                     double preciseX,double preciseY,double preciseZ) {
    public Target(String key,Position position,Box box,boolean approximate,String evidence,int entityId) {
        this(key,position,box,approximate,evidence,entityId,position.x()+.5,position.y()+.5,position.z()+.5);
    }
    public double distanceSquared(double x,double y,double z) {
        double dx=preciseX-x,dy=preciseY-y,dz=preciseZ-z;return dx*dx+dy*dy+dz*dz;
    }
    public static Target block(Position p) { return new Target(p.toString(),p,Box.block(p),false,"observer.evidence.block",-1); }
}
