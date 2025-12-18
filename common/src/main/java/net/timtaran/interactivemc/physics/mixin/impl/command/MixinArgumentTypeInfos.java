/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.mixin.impl.command;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import net.timtaran.interactivemc.physics.command.argument.VxBodyArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin class to intercept the argument type registration.
 * <p>
 * This mixin is used to register the VxBodyArgument type.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(ArgumentTypeInfos.class)
public abstract class MixinArgumentTypeInfos {

    @Shadow
    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> ArgumentTypeInfo<A, T> register(
        Registry<ArgumentTypeInfo<?, ?>> registry, String name, Class<? extends A> clazz, ArgumentTypeInfo<A, T> info) {
        throw new IllegalStateException("Mixin injection failed");
    }

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void onBootstrap(Registry<ArgumentTypeInfo<?, ?>> registry, CallbackInfoReturnable<ArgumentTypeInfo<?, ?>> cir) {

        // Register the VxBodyArgument type.
        register(registry, "velthoric:vx_body", VxBodyArgument.class,
            SingletonArgumentInfo.contextFree(VxBodyArgument::instance));
    }
}