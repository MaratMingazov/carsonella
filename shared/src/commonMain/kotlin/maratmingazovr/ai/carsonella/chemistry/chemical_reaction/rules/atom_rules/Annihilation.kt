package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.Vec2D
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.POSITRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
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

    private var positron: Entity? = null
    private var electron: Entity? = null

    override fun matchesAtoms(reagents: List<Entity>): Boolean {
        positron = null
        electron = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val species = first.state().value.species
        if (species !is Species.Elemental) return false
        if (species.element != POSITRON) return false

        val positronPosition = first.state().value.position
        val positronRadius = POSITRON.details.radius

        val (nearestElectron, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == ELECTRON
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(positronPosition) }
            .minByOrNull { it.second }
            ?: return false

        val electronRadius = ELECTRON.details.radius
        return if (distanceSquare < positronRadius * electronRadius * 2f) {
            positron = first
            electron = nearestElectron
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val positronEntity = positron!!
        val electronEntity = electron!!

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(positronEntity, electronEntity)
        // В системе покоя пары фотоны разлетаются строго в противоположные стороны.
        // Если суммарный импульс пары нулевой — берём случайную ось.
        val photonDirection = if (velocity > 1e-6f) direction else randomDirection(entityGenerator.random)
        val oppositeDirection = Vec2D(-photonDirection.x, -photonDirection.y)

        val positronPosition = positronEntity.state().value.position
        val electronPosition = electronEntity.state().value.position
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
                        10f,
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
                        10f,
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