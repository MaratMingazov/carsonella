package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_12
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.MAGNESIUM_23
import maratmingazovr.ai.carsonella.chemistry.Element.MAGNESIUM_24
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.SODIUM_23
import maratmingazovr.ai.carsonella.chemistry.Element.NEON_20
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Carbon burning — горение углерода внутри массивной звезды.
 * Два ядра ¹²C сливаются по одному из четырёх каналов:
 * 1: ¹²C + ¹²C → ²⁰Ne + ⁴He + γ   (Q = +4.62 МэВ, доминирующий)
 * 2: ¹²C + ¹²C → ²³Na + p   + γ   (Q = +2.24 МэВ)
 * 3: ¹²C + ¹²C → ²⁴Mg       + γ   (Q = +13.93 МэВ, минорный γ-канал)
 * 4: ¹²C + ¹²C → ²³Mg + n   + γ   (Q = −2.60 МэВ, эндотермический — нейтронный источник)
 *
 * ²³Mg нестабилен (T½ = 11.3 с, β⁺ → ²³Na) — generic BetaPlusDecay подхватит распад,
 * поэтому канал 4 в долгосрочной перспективе сливается с каналом 2, плюс свободный нейтрон.
 */
class StarCarbonBurning(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {

    override val id = "StarCarbonBurning"

    /** [result]/[extras] — выбранный канал реакции; элементы выяснены в matchesAtoms. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
        val result: Element,
        val extras: List<Element>,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null
        val firstAtom = reagents.first() as? Atom ?: return null
        val firstAtomPosition = firstAtom.state().value.centerPosition
        if (firstAtom.element != CARBON_12) return null
        if (!firstAtom.state().value.alive) return null
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                it is Atom && it.element == CARBON_12
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.centerPosition.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        if (distanceSquare >= CARBON_12.details.radius * CARBON_12.details.radius * 2f) return null

        // Случайно выбираем один из четырёх каналов горения углерода.
        val (result, extras) = listOf(
            NEON_20       to listOf(HELIUM_4),
            SODIUM_23     to listOf(Proton),
            MAGNESIUM_24  to emptyList(),
            MAGNESIUM_23  to listOf(NEUTRON),
        ).random(entityGenerator.random)

        return Match(firstAtom, secondAtom, CARBON_12, CARBON_12, result, extras)   // оба реагента — ¹²C по проверке/фильтру
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element, result, extras) = match as Match

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.centerPosition
        val resultRadius = result.details.radius
        // Перенос оболочки на продукт (2C2): два ядра сливаются, их электроны (в звезде 0) переходят
        // на продукт, но не больше его Z; лишние улетают свободными e⁻ (shake-off). Extras (α/p/n) — голые.
        val parentElectrons = atom1.state().value.electrons + atom2.state().value.electrons
        val resultElectrons = minOf(parentElectrons, result.details.p)
        val shakeOff = parentElectrons - resultElectrons
        val spawnList = mutableListOf<() -> Entity>()

        spawnList += {
            entityGenerator.createEntity(
                result,
                resultPosition,
                direction,
                velocity,
                energy = 0f,
                atom1.getEnvironment(),
                electrons = resultElectrons,
            )
        }

        extras.forEachIndexed { index, extra ->
            val offsetSign = if (index % 2 == 0) 1f else -1f
            spawnList += {
                entityGenerator.createEntity(
                    extra,
                    Position(resultPosition.x + offsetSign * 1.5f * direction.x * resultRadius, resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                    environment = atom1.getEnvironment(),
                    electrons = 0,
                )
            }
        }

        repeat(shakeOff) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON,
                    Position(resultPosition.x, resultPosition.y + resultRadius),
                    randomDirection(entityGenerator.random),
                    MAX_VELOCITY,
                    energy = 0f,
                    environment = atom1.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        val resultPhotonEnergy = 1000f
        spawnList += {
            entityGenerator.createEntity(
                PHOTON,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultRadius,
                    resultPosition.y + 1.5f * direction.y * resultRadius,
                ),
                direction,
                MAX_VELOCITY,
                energy = resultPhotonEnergy,
                environment = atom1.getEnvironment(),
                electrons = 0,
            )
        }

        return ReactionOutcome(
            consumed = listOf(atom1, atom2),
            spawn = spawnList,
            description = "$id: ${atom1Element.symbol(atom1.state().value.electrons)} + ${atom2Element.symbol(atom2.state().value.electrons)} -> ${
                result.symbol(
                    resultElectrons
                )
            } + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
