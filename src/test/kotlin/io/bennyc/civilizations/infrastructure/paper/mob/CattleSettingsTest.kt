package io.bennyc.civilizations.infrastructure.paper.mob

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import kotlin.test.*

class CattleSettingsTest {
    @Test fun `startup durations default and accept bounds while invalid paths reject`() {
        val c=YamlConfiguration()
        assertEquals(43200000,CattleSettings.read(c).maturityMillis)
        assertEquals(21600000,CattleSettings.read(c).breedingCooldownMillis)
        for(key in listOf("maturity-seconds","breeding-cooldown-seconds")) {
            val path="scarcity.cattle.$key"
            for(value in listOf(1,31536000)) { c.set(path,value);CattleSettings.read(c) }
            for(value in listOf(0,-1,31536001,1.5,"10",true)) {
                c.set(path,value)
                assertTrue(assertFailsWith<IllegalArgumentException> { CattleSettings.read(c) }.message!!.contains(path))
            }
            c.set(path,null)
        }
        c.set("scarcity.cattle","wrong")
        assertTrue(assertFailsWith<IllegalArgumentException> { CattleSettings.read(c) }.message!!.contains("scarcity.cattle"))
    }
    @Test fun `seed files reject traversal bad keys missing coordinates and duplicate IDs`() {
        val dir=Files.createTempDirectory("cattle-settings")
        val file=dir.resolve("seeds.yml")
        try {
            val valid="""seeds:
              - {id: a, world-uuid: '00000000-0000-0000-0000-000000000001', x: 0, y: 64, z: 0}
              - {id: b, world-uuid: '00000000-0000-0000-0000-000000000001', x: 2, y: 64, z: 0}
            """.trimIndent()
            Files.writeString(file,valid)
            assertEquals(2,CattleSettings.seeds(dir,"seeds.yml").size)
            assertFailsWith<IllegalArgumentException> { CattleSettings.seeds(dir,"../seeds.yml") }
            for(bad in listOf(valid.replace("id: b","id: a"),valid.replace("x: 0, ",""),valid.replace("y: 64","y: 2147483647"),valid.replace("z: 0","extra: 0"),valid.replace("00000000-0000-0000-0000-000000000001","bad"))) {
                Files.writeString(file,bad)
                assertFailsWith<IllegalArgumentException> { CattleSettings.seeds(dir,"seeds.yml") }
            }
        } finally { Files.deleteIfExists(file);Files.deleteIfExists(dir) }
    }
}
