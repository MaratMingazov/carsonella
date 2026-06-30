package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.OXYGEN_16
import maratmingazovr.ai.carsonella.chemistry.Element.PHOSPHORUS_31
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Element.SILICON_28
import maratmingazovr.ai.carsonella.chemistry.Element.SULFUR_31
import maratmingazovr.ai.carsonella.chemistry.Element.SULFUR_32
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Oxygen burning — горение кислорода внутри массивной звезды.
 * Два ядра ¹⁶O сливаются по одному из четырёх каналов:
 * 1: ¹⁶O + ¹⁶O → ²⁸Si + ⁴He + γ   (Q = +9.59 МэВ, доминирующий)
 * 2: ¹⁶O + ¹⁶O → ³¹P  + p   + γ   (Q = +7.68 МэВ)
 * 3: ¹⁶O + ¹⁶O → ³¹S  + n   + γ   (Q = +1.50 МэВ, нейтронный канал)
 * 4: ¹⁶O + ¹⁶O → ³²S        + γ   (Q = +16.54 МэВ, минорный γ-канал)
 *
 * Симметрия с StarCarbonBurning: четвёрки каналов (α, p, γ, n) у обеих реакций совпадают
 * по типу — у углерода ²⁰Ne+α / ²³Na+p / ²⁴Mg+γ / ²³Mg+n, у кислорода ²⁸Si+α / ³¹P+p / ³²S+γ / ³¹S+n.
 */
class StarOxygenBurning(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {

    override val id = "StarOxygenBurning"

    private var atom1 : Entity<*>? = null
    private var atom2 : Entity<*>? = null
    private var atom1El : Element? = null   // элементы атомов, запомненные в matchesAtoms — produce не вычисляет заново
    private var atom2El : Element? = null
    private var resultElement : Element? = null
    private var extraElements : List<Element> = emptyList()

    override fun matchesAtoms(reagents: List<Entity<*>>): Boolean {
        atom1 = null
        atom2 = null
        atom1El = null
        atom2El = null
        resultElement = null
        extraElements = emptyList()
        if (reagents.size < 2) return false
        val firstAtom = reagents.first()
        val firstAtomPosition = firstAtom.state().value.position
        // species в локальный val → smart-cast к Elemental ниже (через Entity<*> компилятор сам этого не знает).
        val firstSpecies = firstAtom.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        if (firstSpecies.element != OXYGEN_16) return false
        if (!firstAtom.state().value.alive) return false
        if (firstAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        val (secondAtom, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == OXYGEN_16
            }
            .filter { it.state().value.alive }
            .map { it to it.state().value.position.distanceSquareTo(firstAtomPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (secondAtom.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false

        if (distanceSquare >= OXYGEN_16.details.radius * OXYGEN_16.details.radius * 2f) return false

        // Случайно выбираем один из четырёх каналов горения кислорода.
        val (result, extras) = listOf(
            SILICON_28    to listOf(HELIUM_4),
            PHOSPHORUS_31 to listOf(Proton),
            SULFUR_31     to listOf(NEUTRON),
            SULFUR_32     to emptyList(),
        ).random(entityGenerator.random)

        atom1 = firstAtom
        atom2 = secondAtom
        atom1El = OXYGEN_16   // оба реагента — ¹⁶O по проверке/фильтру
        atom2El = OXYGEN_16
        resultElement = result
        extraElements = extras
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a1 = atom1!!
        val a2 = atom2!!
        val result = resultElement!!
        val extras = extraElements

        val (direction, velocity) = calculateNewEntityDirectionAndVelocity(a1, a2)
        val resultPosition = a1.state().value.position
        val resultRadius = result.details.radius
        val atom1Element = atom1El!!   // запомнили в matchesAtoms
        val atom2Element = atom2El!!
        // Перенос оболочки на продукт (2C2): два ядра сливаются, их электроны (в звезде 0) переходят
        // на продукт, но не больше его Z; лишние улетают свободными e⁻ (shake-off). Extras (α/p/n) — голые.
        val parentElectrons = a1.state().value.electrons + a2.state().value.electrons
        val resultElectrons = minOf(parentElectrons, result.details.p)
        val shakeOff = parentElectrons - resultElectrons
        val spawnList = mutableListOf<() -> Entity<*>>()

        spawnList += {
            entityGenerator.createEntity(
                result,
                resultPosition,
                direction,
                velocity,
                energy = a1.state().value.energy + a2.state().value.energy,
                a1.getEnvironment(),
                electrons = resultElectrons,
            )
        }

        extras.forEachIndexed { index, extra ->
            val offsetSign = if (index % 2 == 0) 1f else -1f
            spawnList += {
                entityGenerator.createEntity(
                    extra,
                    Position(resultPosition.x + offsetSign * 1.5f * direction.x * resultRadius, resultPosition.y),
                    direction,
                    velocity,
                    energy = 0f,
                    environment = a1.getEnvironment(),
                    electrons = 0,
                )
            }
        }

        repeat(shakeOff) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON,
                    Position(resultPosition.x, resultPosition.y + resultRadius),
                    randomDirection(entityGenerator.random),
                    20f,
                    energy = 0f,
                    environment = a1.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        val resultPhotonEnergy = 1000f
        spawnList += {
            entityGenerator.createEntity(
                PHOTON,
                Position(
                    resultPosition.x + 1.5f * direction.x * resultRadius,
                    resultPosition.y + 1.5f * direction.y * resultRadius,
                ),
                direction,
                10f,
                energy = resultPhotonEnergy,
                environment = a1.getEnvironment(),
                electrons = 0,
            )
        }

        return ReactionOutcome(
            consumed = listOf(a1, a2),
            spawn = spawnList,
            description = "$id: ${atom1Element.symbol(a1.state().value.electrons)} + ${atom2Element.symbol(a2.state().value.electrons)} -> ${
                result.symbol(
                    resultElectrons
                )
            } + ${PHOTON.details.symbol} [$resultPhotonEnergy ev]"
        )
    }
}
