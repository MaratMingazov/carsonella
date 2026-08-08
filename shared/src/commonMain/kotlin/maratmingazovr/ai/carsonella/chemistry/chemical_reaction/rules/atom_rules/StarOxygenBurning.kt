package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16
import maratmingazovr.ai.carsonella.chemistry.Element.PHOSPHORUS_31
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.HYDROGEN
import maratmingazovr.ai.carsonella.chemistry.Element.SILICON_28
import maratmingazovr.ai.carsonella.chemistry.Element.SULFUR_31
import maratmingazovr.ai.carsonella.chemistry.Element.SULFUR_32
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Oxygen burning — горение кислорода внутри массивной звезды.
 * Два ядра ¹⁶O сливаются по одному из четырёх каналов:
 * 1: ¹⁶O + ¹⁶O → ²⁸Si + ⁴He + γ   (Q = +9.59 МэВ, доминирующий)
 * 2: ¹⁶O + ¹⁶O → ³¹P  + p   + γ   (Q = +7.68 МэВ)
 * 3: ¹⁶O + ¹⁶O → ³¹S  + n   + γ   (Q = +1.50 МэВ, нейтронный канал)
 * 4: ¹⁶O + ¹⁶O → ³²S        + γ   (Q = +16.54 МэВ, минорный γ-канал)
 *
 * Симметрия с StarCarbonBurning: четвёрки каналов (α, p, γ, n) у обеих реакций совпадают
 * по типу — у углерода ²⁰Ne+α / ²³Na+p / ²⁴Mg+γ / ²³Mg+n, у кислорода ²⁸Si+α / ³¹P+p / ³²S+γ / ³¹S+n.
 */
class StarOxygenBurning(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {

    override val id = "StarOxygenBurning"

    /** [result]/[extras] — выбранный канал реакции; элементы выяснены в matchesAtom. */
    private data class Match(
        val atom1: Entity,
        val atom2: Entity,
        val atom1Element: Element,
        val atom2Element: Element,
        val result: Element,
        val extras: List<Element>,
    ) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null
        val atomPosition = atom.kinematics.position
        if (atom.element != OXYGEN_16) return null
        if (atom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        val (secondAtom, distanceSquare) = neighbors
            .filter {
                it is Atom && it.element == OXYGEN_16
            }
            .filter { it.state().value.alive }
            .map { it to it.kinematics.position.distanceSquareTo(atomPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

        if (distanceSquare >= OXYGEN_16.details.radius * OXYGEN_16.details.radius * 2f) return null

        // Случайно выбираем один из четырёх каналов горения кислорода.
        val (result, extras) = listOf(
            SILICON_28    to listOf(HELIUM_4),
            PHOSPHORUS_31 to listOf(HYDROGEN),
            SULFUR_31     to listOf(NEUTRON),
            SULFUR_32     to emptyList(),
        ).random(entityGenerator.random)

        return Match(atom, secondAtom, OXYGEN_16, OXYGEN_16, result, extras)   // оба реагента — ¹⁶O по проверке/фильтру
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element, result, extras) = match as Match

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.kinematics.position
        val resultRadius = result.details.radius
        // Перенос оболочки на продукт (2C2): два ядра сливаются, их электроны (в звезде 0) переходят
        // на продукт, но не больше его Z; лишние улетают свободными e⁻ (shake-off). Extras (α/p/n) — голые.
        val parentElectrons = atom1.electrons + atom2.electrons
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
            spawn = spawnList,)
    }
}
