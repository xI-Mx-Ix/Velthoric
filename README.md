<p align="center">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/velthoric_icon_rounded_corners.png" alt="Velthoric Icon" width="128" height="128">
</p>

<div align="center">
  <h1>Velthoric Physics Mod</h1>
</div>

<p align="center">
  <img src="https://img.shields.io/badge/Environment-Client%20%26%20Server-blue" alt="Environment">
  &nbsp;
  <a href="https://cloudsmith.io/~imx-dev/repos/velthoric">
    <img src="https://img.shields.io/badge/Hosted_by-Cloudsmith-238636?logo=cloudsmith&logoColor=white" alt="Hosted by Cloudsmith">
  </a>
</p>

<div align="center" style="display: flex; justify-content: center; flex-wrap: wrap; gap: 16px; margin-bottom: 12px;">
  <a href="https://fabricmc.net/"><img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/fabric_badge.png" alt="Fabric"></a>
  <a href="https://files.minecraftforge.net/"><img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/forge_badge.png" alt="Forge"></a>
  <a href="https://neoforged.net/"><img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/neoforge_badge.png" alt="NeoForge"></a>
  <a href="https://quiltmc.org/"><img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/quilt_badge.png" alt="Quilt"></a>
</div>

<div align="center" style="margin-bottom: 24px;">
  <a href="https://docs.architectury.dev/api/introduction/">
    <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/architectury_api_badge.png" alt="Architectury API">
  </a>
</div>

---

**💥 What’s this mod all about?**

Velthoric brings **high-performance physics** to Minecraft using the full Jolt Physics engine via JoltJNI. Move, collide, and interact with rigid and soft bodies, ropes, and cloth, all with persistent worlds and a powerful developer API.

### ⚡ **Core Features**

The entire system is built on a highly performant **Structure of Arrays (SoA)** architecture to handle thousands of objects efficiently.

* **Rigid & Soft Bodies:** Full simulation of solid objects as well as deformable things like cloth and ropes.
* **Drivable Vehicles:** Cars and motorcycles with working suspension and wheel physics.
* **Collision Detection:** Efficient broad and narrow phase detection that supports complex shapes and convex hulls.
* **Humanoid Ragdolls:** Create physical ragdolls from living entities.
* **Buoyancy:** Objects float in water and lava based on their volume and density.
* **Block Conversion:** Convert any standard Minecraft block into a dynamic physics object.
* **Constraints:** Connect bodies using joints and other physics constraints.
* **Raycasting:** Precise ray and shape casting against the physics world.
* **Persistence:** Physics bodies are saved with the world and synchronized efficiently between server and client.

### 🛠️ **Developer API**

Create custom physics objects, control rendering, manipulate bodies, and extend the system.  
Documentation can be found [here](https://xi-mx-ix.github.io/velthoric-docs).

### 🎯 **Testing Commands**

---

**`/vxsummon`**  
Spawn built-in and custom objects: boxes, ropes, cloth, marbles and more.  
Also works with any objects registered via the API.

**Test Vehicles:**  
You can spawn a test car or motorcycle that you can drive using:
```
/vxsummon velthoric:car ~ ~ ~
/vxsummon velthoric:motorcycle ~ ~ ~
```
The seat is visible when you press **F3 + B**.

---

**`/vxtest`**  
Quickly create predefined setups for dev & debugging.  
Includes chained boxes, configurable soft bodies, box grids, and more.

---

**`/vxkill @x[...]`**  
Remove physics objects matching a selector.  
You can target objects by `type`, `bodytype`, `distance`, `limit`, and `sort`.

**Example:**
```
/vxkill @x[type=velthoric:box,limit=5,sort=nearest]
```

### 🔧 **Included Tools**

* **PhysicsGun**: Grab, move, rotate, and throw objects around.
* **Magnetizer**: Push things away or pull them closer to you.
* **BoxLauncher**: Spawn and shoot boxes wherever you want.
* **RagdollLauncher**: Launch ragdolls and watch them tumble.
* **ChainCreator**: Connect objects together or tie them to the ground.

⚙️ **Customization**: Just press **TAB** to configure your tools. You can adjust settings like **strength**, **range**, and more to fit your style.

### 💻 **Supported Platforms**

- Windows x86_64
- Linux x86_64
- Linux Arm64
- MacOS x86_64
- MacOS Arm64

> **Note:** All other platforms (e.g., 32-bit systems, Android) are **not supported** and will **crash**.

### ⚠️ **Bug Reports & Mod Compatibility**

Found a bug, crash, or an incompatible mod? Please report it on the **issue tracker**:

<a href="https://github.com/xI-Mx-Ix/Velthoric/issues">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/issues_badge.png" alt="GitHub Issues">
</a>

---

### 🙏 Acknowledgments

* [JoltJNI](https://github.com/stephengold/jolt-jni) - JNI bindings for Jolt Physics
* [Jolt Physics](https://github.com/jrouwe/JoltPhysics) - Physics engine