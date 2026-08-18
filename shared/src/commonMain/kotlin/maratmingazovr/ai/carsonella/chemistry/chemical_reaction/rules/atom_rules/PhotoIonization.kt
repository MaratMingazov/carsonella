package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate
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
        val atom: Atom,
        val photon: SubAtom,
        val atomElement: Element,     // элементы реагентов, выясненные в matchesAtom — produce не вычисляет заново
        val photonElement: Element,
        val level: Float?,
    ) : MatchedData

    override fun matchesAtom(atom: Atom, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null
        val atomElement = atom.element
        val levels = atomElement.energyLevels(atom.electrons)
        if (levels.isEmpty()) return null
        val others = neighbors
        val activationDistanceSquare = atomElement.details.radius * atomElement.details.radius

        val (nearestPhoton, distance) = others
            .asSequence()
            .filterIsInstance<SubAtom>().filter { it.element == PHOTON }
            .filter { it.energy > 0 }
            .filter { it.alive }
            .map { it to atom.kinematics.position.distanceSquareTo(it.kinematics.position) }
            .minByOrNull { it.second }
            ?: return null

        if (distance > activationDistanceSquare) return null
        val expectedEnergy = atom.energy + nearestPhoton.energy

        // Ионизация: энергии хватает достать электрон (с допуском по верхнему уровню)
        if (expectedEnergy >= levels.last() - ENERGY_EPSILON) {
            return Match(atom, nearestPhoton, atomElement, PHOTON, level = null)
        }

        // Поглощение: энергия попадает в окрестность одного из уровней
        val matched = levels.firstOrNull { abs(it - expectedEnergy) < ENERGY_EPSILON } ?: return null
        return Match(atom, nearestPhoton, atomElement, PHOTON, level = matched)
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        /**
         *  Ионизация элемента
         *  Если в элемент прилетел фотон, то электрон заберет эту энергию.
         *  Если пройдем порог [ЭнергияИонизации], то электрон улетит из этого элемента
         */
        val (atom, photon, entityElement, photonElement, level) = match as Match
        val entityEnergy = atom.energy
        val electrons = atom.electrons
        val photonEnergy = photon.energy

        if (level != null) {
            // Поглощение: «снапаем» энергию атома на точный уровень из таблицы, присваивая его целиком.
            // Прибавление разности (level - entityEnergy) дало бы a + (b - a), что в float не гарантирует b бит-в-бит.
            return ReactionOutcome(
                consumed = listOf(photon),
                updateState = listOf(StateUpdate(atom) { atom.energy = level }),)
        } else {
            val energyIonization = entityElement.energyLevels(electrons).last()
            // пройден энергетический порог. Электрон накопил достаточно энергии, чтобы улететь
            val freeEnergy = entityEnergy + photonEnergy - energyIonization
            val entityPosition = atom.kinematics.position
            val entityDirection = atom.kinematics.direction
            val entityVelocity = atom.kinematics.velocity
            val entityRadius = entityElement.details.radius
            val electronDirection = randomDirection(entityGenerator.random)
            val electronVelocity = (10 + 0.2f * freeEnergy).coerceAtMost(MAX_VELOCITY)
            val electronOffset = entityRadius + ELECTRON.details.radius
            val electronPosition = entityPosition.addVelocity(electronDirection * electronOffset)
            val env = atom.getEnvironment()

            // Element НЕ меняется — тот же атом теряет электрон: updateState(electrons−1, energy=0), вылетает e⁻.
            // Водород здесь ничем не особен: H с одним электроном становится H с нулём, то есть протоном.
            return ReactionOutcome(
                consumed = listOf(photon),
                updateState = listOf(StateUpdate(atom) {
                    atom.electrons = electrons - 1
                    atom.energy = 0f
                }),
                spawn = listOf {
                    entityGenerator.createAtom(
                        ELECTRON,
                        electronPosition,
                        electronDirection,
                        electronVelocity,
                        0f,
                        env,
                        electrons = 1
                    )
                },)
        }
    }
}