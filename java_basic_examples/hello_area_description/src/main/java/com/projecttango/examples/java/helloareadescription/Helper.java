package com.projecttango.examples.java.helloareadescription;

/**
 * Created by ChrisYang on 3/8/17.
 */

public class Helper {
    public static double getEulerAngleZ(float[] quaternion)
    {
        float x = quaternion[0];
        float y = quaternion[1];
        float z = quaternion[2];
        float w = quaternion[3];

        // yaw (z-axis rotation)
        double t1 = 2.0 * (w*z+x*y);
        double t2 = 1.0 - 2.0 * (y*y+z*z);

        // this will match the rotation in a cartestian coordinate system
        return (Math.toDegrees(Math.atan2(t1, t2)) + 450) % 360;
    }

    public static float roundToNearestHalf(float f) {
        return ((float)Math.round(f*2))/2;
    }
}
