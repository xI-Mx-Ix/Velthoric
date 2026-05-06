plugins {
    id("multiloader-common")
    id("fabric-loom-compat")
    id("dev.kikugie.fletching-table.fabric") version "0.1.0-alpha.23"
}

loom {
    accessWidenerPath = common.project.file("../../src/main/resources/accesswideners/${commonMod.mc}-${mod.id}.accesswidener")

    if (stonecutter.eval(commonMod.mc, "<=1.21.11")) {
        mixin {
            useLegacyMixinAp = false
        }
    }
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

    modCompileOnly("net.fabricmc:fabric-loader:${commonMod.dep("fabric_loader")}")
}