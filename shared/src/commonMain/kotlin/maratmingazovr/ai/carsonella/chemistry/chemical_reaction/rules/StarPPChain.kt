package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.BERYLLIUM_7
import maratmingazovr.ai.carsonella.chemistry.Element.BORON_8
import maratmingazovr.ai.carsonella.chemistry.Element.DEUTERIUM
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_3
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.LITHIUM_7
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
 *
 * Ветвь pp-III (редкая, ~0.1% после ⁷Be⁴⁺):
 * 1: ⁷Be⁴⁺ + p -> ⁸B⁵⁺ + γ        (захват протона на бериллий-7; ⁸B нестабилен)
 * 2: ⁸B⁵⁺ -> ⁸Be⁴⁺ + e⁺ + νₑ     (β⁺-распад бора-8; живёт в generic BetaPlusDecay)
 *
 * Внутри одного firstAtomElement может быть несколько кандидатов на secondElement — перебираются по порядку,
 * первый найденный поблизости побеждает. Для ⁷Be⁴⁺ приоритет — захват электрона (pp-II ~99.9%), затем
 * захват протона (pp-III ~0.1%).
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

        // В зависимости от первого реагента определяем какие варианты второго реагента возможны и что родится.
        // Шаг ³He+⁴He → ⁷Be (pp-II стартовый) сюда не входит — он живёт в StarAlphaGammaReaction
        // через alphaGammaResult на ³He²⁺.
        // Для ⁷Be⁴⁺ возможны две ветки: + e⁻ → ⁷Li³⁺ (pp-II, доминирует) либо + p → ⁸B⁵⁺ (pp-III, редкая).
        val candidates: List<Triple<Element, Element, List<Element>>> = when (firstAtomElement) {
            Proton      -> listOf(Triple(Proton,    DEUTERIUM, emptyList()))
            DEUTERIUM   -> listOf(Triple(Proton,    HELIUM_3,  emptyList()))
            HELIUM_3    -> listOf(Triple(HELIUM_3,  HELIUM_4,  listOf(Proton, Proton)))
            BERYLLIUM_7 -> listOf(
                Triple(ELECTRON, LITHIUM_7, emptyList()),
                Triple(Proton,   BORON_8,   emptyList()),
            )
            LITHIUM_7   -> listOf(Triple(Proton,    HELIUM_4,  listOf(HELIUM_4)))
            else -> return false
        }

        // Перебираем кандидатов в порядке приоритета — первый найденный поблизости побеждает.
        for ((secondElement, result, extras) in candidates) {
            val (secondAtom, distanceSquare) = reagents
                .drop(1)
                .filter { it.state().value.element == secondElement }
                .filter { it.state().value.alive }
                .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
                .minByOrNull { it.second }
                ?: continue

            if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) continue

            if (distanceSquare < firstAtomElement.details.radius * secondElement.details.radius * 2f) {
                atom1 = firstAtom
                atom2 = secondAtom
                resultElement = result
                extraElements = extras
                return true
            }
        }
        return false
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val result = resultElement!!
        val extras = extraElements
        // Перенос оболочки на продукт (2C2): наследует электроны первого реагента-ядра, не больше Z
        // продукта. В звезде ядра голые → 0. PP многоканальна (синтез + захват e⁻ в ядро), поэтому без
        // обобщённого shake-off; кламп лишь страхует от аниона в краевых случаях.
        val resultElectrons = minOf(a1.state().value.electrons, result.details.p)

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
                electrons = resultElectrons,
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
                    electrons = 0,
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
