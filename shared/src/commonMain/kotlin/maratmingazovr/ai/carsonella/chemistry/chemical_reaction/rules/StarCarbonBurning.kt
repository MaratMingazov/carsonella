package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.CARBON_12_ION_6
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.MG_24_ION_12
import maratmingazovr.ai.carsonella.chemistry.Element.NA_23_ION_11
import maratmingazovr.ai.carsonella.chemistry.Element.NEON_20_ION_10
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * Carbon burning — горение углерода внутри массивной звезды.
 * Два ядра ¹²C сливаются по одному из трёх каналов:
 * 1: ¹²C + ¹²C → ²⁰Ne + ⁴He + γ
 * 2: ¹²C + ¹²C → ²³Na + p  + γ
 * 3: ¹²C + ¹²C → ²⁴Mg      + γ
 */
class StarCarbonBurning(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {

    override val id = "StartCarbonBurning"

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null
    private var resultElement : Element? = null
    private var extraElements : List<Element> = emptyList()

    override fun matches(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        resultElement = null
        extraElements = emptyList()
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        if (firstAtom.state().value.element != CARBON_12_ION_6) return false
        if (!firstAtom.state().value.alive) return false
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == CARBON_12_ION_6 }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        if (distanceSquare >= CARBON_12_ION_6.details.radius * CARBON_12_ION_6.details.radius * 2f) return false

        // Случайно выбираем один из трёх каналов горения углерода.
        val (result, extras) = listOf(
            NEON_20_ION_10 to listOf(HELIUM_4_ION_2),
            NA_23_ION_11   to listOf(Proton),
            MG_24_ION_12   to emptyList(),
        ).random(entityGenerator.random)

        atom1 = firstAtom
        atom2 = secondAtom
        resultElement = result
        extraElements = extras
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val result = resultElement!!
        val extras = extraElements

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val resultRadius = result.details.radius
        val atom1Element = a1.state().value.element
        val atom2Element = a2.state().value.element
        val spawnList = mutableListOf<() -> Entity<*>>()

        spawnList += {
            entityGenerator.createEntity(
                result,
                resultPosition,
                direction,
                velocity,
                energy = a1.state().value.energy + a2.state().value.energy,
                a1.getEnvironment(),
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
                )
            }
        }

        spawnList += {
            entityGenerator.createEntity(
                PHOTON,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultRadius,
                    resultPosition.y + 1.5f * direction.y * resultRadius,
                ),
                direction,
                10f,
                energy = 1000f,
            )
        }

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${result.details.symbol} + ${PHOTON.details.symbol}"
        )
    }
}