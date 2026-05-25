package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.BERYLLIUM_7_ION_4
import maratmingazovr.ai.carsonella.chemistry.Element.DEUTERIUM_ION
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_3_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4_ION_2
import maratmingazovr.ai.carsonella.chemistry.Element.LITHIUM_7_ION_3
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator

/**
 * pp-chain - Процесс внутри звезды
 *
 * Ветвь pp-I:
 * 1: p + p -> D⁺            (2 протона образуют ион Дейтерия ²H⁺, один из протонов превращается в нейтрон)
 * 2: D⁺ + p -> ³He²⁺ + γ    (Дейтерий и протон образуют Гелий-3 ³He²⁺, выделяется энергия)
 * 3: ³He²⁺ + ³He²⁺ -> ⁴He²⁺ + 2p   (терминатор pp-I)
 *
 * Ветвь pp-II (альтернативный путь после ³He²⁺):
 * 1: ³He²⁺ + ⁴He²⁺ -> ⁷Be⁴⁺ + γ   (этот шаг живёт в StarAlphaGammaReaction — через alphaGammaResult у ³He²⁺)
 * 2: ⁷Be⁴⁺ + e⁻ -> ⁷Li³⁺          (захват электрона ядром; в реальности выделяется νₑ, у нас условно — фотон)
 * 3: ⁷Li³⁺ + p -> 2 ⁴He²⁺         (горение лития обратно в гелий)
 */
class StarPPChain(
    private val entityGenerator: IEntityGenerator,
) : ReactionRule {

    override val id = "StarPPChain"

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
        val firstAtomElement = firstAtom.state().value.element
        if (!firstAtom.state().value.alive) return false
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        // В зависимости от первого реагента определяем какой второй реагент нужен и что родится.
        // Шаг ³He+⁴He → ⁷Be (pp-II стартовый) сюда не входит — он живёт в StarAlphaGammaReaction
        // через alphaGammaResult на ³He²⁺.
        val (secondElement, result, extras) = when (firstAtomElement) {
            Proton            -> Triple(Proton,         DEUTERIUM_ION,    emptyList<Element>())
            DEUTERIUM_ION     -> Triple(Proton,         HELIUM_3_ION_2,   emptyList())
            HELIUM_3_ION_2    -> Triple(HELIUM_3_ION_2, HELIUM_4_ION_2,   listOf(Proton, Proton))
            BERYLLIUM_7_ION_4 -> Triple(ELECTRON,       LITHIUM_7_ION_3,  emptyList())
            LITHIUM_7_ION_3   -> Triple(Proton,         HELIUM_4_ION_2,   listOf(HELIUM_4_ION_2))
            else -> return false
        }

        // Ищем ближайший подходящий второй реагент
        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter { it.state().value.element == secondElement }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        return if (distanceSquare < firstAtomElement.details.radius * secondElement.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            resultElement = result
            extraElements = extras
            true
        } else {
            false
        }
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

        // Дополнительные продукты (для шага ³He+³He → ⁴He + 2p)
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
                )
            }
        }

        // На каждом шаге pp-цепочки выделяется фотон ~1000 эВ
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
            )
        }

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "$id: ${atom1Element.details.symbol} + ${atom2Element.details.symbol} -> ${result.details.symbol} + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
