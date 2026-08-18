package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.randomDirection
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.StateUpdate


// Молекулярная фотоионизация: фотон достаточной энергии выбивает из молекулы электрон — молекула ВЫЖИВАЕТ как катион
class MolecularPhotoIonization(private val entityGenerator: IEntityGenerator) : MoleculeReactionRule() {
    override val id = "MolecularPhotoIonization"

    private data class Match(
        val molecule: Molecule,
        val photon: SubAtom,
        val atom: MoleculeAtom,   // Атом, поглотивший фотон: от него и улетит электрон.
    ) : MatchedData

    /**
     * Попадание ЛОКАЛЬНОЕ, ионизация МОЛЕКУЛЯРНАЯ. Фотон поглощает конкретный атом — своей позиции у
     * молекулы нет, а прежний диск вокруг центра масс накрывал пустоту между атомами. Но электрон уходит
     * у молекулы ЦЕЛИКОМ: порог берётся из её лестницы (минимальный IP по атомам, кеш графа), а не из
     * лестницы попавшегося атома. Этим правило и отличается от [PhotoDissociation], где связь локальна.
     */
    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // ионизовать нечем: фотон приходит соседом

        val threshold = molecule.energyLevels.lastOrNull() ?: return null // есть ли у молекулы ионизируемый атом?

        val photons = neighbors
            .filterIsInstance<SubAtom>()
            .filter { it.element == Element.PHOTON && it.energy > 0f && it.alive }
            .filter { it.getEnvironment() === molecule.getEnvironment() }        // оба в одной среде
            .filter { molecule.energy + it.energy >= threshold }                 // не хватает на ионизацию → мимо (может сработать распад)
        if (photons.isEmpty()) return null

        return molecule.atoms
            .flatMap { atom -> photons.map { photon -> Triple(atom, photon, atom.kinematics.position.distanceSquareTo(photon.kinematics.position)) } }
            .filter { (atom, _, distanceSquare) -> distanceSquare <= atom.radius * atom.radius }   // фотон попал в атом
            .minByOrNull { (_, _, distanceSquare) -> distanceSquare }
            ?.let { (atom, photon, _) -> Match(molecule, photon, atom) }
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, photon, atom) = match as Match
        val threshold = molecule.energyLevels.last()           // matches гарантирует что лестница непуста
        val electrons = molecule.electrons

        // Избыток над порогом ионизации уносит вылетевший электрон (энергия молекулы → 0).
        val available = molecule.energy + photon.energy
        val freeEnergy = (available - threshold).coerceAtLeast(0f)

        val env = molecule.getEnvironment()
        // Электрон принадлежал молекуле целиком, но вылетает от того атома, который поглотил фотон:
        // случайное направление, старт за его радиусом — как в атомной PhotoIonization.
        val electronDirection = randomDirection(entityGenerator.random)
        val electronOffset = atom.radius + ELECTRON.details.radius
        val electronPosition = atom.kinematics.position.addVelocity(electronDirection * electronOffset)
        val electronVelocity = 10 + 0.2f * freeEnergy

        // Граф НЕ меняется — та же молекула теряет электрон: updateState(electrons−1, energy=0), вылетает e⁻.
        return ReactionOutcome(
            consumed = listOf(photon),
            updateState = listOf(StateUpdate(molecule) {
                molecule.electrons = electrons - 1
                molecule.energy = 0f
            }),
            spawn = listOf {
                entityGenerator.createAtom(ELECTRON, electronPosition, electronDirection, electronVelocity, 0f, env, electrons = 1)
            },
        )
    }
}