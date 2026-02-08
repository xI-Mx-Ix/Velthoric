<p align="center">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/velthoric_icon_rounded_corners.png" alt="Velthoric Icon" width="128" height="128">
</p>

<div align="center">
  <h1>Velthoric Physics Mod</h1>
  <p><i>Redefining physics in the world of Minecraft.</i></p>
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

### 👋 Welcome to Velthoric

Velthoric is a project dedicated to bringing advanced, high-performance physics to Minecraft.

**Important Note:** Right now, this mod is a **Proof of Concept**. This means we are currently testing what’s possible and building the foundation. It is a work in progress, but it won't stay this way forever. We have big plans to turn this into a fully-fledged, stable physics engine for the community.

### 💥 What makes this mod special?
We aren't just adding simple animations. Velthoric integrates the professional Jolt Physics engine (via JoltJNI) directly into Minecraft. This allows for interactions that feel heavy, realistic, and incredibly smooth.

**Current Features:**
*   **True Rigid and Soft Bodies:** Interact with solid objects, or play with deformable things like cloth and ropes.
*   **Working Vehicles:** Drive cars and motorcycles that feature actual suspension and wheel physics.
*   **Realistic Ragdolls:** Watch living entities react to the world with physical skeletal systems.
*   **Buoyancy:** Objects actually float in water and lava based on their weight and shape.
*   **World Interaction:** Convert standard blocks into dynamic physics objects that fall and collide.
*   **Optimized Performance:** Built using a "Structure of Arrays" (SoA) architecture to handle thousands of objects without killing your frame rate.
*   **Persistence:** Everything stays where it is. Physics bodies are saved with your world and synced perfectly between the server and the client.

---

### 🎮 How to play with it
You can test the engine right now using these commands:

*   **`/vxsummon`**: Spawn objects like boxes, ropes, or marbles.
    *   Try this to test a vehicle: `/vxsummon velthoric:car ~ ~ ~` (Use **F3 + B** to see where to sit).
*   **`/vxtest`**: A quick way to spawn debug setups like chain grids or soft bodies.
*   **`/vxkill`**: Use this to clean up. You can target specific types of objects or everything nearby.
    *   Example: `/vxkill @x[type=velthoric:box,limit=5,sort=nearest]`

### 🔧 Tools included
We’ve added several tools to help you manipulate the world:
*   **PhysicsGun:** Grab, move, and throw objects around.
*   **Magnetizer:** Push or pull things with magnetic force.
*   **Launchers:** Special tools to shoot boxes or ragdolls into the air.
*   **ChainCreator:** Link objects together or anchor them to the ground.

**Hint:** You can press **TAB** while holding any tool to open a menu and tweak settings like strength and range.

---

### 💻 Supported Platforms
Because Velthoric relies on native code for the physics engine, it currently supports:
*   Windows (x86_64)
*   Linux (x86_64 and Arm64)
*   MacOS (x86_64 and Arm64)

**Warning:** Any other platforms, such as 32-bit systems or Android, are not supported and will result in a crash.

### ⚠️ Feedback and Bug Reports
If you find a bug or a compatibility issue, please let us know on the issue tracker:

<a href="https://github.com/xI-Mx-Ix/Velthoric/issues">
  <img src="https://raw.githubusercontent.com/xI-Mx-Ix/Velthoric/refs/heads/master/assets/issues_badge.png" alt="GitHub Issues">
</a>

---

### 🙏 Acknowledgments
This project wouldn't be possible without these amazing resources:
*   [JoltJNI](https://github.com/stephengold/jolt-jni): The JNI bindings for Jolt Physics.
*   [Jolt Physics](https://github.com/jrouwe/JoltPhysics): The powerful engine behind it all.