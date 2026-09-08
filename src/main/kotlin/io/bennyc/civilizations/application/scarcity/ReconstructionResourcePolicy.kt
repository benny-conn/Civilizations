package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot

/** A reconstruction safety invariant, independent of season balancing or world geography. */
object ReconstructionResourcePolicy {
    // Include placed storage blocks: their provenance cannot make free reconstruction safe.
    private val excluded = setOf(
        "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
        "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
        "redstone_ore", "deepslate_redstone_ore", "emerald_ore", "deepslate_emerald_ore",
        "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore",
        "nether_gold_ore", "nether_quartz_ore", "ancient_debris",
        "coal_block", "iron_block", "copper_block", "gold_block", "redstone_block",
        "emerald_block", "lapis_block", "diamond_block", "netherite_block",
        "raw_iron_block", "raw_copper_block", "raw_gold_block",
        "sugar_cane", "hay_block", "bone_block", "dried_kelp_block", "honey_block",
        "honeycomb_block", "slime_block", "melon", "pumpkin", "carved_pumpkin",
        "jack_o_lantern", "sponge", "wet_sponge", "amethyst_block",
        "budding_amethyst", "amethyst_cluster", "small_amethyst_bud",
        "medium_amethyst_bud", "large_amethyst_bud", "gilded_blackstone",
    ).mapTo(hashSetOf()) { "minecraft:$it" }

    fun excludes(state: SimpleBlockSnapshot): Boolean = state.blockData.substringBefore('[') in excluded

    fun permits(original: SimpleBlockSnapshot, changed: SimpleBlockSnapshot): Boolean =
        !excludes(original) && !excludes(changed)
}

data object ResourceReconstructionDenied : ApplicationFailure {
    override val description = "Resource-bearing blocks cannot be journaled or reconstructed. " +
        "Historical resource damage requires manual resolution; its saved records are unchanged."
}
