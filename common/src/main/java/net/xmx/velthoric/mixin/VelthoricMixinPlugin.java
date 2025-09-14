/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class VelthoricMixinPlugin implements IMixinConfigPlugin {

    public static boolean isSodiumPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            isSodiumPresent = true;
        } catch (ClassNotFoundException e) {
            isSodiumPresent = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean isSodiumMixin = mixinClassName.contains(".sodium.");
        boolean isVanillaMixin = mixinClassName.contains(".vanilla.");

        if (isSodiumMixin) {
            return isSodiumPresent;
        }
        if (isVanillaMixin) {
            return !isSodiumPresent;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}