package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.POSITRON
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Аннигиляция электрон-позитронной пары: e⁻ + e⁺ → 2γ.
 *
 * Закон сохранения импульса требует двух фотонов, разлетающихся в противоположных
 * направлениях (в системе покоя пары — строго на 180°, ~511 кэВ каждый). Реальную
 * энергию 511 кэВ сжимаем до 511 eV — порядок соразмерен другим фотонам в проекте
 * (Lyman α = 13.6 eV, ионизации тяжёлых ядер — сотни eV). Этого хватает на
 * фотоионизацию любого лёгкого атома, что физически правдоподобно — γ-фотон от
 * аннигиляции в реальности очень жёсткий.
 *
 * Работает в любой среде (не только в звезде): позитроны рождаются в β⁺-распадах
 * по всей вселенной и должны находить свою пару везде, где есть электроны. Без
 * этого правила позитроны от BetaPlusDecay копились бы вечно.
 *
 * Первым реагентом ожидается POSITRON — позитрон шлёт reaction request в SubAtom.step(),
 * электрон сам реакцию не запрашивает.
 */
class Annihilation(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "Annihilation"

    private data class Match(val positron: Entity, val electron: Entity) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null

        val first = reagents.first() as? SubAtom ?: return null
        if (!first.state().value.alive) return null
        if (first.element != POSITRON) return null

        val positronPosition = first.state().value.centerPosition
        val positronRadius = POSITRON.details.radius

        val (nearestElectron, distanceSquare) = reagents
            .drop(1)
            .filter {
                it is SubAtom && it.element == ELECTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.centerPosition.distanceSquareTo(positronPosition) }
            .minByOrNull { it.second }
            ?: return null

        val electronRadius = ELECTRON.details.radius
        return if (distanceSquare < positronRadius * electronRadius * 2f) {
            Match(first, nearestElectron)
        } else {
            null
        }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (positronEntity, electronEntity) = match as Match

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(positronEntity, electronEntity)
        // В системе покоя пары фотоны разлетаются строго в противоположные стороны.
        // Если суммарный импульс пары нулевой — берём случайную ось.
        val photonDirection = if (velocity > 1e-6f) direction else randomDirection(entityGenerator.random)
        val oppositeDirection = Vec2D(-photonDirection.x, -photonDirection.y)

        val positronPosition = positronEntity.state().value.centerPosition
        val electronPosition = electronEntity.state().value.centerPosition
        val centerPosition = Position(
            (positronPosition.x + electronPosition.x) / 2f,
            (positronPosition.y + electronPosition.y) / 2f,
        )
        val photonRadius = Element.PHOTON.details.radius
        val photonEnergy = 511f

        return ReactionOutcome(
            consumed = listOf(positronEntity, electronEntity),
            spawn = listOf(
                {
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(
                            centerPosition.x + photonDirection.x * photonRadius,
                            centerPosition.y + photonDirection.y * photonRadius,
                        ),
                        photonDirection,
                        MAX_VELOCITY,
                        energy = photonEnergy,
                        environment = positronEntity.getEnvironment(),
                        electrons = 0,
                    )
                },
                {
                    entityGenerator.createEntity(
                        Element.PHOTON,
                        Position(
                            centerPosition.x + oppositeDirection.x * photonRadius,
                            centerPosition.y + oppositeDirection.y * photonRadius,
                        ),
                        oppositeDirection,
                        MAX_VELOCITY,
                        energy = photonEnergy,
                        environment = positronEntity.getEnvironment(),
                        electrons = 0,
                    )
                },
            ),
            description = "$id: ${POSITRON.details.symbol} + ${ELECTRON.details.symbol} → 2 ${Element.PHOTON.details.symbol} [$photonEnergy ev]",
        )
    }
}