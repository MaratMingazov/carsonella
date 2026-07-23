package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.radius

/**
 * Молекулярная фотоионизация: фотон достаточной энергии выбивает из молекулы электрон — молекула
 * ВЫЖИВАЕТ как катион (тот же граф, `electrons − 1`), избыток `E − IP` уносит вылетевший электрон.
 * Прямое зеркало атомной [maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules.PhotoIonization],
 * только субъект — молекула.
 *
 * Порог = [MoleculeGraph.ionizationEnergy] (минимум атомного IP по графу, кэш на графе). В отличие от
 * атома, молекулярный ион НЕ требует смены Species: заряд живёт в [EntityState.electrons] как счётчик,
 * а граф не меняется — поэтому здесь `updateState` (electrons−1), а не consume+spawn (как у H → Proton).
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

    private var molecule: Entity? = null
    private var photon: Entity? = null

    override fun matchesMolecule(reagents: List<Entity>): Boolean {
        molecule = null
        photon = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        val graph = (first.state().value.species as Species.Molecular).graph
        val threshold = graph.ionizationEnergy ?: return false // есть ли у молекулы ионизируемый атом?

        val firstPosition = first.state().value.position
        val radius = first.state().value.species.radius()
        val activationDistanceSquare = radius * radius

        val nearestPhoton = reagents.drop(1)
            .asSequence()
            .filter { val sp = it.state().value.species; sp is Species.Elemental && sp.element == Element.PHOTON }
            .filter { it.state().value.energy > 0f && it.state().value.alive }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to firstPosition.distanceSquareTo(it.state().value.position) }
            .filter { it.second <= activationDistanceSquare }
            .minByOrNull { it.second }
            ?.first
            ?: return false

        val available = first.state().value.energy + nearestPhoton.state().value.energy
        if (available < threshold) return false   // фотона не хватает на ионизацию → мимо (может сработать распад)

        molecule = first
        photon = nearestPhoton
        return true
    }

    // Детерминированный шаг: ионизация бьёт распад. weight = 0 > weight распада (−dissociationEnergy),
    // поэтому при E ≥ IP resolve() выбирает ионизацию; при D ≤ E < IP (порог IP не достигнут — matches
    // вернул false) в игре остаётся только распад. Вероятностный branch заменит это позже.
    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val ph = photon!!
        val graph = (mol.state().value.species as Species.Molecular).graph
        val threshold = graph.ionizationEnergy!!            // matches гарантирует что не null
        val electrons = mol.state().value.electrons

        // Избыток над порогом ионизации уносит вылетевший электрон (энергия молекулы → 0).
        val available = mol.state().value.energy + ph.state().value.energy
        val freeEnergy = (available - threshold).coerceAtLeast(0f)

        val molPosition = mol.state().value.position
        val molDirection = mol.state().value.direction
        val env = mol.getEnvironment()
        val radius = mol.state().value.species.radius()
        val electronPosition = molPosition.plus(Position(1f * radius, 0f))
        val electronVelocity = 10 + 0.2f * freeEnergy

        // Species НЕ меняется — тот же граф теряет электрон: updateState(electrons−1, energy=0), вылетает e⁻.
        return ReactionOutcome(
            consumed = listOf(ph),
            updateState = listOf {
                mol.setElectrons(electrons - 1)
                mol.setEnergy(0f)
            },
            spawn = listOf {
                entityGenerator.createEntity(ELECTRON, electronPosition, molDirection, electronVelocity, 0f, env, electrons = 1)
            },
            description = "$id: ${graph.formulaPretty} + γ[${ph.state().value.energy}eV] -> ${graph.formulaPretty}⁺ + ${ELECTRON.details.label}",
        )
    }
}