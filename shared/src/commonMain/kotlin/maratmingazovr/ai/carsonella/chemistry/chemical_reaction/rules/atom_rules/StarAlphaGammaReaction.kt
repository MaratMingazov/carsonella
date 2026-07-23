package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome


// альфа-захват
// «Внутри звезды ион ловит ⁴He, превращается в более тяжёлый элемент».
class StarAlphaGammaReaction(
    private val entityGenerator: IEntityGenerator,      // вот сюда нужно будет передать лямбду, с помощью которой можно создать молекулу водорода H2
) : AtomReactionRule() {
    override val id = "AlphaReaction"

    private var atom1 : Entity? = null
    private var atom2 : Entity? = null
    private var atom1El : Element? = null   // элементы атомов, запомненные в matchesAtoms — produce не вычисляет заново
    private var atom2El : Element? = null

    override fun matchesAtoms(reagents: List<Entity>) : Boolean {
        atom1 = null
        atom2 = null
        atom1El = null
        atom2El = null
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = reagents.first().state().value.position
        if (!firstAtom.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val firstAtomElement = firstSpecies.element
        if (firstAtomElement.details.alphaGammaResult == null) return false // значит элемент не участвует в альфа захвате

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == HELIUM_4
            }
            .filter { it.state().value.alive }
            .map { it to  it.state().value.position.distanceSquareTo(firstAtomPosition)}
            .minByOrNull { it.second }
            ?: return false

        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        val secondSpecies = secondAtom.state().value.species
        if (secondSpecies !is Species.Elemental) return false
        val secondAtomElement = secondSpecies.element

        return if (distanceSquare < firstAtomElement.details.radius * secondAtomElement.details.radius * 2f) {
            atom1 = firstAtom
            atom2 = secondAtom
            atom1El = firstAtomElement
            atom2El = secondAtomElement
            true
        } else {
            false
        }
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {

        val (direction,velocity) = calculateNewEntityDirectionAndVelocity(atom1!!, atom2!!)
        val resultPosition = atom1!!.state().value.position
        val atom1Element = atom1El!!   // запомнили в matchesAtoms
        val atom2Element = atom2El!!
        val resultElement = atom1Element.details.alphaGammaResult!!
        // Перенос электронной оболочки на продукт (2C2): наследует электроны родителя-ядра,
        // но не больше своего Z. (α,γ) повышает Z → кламп здесь no-op, shake-off не нужен.
        val resultElectrons = minOf(atom1!!.state().value.electrons, resultElement.details.p)
        val resultPhotonEnergy = 1000f


        return ReactionOutcome(
            consumed = listOf(atom1!!, atom2!!),
            spawn = listOf {
                entityGenerator.createEntity(
                    resultElement,
                    resultPosition,
                    direction,
                    velocity,
                    energy = atom1!!.state().value.energy + atom2!!.state().value.energy,
                    atom1!!.getEnvironment(),
                    electrons = resultElectrons,
                )
                entityGenerator.createEntity(
                    Element.PHOTON,
                    Position(
                        resultPosition.x + 1.5f * direction.x * resultElement.details.radius,
                        resultPosition.y + 1.5f * direction.y * resultElement.details.radius
                    ),
                    direction,
                    10f,
                    energy = resultPhotonEnergy,
                    environment = atom1!!.getEnvironment(),
                    electrons = 0,
                )
            },
            description = "$id: ${atom1Element.symbol(atom1!!.state().value.electrons)} + ${atom2Element.symbol(atom2!!.state().value.electrons)} -> ${
                resultElement.symbol(
                    resultElectrons
                )
            } + ${Element.PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
