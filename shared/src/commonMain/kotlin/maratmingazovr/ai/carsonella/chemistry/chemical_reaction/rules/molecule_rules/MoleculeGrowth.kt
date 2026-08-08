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
        if (!subject.hasFreeValence) return null
        // Внутри звезды слишком горячо — молекулы не растут (как и не образуются).
        if (subject.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null

        val subjectPosition = subject.state().value.kinematics.position
        val subjectRadius = subject.radius

        val (second, distanceSquare) = neighbors
            .filter { canBond(it) }
            .filter { it.getEnvironment() === subject.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.kinematics.position.distanceSquareTo(subjectPosition) }
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
            is Molecule -> entity.hasFreeValence
            is Atom -> state.electrons == entity.element.details.p &&   // нейтральный — есть электроны для общей пары
                entity.element.valence(state.electrons) > 0
            is SubAtom, is Star -> false
        }
    }

    // Энергия связи, которую даст рост (новая связь order=1) — экзотермично, «+» (контракт weight = энергия
    // реакции со знаком). Так рост честно конкурирует с усилением: у углерода рост выгоднее (C–H 4.28),
    // у кислорода — усиление (O=O выигрыш 3.65 > рост O–O 1.51). Данные из Match.
    override fun weight(match: MatchedData): Float {
        val (molecule, partnerEntity) = match as Match
        val partnerEntityIsotop = getIsotope(partnerEntity)
        val moleculeAtomIsotope = molecule.firstFreeValenceAtomIsotope!!
        return BondEnergy.of(moleculeAtomIsotope, partnerEntityIsotop, order = 1) ?: 0f
    }

    // Изотоп того атома партнёра, который войдёт в новую связь. Зовётся только после canBond,
    // поэтому частица и звезда сюда не доходят.
    private fun getIsotope(entity: Entity): Element = when (entity) {
        is Molecule -> entity.firstFreeValenceAtomIsotope!!
        is Atom -> entity.element
        is SubAtom, is Star -> error("getIsotope: ${entity::class.simpleName} не может расти — canBond должен был отсеять")
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val (molecule, partnerEntity) = match as Match
        val env = molecule.getEnvironment()

        // matchesMolecule гарантировал свободные слоты у обоих → firstFreeValenceAtom не null.
        val molAtom = molecule.firstFreeValenceAtom()!!

        // Образование связи ЭКЗОТЕРМИЧНО: энергию новой связи высвобождаем фотоном (как в CovalentBondFormation).
        val bondEnergy = BondEnergy.of(molAtom.structure.isotope, getIsotope(partnerEntity), order = 1)

        // Само слияние — дело конструкторов Molecule: граф, кинематику, энергию и электроны (сохранение, §8)
        // считают они. Правилу остаётся выбрать перегрузку по типу партнёра: молекула или атом.
        val spawn = mutableListOf<() -> Entity>(
            {
                when (partnerEntity) {
                    is Molecule -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, partnerEntity.firstFreeValenceAtom()!!, env)
                    is Atom -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, env)
                    is SubAtom, is Star -> error("produce: ${partnerEntity::class.simpleName} не может расти — canBond должен был отсеять")
                }
            },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            val p1 = molecule.state().value.kinematics.position
            val p2 = partnerEntity.state().value.kinematics.position
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