package net.xmx.xbullet.api;

import net.xmx.xbullet.init.XBullet;

public class XBulletAPI {

    private static XBulletAPI instance;

    private final PhysicsObjectModule physicsObjectModule;

    private XBulletAPI() {
        XBullet.LOGGER.debug("XBulletAPI singleton constructor called.");
        this.physicsObjectModule = new PhysicsObjectModule();
    }

    public static XBulletAPI getInstance() {
        if (instance == null) {
            synchronized (XBulletAPI.class) {
                if (instance == null) {
                    instance = new XBulletAPI();
                    XBullet.LOGGER.debug("XBulletAPI singleton created.");
                }
            }
        }
        return instance;
    }

    /**
     * Access the main module for physics object interactions.
     * @return The singleton instance of the PhysicsObjectModule.
     */
    public PhysicsObjectModule objects() {
        return this.physicsObjectModule;
    }
}