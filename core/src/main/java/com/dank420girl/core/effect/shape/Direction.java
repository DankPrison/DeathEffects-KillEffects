package com.dank420girl.core.effect.shape;

/**
 * Which way a growing shape (vortex, DNA) points.
 */
public enum Direction {
    /** Straight up. */
    UP,
    /** Horizontally, the way the player was facing. */
    FORWARD;

    /** Turns a shape built along the Y axis so it points this way. */
    void orient(Vec3 v, float yaw) {
        if (this == FORWARD) {
            v.rotateX(Math.toRadians(90));
            v.rotateY(Math.toRadians(-yaw));
        }
    }
}
