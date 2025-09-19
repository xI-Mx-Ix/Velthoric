/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.argument;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class VxObjectSelector {

    private final int limit;
    private final MinMaxBounds.Doubles distance;
    @Nullable private final ResourceLocation type;
    private final boolean typeInverse;
    @Nullable private final EBodyType bodyType;
    private final BiConsumer<Vec3, List<VxBody>> order;

    public VxObjectSelector(int limit, MinMaxBounds.Doubles distance, @Nullable ResourceLocation type, boolean typeInverse, @Nullable EBodyType bodyType, BiConsumer<Vec3, List<VxBody>> order) {
        this.limit = limit;
        this.distance = distance;
        this.type = type;
        this.typeInverse = typeInverse;
        this.bodyType = bodyType;
        this.order = order;
    }

    private Predicate<VxBody> buildPredicate(CommandSourceStack source) {
        Vec3 sourcePos = source.getPosition();
        Predicate<VxBody> predicate = (obj) -> true;

        if (type != null) {
            predicate = predicate.and(obj -> (obj.getType().getTypeId().equals(type)) != typeInverse);
        }

        if (bodyType != null) {
            predicate = predicate.and(obj -> {
                int index = obj.getDataStoreIndex();
                if (index != -1) {
                    return obj.getWorld().getObjectManager().getDataStore().bodyType[index] == bodyType;
                }
                return false;
            });
        }

        if (!distance.isAny()) {
            predicate = predicate.and(obj -> {
                var objPos = obj.getTransform().getTranslation();
                return distance.matchesSqr(sourcePos.distanceToSqr(objPos.x(), objPos.y(), objPos.z()));
            });
        }

        return predicate;
    }

    public List<VxBody> select(CommandSourceStack source) {
        VxPhysicsWorld world = VxPhysicsWorld.get(source.getLevel().dimension());
        if (world == null) {
            return Collections.emptyList();
        }

        Predicate<VxBody> predicate = buildPredicate(source);
        List<VxBody> allObjects = new ArrayList<>(world.getObjectManager().getAllObjects());
        List<VxBody> filteredObjects = new ArrayList<>();

        for (VxBody obj : allObjects) {
            if (predicate.test(obj)) {
                filteredObjects.add(obj);
            }
        }

        order.accept(source.getPosition(), filteredObjects);

        if (filteredObjects.size() <= limit) {
            return filteredObjects;
        }

        return filteredObjects.subList(0, limit);
    }
}