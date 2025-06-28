package net.xmx.xbullet.model.objmodel;

import java.lang.reflect.Method;

public final class ShaderCompat {
    private static final boolean isIrisShaderActive;

    static {
        boolean active;
        try {

            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstanceMethod = irisApiClass.getMethod("getInstance");
            Object irisApiInstance = getInstanceMethod.invoke(null);
            Method isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            active = (Boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
        } catch (Exception e) {

            active = false;
        }
        isIrisShaderActive = active;
    }

    public static boolean isShaderPackActive() {
        return isIrisShaderActive;
    }
}