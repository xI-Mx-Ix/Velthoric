plugins {
    id("multiloader-loader")
    id("fabric-loom-compat")
    id("dev.kikugie.fletching-table.fabric") version "0.1.0-alpha.23"
}

dependencies {
    minecraft("com.mojang:minecraft:${commonMod.mc}")

    if (stonecutter.eval(commonMod.mc, "<=1.21.11")) {
        mappings(loom.layered {
            officialMojangMappings()
            commonMod.depOrNull("parchment")?.let { parchmentVersion ->
                parchment("org.parchmentmc.data:parchment-${commonMod.mc}:$parchmentVersion@zip")
            }
        })
    }

    modImplementation("net.fabricmc:fabric-loader:${commonMod.dep("fabric_loader")}")

    val mixinExtras: Dependency = dependencies.create("io.github.llamalad7:mixinextras-fabric:${commonMod.dep("mixinextras")}")
    annotationProcessor(mixinExtras)
    implementation(mixinExtras)
    include(mixinExtras)

    modImplementation("net.fabricmc.fabric-api:fabric-api:${commonMod.dep("fabric_api")}")

    modImplementation("dev.architectury:architectury-fabric:${commonMod.dep("architectury_api")}")

    // Optional dependencies
    // Mod Menu (https://www.curseforge.com/minecraft/mc-mods/modmenu)
    commonMod.depOrNull("mod_menu")?.let { modMenuVersion ->
        modImplementation("com.terraformersmc:modmenu:${modMenuVersion}")
    }

    modImplementation(fletchingTable.modrinth("sodium", commonMod.mc))
    modImplementation(fletchingTable.modrinth("modmenu", commonMod.mc))
    modImplementation(fletchingTable.modrinth("lithium", commonMod.mc))
}

loom {
    accessWidenerPath = common.project.file("../../src/main/resources/accesswideners/${commonMod.mc}-${mod.id}.accesswidener")

    runs {
        getByName("client") {
            client()
            ideConfigFolder.set("Fabric")
            configName = "Fabric Client"
            ideConfigGenerated(true)
        }
        getByName("server") {
            server()
            ideConfigFolder.set("Fabric")
            configName = "Fabric Server"
            ideConfigGenerated(true)
        }
    }

    if (stonecutter.eval(commonMod.mc, "<=1.21.11")) {
        mixin {
            useLegacyMixinAp = true
            defaultRefmapName = "${mod.id}.refmap.json"
        }
    }
}