package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimGroupTier
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.BattleCasualtyRules
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.protection.LandProtectionRules
import io.bennyc.civilizations.application.war.WarService
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.BattleCombatRulesSnapshot
import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Path
import java.math.BigDecimal
import java.math.RoundingMode

data class RepairRunnerRules(
    val blocksPerTick: Int,
    val assessmentBlocksPerTick: Int,
) {
    init {
        require(blocksPerTick in 1..MAX_BLOCKS_PER_TICK) {
            "repair.runner.blocks-per-tick must be between 1 and $MAX_BLOCKS_PER_TICK"
        }
        require(assessmentBlocksPerTick in 1..MAX_ASSESSMENT_BLOCKS_PER_TICK) {
            "repair.assessment.blocks-per-tick must be between 1 and " +
                MAX_ASSESSMENT_BLOCKS_PER_TICK
        }
    }

    companion object {
        const val MAX_BLOCKS_PER_TICK = 1_000
        const val MAX_ASSESSMENT_BLOCKS_PER_TICK = 4_000
    }
}

data class BattleResolutionRules(
    val observationsPerTick: Int,
) {
    init {
        require(observationsPerTick in 1..MAX_OBSERVATIONS_PER_TICK) {
            "gameplay.war.resolution-observations-per-tick must be between 1 and " +
                MAX_OBSERVATIONS_PER_TICK
        }
    }

    companion object {
        const val MAX_OBSERVATIONS_PER_TICK = 4_000
    }
}

