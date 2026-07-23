package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.molecule_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.chemistry.graph.AtomNode
import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import maratmingazovr.ai.carsonella.chemistry.graph.BondEnergy
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Рост молекулы: молекула со свободным валентным слотом притягивает ближайшего соседа,
 * у которого тоже есть свободный слот, и сливается с ним в одну бо́льшую молекулу.
 *
 * Субъект — всегда молекула ([MoleculeReactionRule]). Партнёром может быть:
 *  - **атом** (нейтральный лёгкий, `valence > 0`) — атом+молекула: так O–H ловит второй H → H₂O;
 *  - **другая молекула** (есть свободный слот) — молекула+молекула: ·CH₃ + ·OH → CH₃OH.
 *
 * Атом-партнёр оборачивается в одноузловой граф, и оба случая сливаются одним [MoleculeGraph.merge]
 * (атом = вырожденная молекула, §8). Связь стартует одинарной (`order = 1`) — как в 3a; кратность
 * эмёрджентна (рост/усиление, см. §6).
 *
 * Выбор партнёра — ПЕРМИССИВНЫЙ (решение 3b): любой свободный слот + близость, единственный гард —
 * потолок валентности (его держит `freeSlots`). Радикалы-интермедиаты допускаются и растут дальше;
 * энергетика/предпочтения связей — отдельный слой реализма позже (§5.3/§6).
 */
class MoleculeGrowth(
    private val entityGenerator: IEntityGenerator,
) : MoleculeReactionRule() {
    override val id = "MoleculeGrowth"

    private var molecule: Entity? = null
    private var partner: Entity? = null

    override fun matchesMolecule(reagents: List<Entity>): Boolean {
        molecule = null
        partner = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        // субъект-молекула гарантирован базой; нужен свободный слот, чтобы было куда расти
        val firstGraph = (first.state().value.species as Species.Molecular).graph
        if (!firstGraph.hasFreeSlot) return false
        // Внутри звезды слишком горячо — молекулы не растут (как и не образуются).
        if (first.getEnvironment().getEnvTemperature() == TemperatureMode.Star) return false

        val firstPosition = first.state().value.position
        val firstRadius = first.state().value.species.radius

        val (second, distanceSquare) = reagents
            .drop(1)
            .filter { canBond(it) }
            .filter { it.getEnvironment() === first.getEnvironment() }   // оба в одной среде
            .map { it to it.state().value.position.distanceSquareTo(firstPosition) }
            .minByOrNull { it.second }
            ?: return false

        val secondRadius = second.state().value.species.radius
        return if (distanceSquare < firstRadius * secondRadius * 2f) {
            molecule = first
            partner = second
            true
        } else {
            false
        }
    }

    // Партнёр способен дать молекуле новую связь: живой, со свободным слотом.
    //  - молекула: hasFreeSlot();
    //  - атом: нейтральный лёгкий атом с valence > 0 (как в CovalentBondFormation).
    private fun canBond(entity: Entity): Boolean {
        val state = entity.state().value
        if (!state.alive) return false
        return when (val species = state.species) {
            is Species.Molecular -> species.graph.hasFreeSlot
            is Species.Elemental -> {
                val element = species.element
                element.details.type == ElementType.Atom &&
                    state.electrons == element.details.p &&   // нейтральный — есть электроны для общей пары
                    element.valence() > 0
            }
        }
    }

    // Партнёр как граф: молекула → её граф; атом → одноузловой граф (атом = вырожденная молекула, §8).
    private fun graphOf(entity: Entity): MoleculeGraph =
        when (val species = entity.state().value.species) {
            is Species.Molecular -> species.graph
            is Species.Elemental -> MoleculeGraph(listOf(AtomNode(0, species.element)), emptyList())
        }

    // Энергия связи, которую даст рост (новая связь order=1) — экзотермично, «+» (контракт weight = энергия
    // реакции со знаком). Так рост честно конкурирует с усилением: у углерода рост выгоднее (C–H 4.28),
    // у кислорода — усиление (O=O выигрыш 3.65 > рост O–O 1.51). Поля выставлены в matchesMolecule.
    override fun weight(): Float {
        val mol = molecule ?: return 0f
        val partnerEntity = partner ?: return 0f
        val molGraph = (mol.state().value.species as Species.Molecular).graph
        val partnerGraph = graphOf(partnerEntity)
        val molNode = molGraph.firstFreeSlotAtomNode!!
        val partnerNode = partnerGraph.firstFreeSlotAtomNode!!
        return BondEnergy.of(molNode.isotope, partnerNode.isotope, order = 1) ?: 0f
    }

    override fun produce(): ReactionOutcome {
        val mol = molecule!!
        val partnerEntity = partner!!
        val molGraph = (mol.state().value.species as Species.Molecular).graph
        val partnerGraph = graphOf(partnerEntity)

        // matchesMolecule гарантировал свободные слоты у обоих → firstFreeSlotNode не null.
        val molNode = molGraph.firstFreeSlotAtomNode!!
        val partnerNode = partnerGraph.firstFreeSlotAtomNode!!
        val merged = molGraph.merge(partnerGraph, thisNode = molNode.localId, otherNode = partnerNode.localId, bondOrder = 1)

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(mol, partnerEntity)
        val p1 = mol.state().value.position
        val p2 = partnerEntity.state().value.position
        val midpoint = Position((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        // Сохранение электронов (§8): электроны новой молекулы = сумма электронов реагентов.
        val electrons = mol.state().value.electrons + partnerEntity.state().value.electrons
        val energy = mol.state().value.energy + partnerEntity.state().value.energy
        val env = mol.getEnvironment()

        // Образование связи ЭКЗОТЕРМИЧНО: энергию новой связи высвобождаем фотоном (как в CovalentBondFormation).
        val molIso = molNode.isotope
        val partnerIso = partnerNode.isotope
        val bondEnergy = BondEnergy.of(molIso, partnerIso, order = 1)
        val spawn = mutableListOf(
            { entityGenerator.createEntity(Species.Molecular(merged), midpoint, direction, velocity, energy, env, electrons) },
        )
        if (bondEnergy != null && bondEnergy > 0f) {
            spawn += {
                // Фотон уносит энергию связи и УЛЕТАЕТ (скорость 40, как в SpontaneousEmission): за тик покидает
                // радиус активации, иначе PhotoDissociation тут же распустил бы молекулу обратно (энергия фотона =
                // энергии связи = порогу распада) — бесконечный цикл образование↔распад.
                entityGenerator.createEntity(Element.PHOTON, midpoint, randomDirection(entityGenerator.random), 40f, energy = bondEnergy, environment = env, electrons = 0)
            }
        }

        return ReactionOutcome(
            consumed = listOf(mol, partnerEntity),
            spawn = spawn,
            description = "$id: ${molGraph.formulaPretty} + ${partnerGraph.formulaPretty} -> ${merged.formulaPretty}" +
                (bondEnergy?.let { " + γ[${it}eV]" } ?: ""),
        )
    }
}