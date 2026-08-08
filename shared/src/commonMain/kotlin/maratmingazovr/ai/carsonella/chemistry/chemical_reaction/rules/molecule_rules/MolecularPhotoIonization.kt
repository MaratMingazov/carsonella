package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate

/**
 * Молекулярная фотоионизация: фотон достаточной энергии выбивает из молекулы электрон — молекула
 * ВЫЖИВАЕТ как катион (тот же граф, `electrons − 1`), избыток `E − IP` уносит вылетевший электрон.
 * Прямое зеркало атомной [maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.PhotoIonization],
 * только субъект — молекула.
 *
 * Порог = последний уровень [MoleculeGraph.energyLevels] (минимум атомного IP по графу, кэш на графе). В отличие от
 * атома, молекулярный ион НЕ требует нового графа: заряд живёт в [EntityState.electrons] как счётчик,
 * а граф не меняется — поэтому здесь `updateState` (electrons−1), как и у атома.
 *
 * Рамки этого шага (детерминированно; вероятностное ветвление — отдельным правилом):
 *  - weight = 0, поэтому при `E ≥ IP` ионизация уверенно бьёт распад ([PhotoDissociation], weight < 0)
 *    в resolve(). Так «можешь ионизоваться — ионизуйся; иначе (`D ≤ E < IP`) — распадись».
 *  - Только ОДНОКРАТНАЯ ионизация (катион +1). Диссоциативная ионизация (ионизация + разрыв) и
 *    вероятностный branch распад/ионизация — позже (образец одного-ролла: StarProtonCaptureReaction).
 *  - Избыток `E − IP` целиком уносит электрон (энергия молекулы → 0), как у атомной фотоионизации.
 */
class MolecularPhotoIonization(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "MolecularPhotoIonization"

    private data class Match(val molecule: Molecule, val photon: Entity) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // ионизовать нечем: фотон приходит соседом

        val subjectState = molecule.state().value
        val threshold = molecule.energyLevels.lastOrNull() ?: return null // есть ли у молекулы ионизируемый атом?

        val subjectPosition = subjectState.kinematics.position
        val radius = molecule.radius
        val activationDistanceSquare = radius * radius

        val nearestPhoton = neighbors
            .asSequence()
            .filter { it is SubAtom && it.element == Element.PHOTON }
            .filter { it.state().value.energy > 0f && it.state().value.alive }
            .filter { it.getEnvironment() === molecule.getEnvironment() }   // оба в одной среде
            .map { it to subjectPosition.distanceSquareTo(it.state().value.kinematics.position) }
            .filter { it.second <= activationDistanceSquare }
            .minByOrNull { it.second }
            ?.first
            ?: return null

        val available = subjectState.energy + nearestPhoton.state().value.energy
        if (available < threshold) return null   // фотона не хватает на ионизацию → мимо (может сработать распад)

        return Match(molecule, nearestPhoton)
    }

    // Детерминированный шаг: ионизация бьёт распад. weight = 0 > weight распада (−dissociationEnergy),
    // поэтому при E ≥ IP resolve() выбирает ионизацию; при D ≤ E < IP (порог IP не достигнут — matches
    // вернул false) в игре остаётся только распад. Вероятностный branch заменит это позже.
    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, photon) = match as Match
        val threshold = molecule.energyLevels.last()           // matches гарантирует что лестница непуста
        val electrons = molecule.state().value.electrons

        // Избыток над порогом ионизации уносит вылетевший электрон (энергия молекулы → 0).
        val available = molecule.state().value.energy + photon.state().value.energy
        val freeEnergy = (available - threshold).coerceAtLeast(0f)

        val molPosition = molecule.state().value.kinematics.position
        val molDirection = molecule.state().value.kinematics.direction
        val env = molecule.getEnvironment()
        val radius = molecule.radius
        val electronPosition = molPosition.plus(Position(1f * radius, 0f))
        val electronVelocity = 10 + 0.2f * freeEnergy

        // Граф НЕ меняется — та же молекула теряет электрон: updateState(electrons−1, energy=0), вылетает e⁻.
        return ReactionOutcome(
            consumed = listOf(photon),
            updateState = listOf(StateUpdate(molecule) {
                molecule.setElectrons(electrons - 1)
                molecule.setEnergy(0f)
            }),
            spawn = listOf {
                entityGenerator.createEntity(ELECTRON, electronPosition, molDirection, electronVelocity, 0f, env, electrons = 1)
            },
        )
    }
}