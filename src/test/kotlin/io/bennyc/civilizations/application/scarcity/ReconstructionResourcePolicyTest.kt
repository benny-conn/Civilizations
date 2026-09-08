package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconstructionResourcePolicyTest {
    @Test fun `state properties cannot disguise excluded resources`() {
        assertTrue(ReconstructionResourcePolicy.excludes(SimpleBlockSnapshot("minecraft:deepslate_redstone_ore[lit=true]")))
        assertTrue(ReconstructionResourcePolicy.excludes(SimpleBlockSnapshot("minecraft:diamond_block")))
        assertFalse(ReconstructionResourcePolicy.excludes(SimpleBlockSnapshot("minecraft:stone")))
    }
    @Test fun `ordinary reconstruction remains available but resource replacement is denied`() {
        val stone = SimpleBlockSnapshot("minecraft:stone")
        val air = SimpleBlockSnapshot("minecraft:air")
        val diamond = SimpleBlockSnapshot("minecraft:diamond_ore")
        assertTrue(ReconstructionResourcePolicy.permits(stone, air))
        assertTrue(ReconstructionResourcePolicy.permits(air, stone))
        assertFalse(ReconstructionResourcePolicy.permits(diamond, air))
        assertFalse(ReconstructionResourcePolicy.permits(air, diamond))
    }
}
