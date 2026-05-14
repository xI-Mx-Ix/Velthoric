<p align="center">
  <img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/velthoric_icon_rounded_corners.png" alt="Velthoric Icon" width="128" height="128">
</p>

<div align="center">
  <h1>Velthoric</h1>
  <p>Redefining physics in the world of Minecraft.</p>
</div>

<p align="center">
  <img src="https://img.shields.io/badge/Environment-Client%20%26%20Server-033da8?style=flat-square" alt="Environment">
  &nbsp;
  <a href="https://cloudsmith.io/~velthoric/repos/velthoric/packages/">
    <img src="https://img.shields.io/badge/Hosted_by-Cloudsmith-033da8?style=flat-square&logo=cloudsmith&logoColor=white" alt="Hosted by Cloudsmith">
  </a>
</p>

<div align="center" style="display: flex; justify-content: center; flex-wrap: wrap; gap: 16px; margin-bottom: 12px;">
  <a href="https://fabricmc.net/"><img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/fabric_badge.png" alt="Fabric"></a>
  <a href="https://files.minecraftforge.net/"><img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/forge_badge.png" alt="Forge"></a>
  <a href="https://neoforged.net/"><img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/neoforge_badge.png" alt="NeoForge"></a>
  <a href="https://quiltmc.org/"><img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/quilt_badge.png" alt="Quilt"></a>
</div>

<div align="center" style="margin-bottom: 24px;">
  <a href="https://docs.architectury.dev/api/introduction/">
    <img src="https://raw.githubusercontent.com/velthoric/Velthoric/refs/heads/master/assets/architectury_api_badge.png" alt="Architectury API">
  </a>
</div>

---

### Overview

Velthoric is a modern physics engine integration for Minecraft that brings professional-grade rigid and soft body dynamics to the block world. By utilizing the Jolt Physics engine via native bindings, Velthoric achieves high-fidelity simulations that are physically persistent and synchronized across the network.

**Status:** Velthoric is currently in active development. The codebase is functional but should be considered a "Proof of Concept" as we stabilize the core API and simulation features.

### Key Features

- **Jolt Integration:** Rigid Bodies, Soft Bodies (Cloth/Ropes), Constraints (Joints), and Vehicles.
- **Terrain Interaction:** Advanced collision handling and interaction with Minecraft's block-based terrain.
- **Synchronization API:** Robust state synchronization between Server and Client via `VxSynchronizedData`.
- **Native Performance:** Optimized C++ backend using a "Structure of Arrays" (SoA) architecture for minimal overhead.
- **Persistence:** Full world-saving support for physics bodies.

---

### Building from Source

Since Velthoric relies on native C++ components (`vx-native`), you need a proper build toolchain for your platform.

#### Prerequisites
- **Java 21 JDK**
- **CMake 3.15+**
- **C++ Compiler:**
  - **Windows:** Visual Studio 2022 (with "Desktop development with C++" workload)
  - **Linux:** GCC or Clang (e.g., `build-essential` on Debian/Ubuntu)
  - **macOS:** Xcode Command Line Tools

#### Compilation
To build the project and generate the mod JARs for all supported platforms:
```bash
# This will compile the native libraries and package the JARs
./gradlew build
```
The resulting JARs can be found in the `fabric/build/libs` and `neoforge/build/libs` directories.

---

### Using as a Dependency

Velthoric artifacts are hosted on Cloudsmith. You can include them in your Architectury or platform-specific projects.

#### 1. Add the Repository
```gradle
repositories {
    maven {
        url = "https://dl.cloudsmith.io/public/velthoric/velthoric/maven/"
    }
}
```

#### 2. Add the Dependency
```gradle
dependencies {
    // Replace [loader] with 'fabric' or 'neoforge'
    // Replace [version] with the desired version (e.g., 0.8.0)
    modImplementation "net.xmx.velthoric:velthoric-[loader]:[version]"
}
```

---

### Development Tools & Commands

Velthoric includes a set of debug tools and commands for testing the physics world:

- `/vxsummon <id>`: Spawn physics entities (e.g., `velthoric:car`, `velthoric:marble`).
- `/vxkill`: Clean up nearby physics bodies.
- **PhysicsGun:** Standard tool for grabbing and manipulating rigid bodies.
- **Magnetizer:** Apply magnetic impulses to objects.

---

### Acknowledgments

- [Jolt Physics](https://github.com/jrouwe/JoltPhysics): The underlying physics engine.
- [jolt-jni](https://github.com/stephengold/jolt-jni): Native JNI bindings for Jolt.