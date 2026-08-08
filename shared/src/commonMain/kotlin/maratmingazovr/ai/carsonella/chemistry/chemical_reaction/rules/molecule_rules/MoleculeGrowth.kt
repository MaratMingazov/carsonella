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

        // нужен свободный слот, чтобы было куда расти
        if (!molecule.hasFreeValence) return null
        // Внутри звезды слишком горячо — молекулы не растут (как и не образуются).
        if (molecule.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return null

        val moleculePosition = molecule.state().value.kinematics.position
        val moleculeRadius = molecule.radius

        val (second, distanceSquare) = neighbors
            .filter { canBond(it) }
            .filter { it.getEnvironment() === molecule.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.kinematics.position.distanceSquareTo(moleculePosition) }
            .minByOrNull { it.second }
            ?: return null

        val secondRadius = second.radius
        if (distanceSquare >= moleculeRadius * secondRadius * 2f) return null

        return chooseSites(molecule, second)
    }

    /**
     * Где именно возникнет связь: пара ближайших друг к другу атомов со свободным слотом. Молекула
     * подставляет партнёру ту сторону, которой к нему повёрнута, — а не свой самый старый атом.
     *
     * Партнёр-атом сайта не выбирает, он сам себе конец связи. Партнёр-молекула перебирается парами:
     * атомов у наших молекул единицы, так что полный перебор дешевле любой хитрости.
     *
     * null возможен только если свободных слотов не нашлось, чего [canBond] и `hasFreeValence` не должны
     * пропустить. Возвращаем его вместо `!!`: снаружи это просто «реакция не состоялась».
     */
    private fun chooseSites(subject: Molecule, partner: Entity): Match? {
        val subjectSites = subject.freeValenceAtoms
        return when (partner) {
            is Atom -> {
                val partnerPosition = partner.state().value.kinematics.position
                val site = subjectSites.minByOrNull { it.kinematics.position.distanceSquareTo(partnerPosition) } ?: return null
                Match(subject, partner, site, partnerAtom = null, partnerIsotope = partner.element)
            }
            is Molecule -> {
                val pair = subjectSites
                    .flatMap { site -> partner.freeValenceAtoms.map { site to it } }
                    .minByOrNull { (site, partnerSite) -> site.kinematics.position.distanceSquareTo(partnerSite.kinematics.position) }
                    ?: return null
                Match(subject, partner, pair.first, pair.second, pair.second.structure.isotope)
            }
            is SubAtom, is Star -> null   // canBond уже отсеял; ветка ради исчерпывающего when
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
        val data = match as Match
        return BondEnergy.of(data.moleculeAtom.structure.isotope, data.partnerIsotope, order = 1) ?: 0f
    }

    override fun produce(match: MatchedData): ReactionOutcome {
        val data = match as Match
        val molecule = data.molecule
        val partnerEntity = data.partner
        val molAtom = data.moleculeAtom
        val env = molecule.getEnvironment()

        // Образование связи ЭКЗОТЕРМИЧНО: энергию новой связи высвобождаем фотоном (как в CovalentBondFormation).
        val bondEnergy = BondEnergy.of(molAtom.structure.isotope, data.partnerIsotope, order = 1)

        // Само слияние — дело конструкторов Molecule: граф, кинематику, энергию и электроны (сохранение, §8)
        // считают они. Правилу остаётся выбрать перегрузку по типу партнёра: молекула или атом.
        // Развилка по САЙТУ, а не по классу партнёра: partnerAtom != null ⇔ партнёр молекула, и smart cast
        // даёт обе типизации разом — без `!!` и без каста.
        val partnerAtom = data.partnerAtom
        val spawn = mutableListOf<() -> Entity>(
            {
                when {
                    partnerAtom != null && partnerEntity is Molecule -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, partnerAtom, env)
                    partnerEntity is Atom -> entityGenerator.createMolecule(molecule, molAtom, partnerEntity, env)
                    else -> error("produce: ${partnerEntity::class.simpleName} не может расти — canBond должен был отсеять")
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