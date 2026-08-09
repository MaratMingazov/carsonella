package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Atom
import maratmingazovr.ai.carsonella.chemistry.SubAtom
import maratmingazovr.ai.carsonella.chemistry.Star
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Molecule
import maratmingazovr.ai.carsonella.chemistry.MoleculeAtom
import maratmingazovr.ai.carsonella.chemistry.MAX_VELOCITY
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.MatchedData
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
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

    private data class Match(
        val molecule: Molecule,
        val partner: Entity,
        val moleculeAtom: MoleculeAtom,
        val partnerAtom: MoleculeAtom?,
        val partnerIsotope: Element,
    ) : MatchedData

    override fun matchesMolecule(molecule: Molecule, neighbors: List<Entity>): MatchedData? {
        if (neighbors.isEmpty()) return null   // расти не с кем

        if (!molecule.hasFreeValence) return null // у молекулы нет свободных слотов для роста
        if (molecule.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null // Внутри звезды слишком горячо — молекулы не растут (как и не образуются).

        val freeValenceAtoms = molecule.freeValenceAtoms // вычисляем один раз тут, а не каждый раз внутри метода
        return neighbors
            .filter { it.alive }
            .filter { it.getEnvironment() === molecule.getEnvironment() }   // оба в одной среде
            .flatMap { partner -> candidates(molecule, freeValenceAtoms, partner) }
            .minByOrNull { (_, distanceSquare) -> distanceSquare }
            ?.first
    }


    // у нашей молекулы внутри атомы. Мы перебираем каждый атом и возвращаем те атомы которые могут образовать связь с другим атомом и расстояние
    private fun candidates(molecule: Molecule, freeValenceAtoms: List<MoleculeAtom>, partner: Entity): List<Pair<Match, Float>> =
        when (partner) {
            is Atom ->
                if (!bondable(partner)) emptyList() // у атома нет валентности, связи не будет
                else freeValenceAtoms.mapNotNull { freeValenceAtom ->
                    reachable(freeValenceAtom, partner.kinematics.position, partner.radius)?.let { distanceSquare -> // достаточно ли два атома близки для образовани связи
                        Match(molecule, partner, freeValenceAtom, partnerAtom = null, partnerIsotope = partner.element) to distanceSquare
                    }
            }
            is Molecule -> {
                val partnerFreeValenceAtoms = partner.freeValenceAtoms
                freeValenceAtoms.flatMap { freeValenceAtom ->
                    partnerFreeValenceAtoms.mapNotNull { partnerFreeValenceAtom ->
                        reachable(freeValenceAtom, partnerFreeValenceAtom.kinematics.position, partnerFreeValenceAtom.radius)?.let { distanceSquare -> // достаточно ли два атома близки для образовани связи
                            Match(molecule, partner, freeValenceAtom, partnerFreeValenceAtom, partnerFreeValenceAtom.isotope) to distanceSquare
                        }
                    }
                }
            }
            is SubAtom, is Star -> emptyList()   // связей не образуют; ветка ради исчерпывающего when
        }
    private fun reachable(moleculeAtom: MoleculeAtom, partnerPosition: Position, partnerRadius: Float): Float? {
        val distanceSquare = moleculeAtom.kinematics.position.distanceSquareTo(partnerPosition)
        return if (distanceSquare < moleculeAtom.radius * partnerRadius * 2f) distanceSquare else null
    } // достаточно ли два атома близки для образования связи
    private fun bondable(atom: Atom): Boolean =
        atom.electrons == atom.element.details.p && atom.element.valence(atom.electrons) > 0 // Атом способен дать молекуле новую связь

    override fun weight(match: MatchedData): Float {
        val data = match as Match
        return BondEnergy.of(data.moleculeAtom.isotope, data.partnerIsotope, order = 1) ?: 0f
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val data = match as Match
        val molecule = data.molecule
        val partnerEntity = data.partner
        val molAtom = data.moleculeAtom
        val env = molecule.getEnvironment()

        // Образование связи ЭКЗОТЕРМИЧНО: энергию новой связи высвобождаем фотоном (как в CovalentBondFormation).
        val bondEnergy = BondEnergy.of(molAtom.isotope, data.partnerIsotope, order = 1)

        // Само слияние — дело конструкторов Molecule: граф, кинематику, энергию и электроны (сохранение, §8)
        // считают они. Правилу остаётся выбрать перегрузку по типу партнёра: молекула или атом.
        // Развилка по САЙТУ, а не по классу партнёра: partnerAtom != null ⇔ партнёр молекула, и smart cast
        // даёт обе типизации разом — без `!!` и без каста.
        val partnerAtom = data.partnerAtom
        val spawn = mutableListOf(
            {
                when {
                    partnerAtom != null && partnerEntity is Molecule -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, partnerAtom, env)
                    partnerEntity is Atom -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, env)
                    else -> error("produce: ${partnerEntity::class.simpleName} не может расти — candidates должен был отсеять")
                }
            },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            // Считаем от САЙТОВ связи, а не от центров: у молекулы центра нет, а место рождения фотона
            // у связывающихся атомов и осмысленнее.
            val p1 = molAtom.kinematics.position
            val p2 = partnerAtom?.kinematics?.position ?: (partnerEntity as Atom).kinematics.position
            val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f) // временно, удалим, когда у молекулы не будет своего радиуса
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
        )
    }
}