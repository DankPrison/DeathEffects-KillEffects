package com.dank420girl.core.effect.shape;

/**
 * A small mutable 3D vector for animation math, without the overhead of Bukkit's Location.
 * The rotation methods follow EffectLib's VectorUtils so that ported animations look the same.
 */
public final class Vec3 {
    public double x;
    public double y;
    public double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 multiply(double factor) {
        x *= factor;
        y *= factor;
        z *= factor;
        return this;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vec3 rotateX(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double ny = y * cos - z * sin;
        double nz = y * sin + z * cos;
        y = ny;
        z = nz;
        return this;
    }

    public Vec3 rotateY(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nx = x * cos + z * sin;
        double nz = x * -sin + z * cos;
        x = nx;
        z = nz;
        return this;
    }

    public Vec3 rotateZ(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nx = x * cos - y * sin;
        double ny = x * sin + y * cos;
        x = nx;
        y = ny;
        return this;
    }

    /** A random point on the unit sphere. */
    public static Vec3 randomUnit(java.util.Random random) {
        double x;
        double y;
        double z;
        double lengthSquared;
        do {
            x = random.nextDouble() * 2 - 1;
            y = random.nextDouble() * 2 - 1;
            z = random.nextDouble() * 2 - 1;
            lengthSquared = x * x + y * y + z * z;
        } while (lengthSquared > 1 || lengthSquared < 1.0E-4);
        double length = Math.sqrt(lengthSquared);
        return new Vec3(x / length, y / length, z / length);
    }
}
