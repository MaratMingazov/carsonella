package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Рост молекулы: молекула со свободным валентным слотом притягивает ближайшего соседа,
 * у которого тоже есть свободный слот, и сливается с ним в одну бо́льшую молекулу.
 */
class MoleculeGrowth(
    private val entityGenerator: IEntityGenerator,
) : MoleculeReactionRule() {
    override val id = "MoleculeGrowth"

    private data class Match(val molecule: Molecule, val partner: Entity) : MatchedData

    override fun matchesMolecule(subject: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // расти не с кем

        if (!subject.state().value.alive) return null
        // нужен свободный слот, чтобы было куда расти
        if (!subject.graph.hasFreeValence) return null
        // Внутри звезды слишком горячо — молекулы не растут (как и не образуются).
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null

        val subjectPosition = subject.state().value.kinematics.centerPosition
        val subjectRadius = subject.radius

        val (second, distanceSquare) = neighbors
            .filter { canBond(it) }
            .filter { it.getEnvironment() === subject.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.kinematics.centerPosition.distanceSquareTo(subjectPosition) }
            .minByOrNull { it.second }
            ?: return null

        val secondRadius = second.radius
        return if (distanceSquare < subjectRadius * secondRadius * 2f) {
            Match(subject, second)
        } else {
            null
        }
    }

    // Партнёр способен дать молекуле новую связь: живой, со свободным слотом.
    //  - молекула: hasFreeSlot();
    //  - атом: нейтральный лёгкий атом с valence > 0 (как в CovalentBondFormation).
    private fun canBond(entity: Entity): Boolean {
        val state = entity.state().value
        if (!state.alive) return false
        // Проверка класса заменяет прежний тег ElementType: частица и звезда — не Atom, связей не образуют.
        return when (entity) {
            is Molecule -> entity.graph.hasFreeValence
            is Atom -> state.electrons == entity.element.details.p &&   // нейтральный — есть электроны для общей пары
                entity.element.valence(state.electrons) > 0
            is SubAtom, is Star -> false
        }
    }

    // Партнёр как граф: молекула → её граф; атом → одноузловой граф (атом = вырожденная молекула, §8).
    // Зовётся только после canBond, поэтому частица и звезда сюда не доходят.
    private fun graphOf(entity: Entity): MoleculeGraph = when (entity) {
        is Molecule -> entity.graph
        is Atom -> MoleculeGraph(listOf(AtomNode(0, entity.element)), emptyList())
        is SubAtom, is Star -> error("graphOf: ${entity::class.simpleName} не может расти — canBond должен был отсеять")
    }

    // Энергия связи, которую даст рост (новая связь order=1) — экзотермично, «+» (контракт weight = энергия
    // реакции со знаком). Так рост честно конкурирует с усилением: у углерода рост выгоднее (C–H 4.28),
    // у кислорода — усиление (O=O выигрыш 3.65 > рост O–O 1.51). Данные из Match.
    override fun weight(match: MatchedData): Float {
        val (molecule, partnerEntity) = match as Match
        val partnerEntityIsotop = getIsotope(partnerEntity)
        val moleculeAtomIsotope = molecule.firstFreeValenceAtom()!!.isotope
        return BondEnergy.of(moleculeAtomIsotope, partnerEntityIsotop, order = 1) ?: 0f
    }

    private fun getIsotope(entity: Entity): Element = when (entity) {
        is Molecule -> entity.firstFreeValenceAtom()!!.isotope
        is Atom -> entity.element
        is SubAtom, is Star -> error("graphOf: ${entity::class.simpleName} не может расти — canBond должен был отсеять")
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, partnerEntity) = match as Match
        val molGraph = molecule.graph
        val partnerGraph = graphOf(partnerEntity)

        // matchesMolecule гарантировал свободные слоты у обоих → firstFreeSlotNode не null.
        val molNode = molGraph.firstFreeValenceAtomNode!!
        val partnerNode = partnerGraph.firstFreeValenceAtomNode!!
        val newMoleculeGraph = molecule.merge(partnerGraph, thisNode = molNode.localId, otherNode = partnerNode.localId, bondOrder = 1)


        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(molecule, partnerEntity)
        val p1 = molecule.state().value.kinematics.centerPosition
        val p2 = partnerEntity.state().value.kinematics.centerPosition
        val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        // Сохранение электронов (§8): электроны новой молекулы = сумма электронов реагентов.
        val electrons = molecule.state().value.electrons + partnerEntity.state().value.electrons
        val energy = molecule.state().value.energy + partnerEntity.state().value.energy
        val env = molecule.getEnvironment()

        // Образование связи ЭКЗОТЕРМИЧНО: энергию новой связи высвобождаем фотоном (как в CovalentBondFormation).
        val molIso = molNode.isotope
        val partnerIso = partnerNode.isotope
        val bondEnergy = BondEnergy.of(molIso, partnerIso, order = 1)
        val spawn = mutableListOf(
            { entityGenerator.createMolecule(newMoleculeGraph, midpoint, direction, velocity, energy, env, electrons) },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            val photonDirection = randomDirection(entityGenerator.random)
            val photonVelocity = MAX_VELOCITY
            val offset = molecule.radius + Element.PHOTON.details.radius // нужно выйти за радиус молекулы
            val photonPosition = midpoint.addVelocity(photonDirection * offset)
            spawn += {
                // Фотон уносит энергию связи и УЛЕТАЕТ
                entityGenerator.createEntity(Element.PHOTON, photonPosition, photonDirection,
                    photonVelocity, energy = bondEnergy, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(molecule, partnerEntity),
            spawn = spawn,
            description = "$id: ${molGraph.formulaPretty} + ${partnerGraph.formulaPretty} -> ${newMoleculeGraph.formulaPretty}" +
                (bondEnergy?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}