data class CivilizationsConfiguration(
    val databasePath: Path,
    val claimRules: ClaimRules,
    val phaseRules: GameplayPhaseRules,
    val warRules: WarRulesSnapshot,
    val battleCombatRules: BattleCombatRulesSnapshot,
    val battleResolutionRules: BattleResolutionRules,
    val economyRules: EconomyRules,
    val landProtectionRules: LandProtectionRules,
    val repairRunnerRules: RepairRunnerRules,
) {
    companion object {
        fun load(
            dataFolder: Path,
            config: FileConfiguration,
        ): CivilizationsConfiguration {
            val normalizedDataFolder = dataFolder.toAbsolutePath().normalize()
            val configuredFile = config.requiredString(
                "storage.database-file",
                "v2.database-file",
            )
            require(configuredFile.isNotBlank()) { "storage.database-file cannot be blank" }
            val databasePath = normalizedDataFolder.resolve(configuredFile).normalize()
            require(databasePath.parent == normalizedDataFolder) {
                "storage.database-file must be a file directly inside the plugin data folder"
            }
            val maxClaimArea = config.requiredLong("claims.max-area", "v2.claims.max-area")
            require(maxClaimArea > 0) { "claims.max-area must be positive" }
            val maxClaimCount = config.requiredInt("claims.max-count", "v2.claims.max-count")
            require(maxClaimCount > 0) { "claims.max-count must be positive" }

            val currencyScaleValue = config.requiredInt("economy.currency-scale")
            require(currencyScaleValue in CurrencyScale.MIN_DECIMAL_PLACES..CurrencyScale.MAX_DECIMAL_PLACES) {
                "economy.currency-scale must be between ${CurrencyScale.MIN_DECIMAL_PLACES} and " +
                    CurrencyScale.MAX_DECIMAL_PLACES
            }
            val currencyScale = CurrencyScale(currencyScaleValue)
            return CivilizationsConfiguration(
                databasePath = databasePath,
                claimRules = ClaimRules(
                    maxArea = maxClaimArea,
                    maxClaimsPerCivilization = maxClaimCount,
                    requireEdgeConnection = config.requiredBoolean(
                        "claims.require-edge-connection",
                        "v2.claims.require-edge-connection",
                    ),
                    baseClaimPrice = config.requiredMoney(
                        "claims.base-price",
                        currencyScale,
                    ),
                    pricePerBlock = config.requiredMoney(
                        "claims.price-per-block",
                        currencyScale,
                    ),
                    ordinaryInitiatorRoles = config.requiredMembershipRoles(
                        "claims.ordinary-initiator-roles",
                    ),
                    groupTiers = config.requiredClaimGroupTiers(currencyScale),
                ),
                phaseRules = GameplayPhaseRules(
                    rosterChangesAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.roster-changes",
                        safePhases = ROSTER_PHASES,
                    ),
                    claimCreationAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.claim-creation",
                        safePhases = CLAIM_PHASES,
                    ),
                    memberLandActionsAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.member-land-actions",
                        safePhases = MEMBER_LAND_ACTION_PHASES,
                    ),
                ),
                warRules = WarRulesSnapshot(
                    battleDurationSeconds = config.requiredLong(
                        "gameplay.war.battle-duration-seconds",
                    ).also { seconds ->
                        require(seconds in 1..WarService.MAX_BATTLE_DURATION_SECONDS) {
                            "gameplay.war.battle-duration-seconds must be between 1 and " +
                                WarService.MAX_BATTLE_DURATION_SECONDS
                        }
                    },
                ),
                battleCombatRules = BattleCombatRulesSnapshot(
                    livesPerCombatant = config.requiredInt(
                        "gameplay.war.lives-per-combatant",
                    ).also { lives ->
                        require(lives in 1..BattleCombatRulesSnapshot.MAX_LIVES_PER_COMBATANT) {
                            "gameplay.war.lives-per-combatant must be between 1 and " +
                                BattleCombatRulesSnapshot.MAX_LIVES_PER_COMBATANT
                        }
                    },
                ),
                battleResolutionRules = BattleResolutionRules(
                    observationsPerTick = config.requiredInt(
                        "gameplay.war.resolution-observations-per-tick",
                    ),
                ),
                economyRules = EconomyRules(
                    currencyScale = currencyScale,
                    openingCivilizationBalance = config.requiredMoney(
                        "economy.opening-civilization-balance",
                        currencyScale,
                    ),
                    repair = RepairEconomyRules(
                        restoreOriginalUnitPrice = config.requiredMoney(
                            "economy.repair.restore-original-unit-price",
                            currencyScale,
                        ),
                        removePlacementUnitPrice = config.requiredMoney(
                            "economy.repair.remove-placement-unit-price",
                            currencyScale,
                        ),
                        victorShareBasisPoints = config.requiredPercentageBasisPoints(
                            "economy.repair.victor-share-percent",
                        ),
                        ordinaryInitiatorRoles = config.requiredMembershipRoles(
                            "economy.repair.ordinary-initiator-roles",
                        ),
                    ),
                    battleCasualties = BattleCasualtyRules(
                        attackerDeathCost = config.requiredMoney(
                            "economy.battle-casualties.attacker-death-cost",
                            currencyScale,
                        ),
                        defenderDeathCost = config.requiredMoney(
                            "economy.battle-casualties.defender-death-cost",
                            currencyScale,
                        ),
                        requireAttackerCoverage = config.requiredBoolean(
                            "economy.battle-casualties.require-attacker-coverage",
                        ),
                        lockWithdrawalsDuringBattle = config.requiredBoolean(
                            "economy.battle-casualties.lock-withdrawals-during-battle",
                        ),
                    ),
                ),
                landProtectionRules = LandProtectionRules(
                    enabled = config.requiredBoolean("gameplay.land-protection.enabled"),
                    intervalSeconds = config.requiredLong(
                        "gameplay.land-protection.interval-seconds",
                    ).also { value ->
                        require(value in 60..LandProtectionRules.MAX_INTERVAL_SECONDS) {
                            "gameplay.land-protection.interval-seconds must be between 60 and " +
                                LandProtectionRules.MAX_INTERVAL_SECONDS
                        }
                    },
                    graceSeconds = config.requiredLong(
                        "gameplay.land-protection.grace-seconds",
                    ).also { value ->
                        require(value in 60..LandProtectionRules.MAX_GRACE_SECONDS) {
                            "gameplay.land-protection.grace-seconds must be between 60 and " +
                                LandProtectionRules.MAX_GRACE_SECONDS
                        }
                    },
                    baseCharge = config.requiredMoney(
                        "gameplay.land-protection.base-charge",
                        currencyScale,
                    ),
                    perBlockCharge = config.requiredMoney(
                        "gameplay.land-protection.per-block-charge",
                        currencyScale,
                    ),
                    baseReserve = config.requiredMoney(
                        "gameplay.land-protection.base-reserve",
                        currencyScale,
                    ),
                    perBlockReserve = config.requiredMoney(
                        "gameplay.land-protection.per-block-reserve",
                        currencyScale,
                    ),
                    damageLimitPerExposure = config.requiredInt(
                        "gameplay.land-protection.damage-limit-per-exposure",
                    ).also { value ->
                        require(value in 1..LandProtectionRules.MAX_DAMAGE_LIMIT) {
                            "gameplay.land-protection.damage-limit-per-exposure must be between " +
                                "1 and ${LandProtectionRules.MAX_DAMAGE_LIMIT}"
                        }
                    },
                    assessmentIntervalSeconds = config.requiredLong(
                        "gameplay.land-protection.assessment-interval-seconds",
                    ).also { value ->
                        require(value in 10..3_600) {
                            "gameplay.land-protection.assessment-interval-seconds must be " +
                                "between 10 and 3600"
                        }
                    },
                ).also { rules ->
                    val maximumClaimedArea = try {
                        Math.multiplyExact(maxClaimArea, maxClaimCount.toLong())
                    } catch (failure: ArithmeticException) {
                        throw IllegalArgumentException(
                            "claims.max-area multiplied by claims.max-count must fit in a 64-bit integer",
                            failure,
                        )
                    }
                    try {
                        rules.baseCharge.plus(rules.perBlockCharge.times(maximumClaimedArea))
                    } catch (failure: RuntimeException) {
                        throw IllegalArgumentException(
                            "gameplay.land-protection charge must fit at the configured maximum claimed area",
                            failure,
                        )
                    }
                    try {
                        rules.baseReserve.plus(rules.perBlockReserve.times(maximumClaimedArea))
                    } catch (failure: RuntimeException) {
                        throw IllegalArgumentException(
                            "gameplay.land-protection reserve must fit at the configured maximum claimed area",
                            failure,
                        )
                    }
                },
                repairRunnerRules = RepairRunnerRules(
                    blocksPerTick = config.requiredInt("repair.runner.blocks-per-tick"),
                    assessmentBlocksPerTick = config.requiredInt(
                        "repair.assessment.blocks-per-tick",
                    ),
                ),
            )
        }

        private fun FileConfiguration.path(
            current: String,
            previous: String? = null,
        ): String = when {
            contains(current, true) -> current
            previous != null && contains(previous, true) -> previous
            else -> current
        }

        private fun FileConfiguration.requiredString(
            current: String,
            previous: String? = null,
        ): String {
            val path = path(current, previous)
            return get(path) as? String
                ?: throw IllegalArgumentException("$current must be a string")
        }

        private fun FileConfiguration.requiredLong(
            current: String,
            previous: String? = null,
        ): Long {
            val path = path(current, previous)
            return when (val value = get(path)) {
                is Byte -> value.toLong()
                is Short -> value.toLong()
                is Int -> value.toLong()
                is Long -> value
                else -> throw IllegalArgumentException("$current must be an integer")
            }
        }

        private fun FileConfiguration.requiredInt(
            current: String,
            previous: String? = null,
        ): Int {
            val value = requiredLong(current, previous)
            require(value in Int.MIN_VALUE..Int.MAX_VALUE) {
                "$current must fit in a 32-bit integer"
            }
            return value.toInt()
        }

        private fun FileConfiguration.requiredBoolean(
            current: String,
            previous: String? = null,
        ): Boolean {
            val path = path(current, previous)
            return get(path) as? Boolean
                ?: throw IllegalArgumentException("$current must be true or false")
        }

        private fun FileConfiguration.requiredPhases(
            path: String,
            safePhases: Set<SeasonStatus>,
        ): Set<SeasonStatus> {
            val rawValues = get(path) as? List<*>
                ?: throw IllegalArgumentException("$path must be a YAML list")
            val phases = rawValues.mapIndexed { index, rawValue ->
                val value = rawValue as? String
                    ?: throw IllegalArgumentException("$path[$index] must be a phase name")
                SeasonStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException(
                        "$path[$index] must be one of ${SeasonStatus.entries.joinToString()}",
                    )
            }
            require(phases.size == phases.toSet().size) { "$path cannot contain duplicate phases" }
            val unsafe = phases.filterNot(safePhases::contains)
            require(unsafe.isEmpty()) {
                "$path cannot enable ${unsafe.joinToString()}; safe values are " +
                    safePhases.joinToString()
            }
            return phases.toSet()
        }

        private fun FileConfiguration.requiredMoney(
            path: String,
            currencyScale: CurrencyScale,
        ) = try {
            currencyScale.parse(requiredScalarText(path)).also { amount ->
                require(amount.minorUnits >= 0) { "$path cannot be negative" }
            }
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("$path ${failure.message}", failure)
        }

        private fun FileConfiguration.requiredPercentageBasisPoints(path: String): Int {
            val percentage = try {
                BigDecimal(requiredScalarText(path))
                    .setScale(2, RoundingMode.UNNECESSARY)
            } catch (failure: NumberFormatException) {
                throw IllegalArgumentException("$path must be a percentage from 0 through 100")
            } catch (failure: ArithmeticException) {
                throw IllegalArgumentException("$path may have at most two decimal places")
            }
            require(percentage >= BigDecimal.ZERO && percentage <= BigDecimal(100)) {
                "$path must be between 0 and 100"
            }
            return percentage.movePointRight(2).intValueExact()
        }

        private fun FileConfiguration.requiredMembershipRoles(
            path: String,
        ): Set<MembershipRole> {
            val rawValues = get(path) as? List<*>
                ?: throw IllegalArgumentException("$path must be a YAML list")
            val roles = rawValues.mapIndexed { index, rawValue ->
                val value = rawValue as? String
                    ?: throw IllegalArgumentException("$path[$index] must be a role name")
                MembershipRole.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException(
                        "$path[$index] must be one of ${MembershipRole.entries.joinToString()}",
                    )
            }
            require(roles.size == roles.toSet().size) { "$path cannot contain duplicate roles" }
            require(roles.isNotEmpty()) { "$path must contain at least one role" }
            return roles.toSet()
        }

        private fun FileConfiguration.requiredClaimGroupTiers(
            currencyScale: CurrencyScale,
        ): List<ClaimGroupTier> {
            val path = "claims.groups.tiers"
            val values = get(path) as? List<*>
                ?: throw IllegalArgumentException("$path must be a YAML list")
            require(values.isNotEmpty()) { "$path must contain at least one tier" }
            return values.mapIndexed { index, raw ->
                val entry = raw as? Map<*, *>
                    ?: throw IllegalArgumentException("$path[$index] must be a map")
                fun integer(key: String): Int {
                    val value = entry[key]
                    val long = when (value) {
                        is Byte -> value.toLong()
                        is Short -> value.toLong()
                        is Int -> value.toLong()
                        is Long -> value
                        else -> throw IllegalArgumentException("$path[$index].$key must be an integer")
                    }
                    require(long in Int.MIN_VALUE..Int.MAX_VALUE) {
                        "$path[$index].$key must fit in a 32-bit integer"
                    }
                    return long.toInt()
                }
                fun money(key: String) = try {
                    val rawMoney = entry[key]
                        ?: throw IllegalArgumentException("$path[$index].$key is required")
                    currencyScale.parse(rawMoney.toString()).also {
                        require(it.minorUnits >= 0) { "$path[$index].$key cannot be negative" }
                    }
                } catch (failure: IllegalArgumentException) {
                    throw IllegalArgumentException("$path[$index].$key ${failure.message}", failure)
                }
                val maxGroups = integer("max-groups")
                require(maxGroups == index + 1) {
                    "$path[$index].max-groups must equal ${index + 1}"
                }
                val minimumMembers = integer("minimum-members")
                require(minimumMembers >= 0) {
                    "$path[$index].minimum-members cannot be negative"
                }
                ClaimGroupTier(
                    maxGroups = maxGroups,
                    minimumMembers = minimumMembers,
                    minimumTreasuryBalance = money("minimum-treasury-balance"),
                    establishmentCost = money("establishment-cost"),
                )
            }
        }

        private fun FileConfiguration.requiredScalarText(path: String): String = when (
            val value = get(path)
        ) {
            is String -> value
            is Byte, is Short, is Int, is Long, is Float, is Double -> value.toString()
            else -> throw IllegalArgumentException("$path must be a numeric scalar")
        }

        private val CLAIM_PHASES =
            setOf(SeasonStatus.SETUP, SeasonStatus.PEACE)
        private val ROSTER_PHASES = CLAIM_PHASES + SeasonStatus.WAR
        private val MEMBER_LAND_ACTION_PHASES =
            ROSTER_PHASES
    }
}
