package net.xmx.vortex.physics.entitycollision;

import com.github.stephengold.joltjni.BodyFilter;

final class SingleBodyFilter extends BodyFilter {
        private int bodyIdToCollide;

        void setBodyIdToCollide(int bodyId) {
            this.bodyIdToCollide = bodyId;
        }

        @Override
        public boolean shouldCollide(int bodyId) {
            return bodyId == bodyIdToCollide;
        }
    }