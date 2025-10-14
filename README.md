<p align="center">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/velthoric_icon.png" alt="Velthoric Icon" width="128" height="128">
</p>

<div align="center">
  <h1>Velthoric Physics Mod</h1>
</div>

<p align="center">
  <img src="https://img.shields.io/badge/Environment-Client%20%26%20Server-blue" alt="Environment">
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

* **Rigid & Soft Bodies** – Full simulation of solid and deformable objects.
* **Narrow & Broad Phase Collisions** – Efficient collision detection for all objects.
* **Constraints** – Support for joints and other physics constraints.
* **Shapes** – Various collision shapes supported.
* **Raycasting & Shape Casting** – Detect and interact with objects using rays or shapes.

### 🛠️ **Developer API**

Create custom physics objects, control rendering, manipulate bodies, and extend the system.  
*(Documentation coming soon!)*

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

* **PhysicsGun** – Pick up, move, rotate, and yeet objects.
* **Magnetizer** – Pull or push objects where you aim.
* **BoxThrower** – Spawn and launch boxes instantly.
* **ChainCreator** – Connect two objects (or one to the ground) with a chain.

### ⚠️ **Bug Reports & Mod Compatibility**

Found a bug, crash, or an incompatible mod? Please report it on the **issue tracker**:

<a href="https://github.com/xI-Mx-Ix/Velthoric/issues">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/issues_badge.png" alt="GitHub Issues">
</a>

---

### 🙏 Acknowledgments

* [JoltJNI](https://github.com/stephengold/jolt-jni) - JNI bindings for Jolt Physics
* [Jolt Physics](https://github.com/jrouwe/JoltPhysics) - Physics engine