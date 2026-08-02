package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HYDROGEN
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection
import kotlin.math.abs

// Допуск при сопоставлении энергии фотона с уровнем атома. Нужен потому, что фотон, рождённый
// в SpontaneousEmission как разность двух уровней (E_high - E_low), может из-за float-округления
// не совпасть бит-в-бит с уровнем атома-мишени, хотя физически это resonant scattering.
// 0.01 eV в ~150 раз меньше минимального промежутка между уровнями (~1.5 eV у He, Li) — коллизий нет.
private const val ENERGY_EPSILON = 0.01f

/**
 * Фотоионизация — это процесс, при котором атом или молекула теряет электрон под воздействием фотона, становясь ионом
 * Ионизация под действием света. Или фотоэффект.
 * Если элемент наберет достаточно энергии (energyIonization), то электрон может вылететь с орбиты
 */
class PhotoIonization (
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "PhotoIonization"

    /**
     * [level] — точный уровень из таблицы, на который «снапаем» атом (поглощение);
     * `null` означает ИОНИЗАЦИЮ: энергии хватило дотянуть до верхнего уровня и оторвать электрон.
     */
    private data class Match(
        val atom: Entity,
        val photon: Entity,
        val atomElement: Element,     // элементы реагентов, выясненные в matchesAtoms — produce не вычисляет заново
        val photonElement: Element,
        val level: Float?,
    ) : MatchedData

    override fun matchesAtoms(reagents: List<Entity>): MatchedData? {
        if (reagents.size < 2) return null

        val first = reagents.first() as? Atom ?: return null
        val firstElement = first.element
        val levels = firstElement.energyLevels(first.state().value.electrons)
        if (levels.isEmpty()) return null
        if (!first.state().value.alive) return null
        val others = reagents.drop(1)
        val activationDistanceSquare = firstElement.details.radius * firstElement.details.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filter {
                it is SubAtom && it.element == PHOTON
            }
            .filter { it.state().value.energy > 0 }
            .filter { it.state().value.alive }
            .map { it to first.state().value.centerPosition.distanceSquareTo(it.state().value.centerPosition) }
            .minByOrNull { it.second }
            ?: return null

        if (distance > activationDistanceSquare) return null
        val expectedEnergy = first.state().value.energy + nearestPhoton.state().value.energy

        // Ионизация: энергии хватает достать электрон (с допуском по верхнему уровню)
        if (expectedEnergy >= levels.last() - ENERGY_EPSILON) {
            return Match(first, nearestPhoton, firstElement, PHOTON, level = null)
        }

        // Поглощение: энергия попадает в окрестность одного из уровней
        val matched = levels.firstOrNull { abs(it - expectedEnergy) < ENERGY_EPSILON } ?: return null
        return Match(first, nearestPhoton, firstElement, PHOTON, level = matched)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        /**
         *  Ионизация элемента
         *  Если в элемент прилетел фотон, то электрон заберет эту энергию.
         *  Если пройдем порог [ЭнергияИонизации], то электрон улетит из этого элемента
         */
        val (atom, photon, entityElement, photonElement, level) = match as Match
        val entityEnergy = atom.state().value.energy
        val electrons = atom.state().value.electrons
        val photonEnergy = photon.state().value.energy

        if (level != null) {
            // Поглощение: «снапаем» энергию атома на точный уровень из таблицы через setEnergy.
            // addEnergy(level - entityEnergy) дал бы a + (b - a), что в float не гарантирует b бит-в-бит.
            return ReactionOutcome(
                consumed = listOf(photon),
                updateState = listOf { atom.setEnergy(level) },
                description = "$id: ${entityElement.label(electrons)} (${entityEnergy}eV) + ${photonElement.details.label} (${photonEnergy}eV) -> ${
                    entityElement.label(
                        electrons
                    )
                } (${level}eV)"
            )
        } else {
            val energyIonization = entityElement.energyLevels(electrons).last()
            // пройден энергетический порог. Электрон накопил достаточно энергии, чтобы улететь
            val freeEnergy = entityEnergy + photonEnergy - energyIonization
            val entityPosition = atom.state().value.centerPosition
            val entityDirection = atom.state().value.direction
            val entityVelocity = atom.state().value.velocity
            val entityRadius = entityElement.details.radius
            val electronDirection = randomDirection(entityGenerator.random)
            val electronVelocity = (10 + 0.2f * freeEnergy).coerceAtMost(MAX_VELOCITY)
            val electronOffset = entityRadius + ELECTRON.details.radius
            val electronPosition = entityPosition.addVelocity(electronDirection * electronOffset)
            val env = atom.getEnvironment()

            // Протий — особый случай: ион водорода это частица Proton (SubAtom), а не «H с 0 электронов».
            // Сменить Element/класс через updateState нельзя (element неизменяем), поэтому здесь consume + spawn.
            if (entityElement == HYDROGEN) {
                val ionPosition = entityPosition.plus(Position(-1f * entityRadius, 0f))
                return ReactionOutcome(
                    consumed = listOf(photon, atom),
                    spawn = listOf {
                        entityGenerator.createEntity(
                            Proton,
                            ionPosition,
                            entityDirection,
                            entityVelocity,
                            0f,
                            env,
                            electrons = 0
                        )
                        entityGenerator.createEntity(
                            ELECTRON,
                            electronPosition,
                            electronDirection,
                            electronVelocity,
                            0f,
                            env,
                            electrons = 1
                        )
                    },
                    description = "$id: ${entityElement.label(electrons)} + ${photonElement.details.label} -> ${Proton.details.label} + ${ELECTRON.details.label}"
                )
            }

            // Element НЕ меняется — тот же атом теряет электрон: updateState(electrons−1, energy=0), вылетает e⁻.
            return ReactionOutcome(
                consumed = listOf(photon),
                updateState = listOf {
                    atom.setElectrons(electrons - 1)
                    atom.setEnergy(0f)
                },
                spawn = listOf {
                    entityGenerator.createEntity(
                        ELECTRON,
                        electronPosition,
                        electronDirection,
                        electronVelocity,
                        0f,
                        env,
                        electrons = 1
                    )
                },
                description = "$id: ${entityElement.label(electrons)} + ${photonElement.details.label} -> ${
                    entityElement.label(
                        electrons - 1
                    )
                } + ${ELECTRON.details.label}"
            )
        }
    }
}