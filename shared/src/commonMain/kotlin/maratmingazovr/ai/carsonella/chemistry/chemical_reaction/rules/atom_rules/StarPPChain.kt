package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

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
import maratmingazovr.ai.carsonella.chemistry.elementOrNull
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome

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
) : AtomReactionRule() {

    override val id = "StarPPChain"

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
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.centerPosition
        if (!firstAtom.state().value.alive) return null
        // Субъект цепочки — и протон (p+p→D), и атом (D+p→³He), поэтому элемент через elementOrNull.
        val firstAtomElement = firstAtom.elementOrNull() ?: return null
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return null

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
            else -> return null
        }

        // Перебираем кандидатов в порядке приоритета — первый найденный поблизости побеждает.
        for ((secondElement, result, extras) in candidates) {
            val (secondAtom, distanceSquare) = reagents
                .drop(1)
                .filter {
                    it.elementOrNull() == secondElement
                }
                .filter { it.state().value.alive }
                .map { it to it.state().value.centerPosition.distanceSquareTo(firstAtomPosition) }
                .minByOrNull { it.second }
                ?: continue

            if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) continue

            if (distanceSquare < firstAtomElement.details.radius * secondElement.details.radius * 2f) {
                return Match(firstAtom, secondAtom, firstAtomElement, secondElement, result, extras)
            }
        }
        return null
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (atom1, atom2, atom1Element, atom2Element, result, extras) = match as Match
        // Перенос оболочки на продукт (2C2): наследует электроны первого реагента-ядра, не больше Z
        // продукта. В звезде ядра голые → 0. PP многоканальна (синтез + захват e⁻ в ядро), поэтому без
        // обобщённого shake-off; кламп лишь страхует от аниона в краевых случаях.
        val resultElectrons = minOf(atom1.state().value.electrons, result.details.p)

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(atom1, atom2)
        val resultPosition = atom1.state().value.centerPosition
        val resultRadius = result.details.radius
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
                    environment = atom1.getEnvironment(),
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
