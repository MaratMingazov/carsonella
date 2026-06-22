package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

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
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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
) : ReactionRule {

    override val id = "StarCarbonBurning"

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
        if (firstAtom.state().value.element != CARBON_12) return false
        if (!firstAtom.state().value.alive) return false
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == CARBON_12 }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        if (distanceSquare >= CARBON_12.details.radius * CARBON_12.details.radius * 2f) return false

        // Случайно выбираем один из четырёх каналов горения углерода.
        val (result, extras) = listOf(
            NEON_20       to listOf(HELIUM_4),
            SODIUM_23     to listOf(Proton),
            MAGNESIUM_24  to emptyList(),
            MAGNESIUM_23  to listOf(NEUTRON),
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
        // Перенос оболочки на продукт (2C2): два ядра сливаются, их электроны (в звезде 0) переходят
        // на продукт, но не больше его Z; лишние улетают свободными e⁻ (shake-off). Extras (α/p/n) — голые.
        val parentElectrons = a1.state().value.electrons + a2.state().value.electrons
        val resultElectrons = minOf(parentElectrons, result.details.p)
        val shakeOff = parentElectrons - resultElectrons
        val spawnList = mutableListOf<() -> Entity<*>>()

        spawnList += {
            entityGenerator.createEntity(
                result,
                resultPosition,
                direction,
                velocity,
                energy = a1.state().value.energy + a2.state().value.energy,
                a1.getEnvironment(),
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
                    environment = a1.getEnvironment(),
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
                    20f,
                    energy = 0f,
                    environment = a1.getEnvironment(),
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
                10f,
                energy = resultPhotonEnergy,
                environment = a1.getEnvironment(),
                electrons = 0,
            )
        }

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "$id: ${atom1Element.symbol(a1.state().value.electrons)} + ${atom2Element.symbol(a2.state().value.electrons)} -> ${result.symbol(resultElectrons)} + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
