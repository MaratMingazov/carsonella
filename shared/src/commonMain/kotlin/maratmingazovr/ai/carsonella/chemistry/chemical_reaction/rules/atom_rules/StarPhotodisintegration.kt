package maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.atom_rules

import maratmingazovr.ai.carsonella.Position
import maratmingazovr.ai.carsonella.TemperatureMode
import maratmingazovr.ai.carsonella.chance
import maratmingazovr.ai.carsonella.chemistry.Element
import maratmingazovr.ai.carsonella.chemistry.Element.ELECTRON
import maratmingazovr.ai.carsonella.chemistry.Element.HELIUM_4
import maratmingazovr.ai.carsonella.chemistry.Element.NEUTRON
import maratmingazovr.ai.carsonella.chemistry.Element.PHOTON
import maratmingazovr.ai.carsonella.chemistry.Element.Proton
import maratmingazovr.ai.carsonella.chemistry.Entity
import maratmingazovr.ai.carsonella.chemistry.Species
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.IEntityGenerator
import maratmingazovr.ai.carsonella.chemistry.chemical_reaction.rules.ReactionOutcome
import maratmingazovr.ai.carsonella.randomDirection

/**
 * Фотодиссоциация ядра (γ,X) — развал ядра жёстким фотоном, ОБРАТНОЕ к радиативному захвату:
 *
 *   A + γ → A′ + ⁴He   (γ,α) — обратное к (α,γ) alphaGammaResult
 *   A + γ → A′ + p      (γ,p) — обратное к (p,γ) protonGammaResult
 *   A + γ → A′ + n      (γ,n) — обратное к (n,γ) neutronGammaResult
 *
 * Это сердце **горения кремния**: при высокой T фотоны разваливают часть ядер, освобождая α/p/n,
 * которые тут же захватываются другими ядрами по α-цепочке ²⁸Si→³²S→…→⁵⁶Ni (уже живёт в
 * StarAlphaGammaReaction). Прямого слияния ²⁸Si+²⁸Si нет — кулоновский барьер слишком высок.
 *
 * Данные не дублируем: target/продукт берём РЕВЕРСОМ существующих полей захвата. Если ядро N —
 * продукт захвата P (P.alphaGammaResult == N и т.п.), то N может развалиться обратно в P + частица.
 * Порог = энергия, излучённая прямым захватом (resultPhotonEnergy=1000): фотон должен её вернуть.
 *
 * Пока только TemperatureMode.Star (как s-процесс — без отдельной HotStar-моды). Звёздные γ от самих
 * ядерных реакций (тоже 1000) служат топливом; цикл замыкается «реакция излучила γ → γ развалил ядро».
 */
class StarPhotodisintegration(
    private val entityGenerator: IEntityGenerator,
) : AtomReactionRule() {
    override val id = "StarPhotodisintegration"

    private sealed class Channel {
        abstract val parent: Element
        abstract val ejected: Element
        data class Alpha(override val parent: Element) : Channel() { override val ejected = HELIUM_4 } // (γ,α)
        data class ProtonOut(override val parent: Element) : Channel() { override val ejected = Proton } // (γ,p)
        data class NeutronOut(override val parent: Element) : Channel() { override val ejected = NEUTRON } // (γ,n)
    }

    private var atom: Entity<*>? = null
    private var photon: Entity<*>? = null
    private var atomEl: Element? = null   // элемент мишени, запомненный в matchesAtoms — produce не вычисляет заново
    private var chosen: Channel? = null

    override fun matchesAtoms(reagents: List<Entity<*>>): Boolean {
        atom = null
        photon = null
        atomEl = null
        chosen = null
        if (reagents.size < 2) return false

        val first = reagents.first()
        if (!first.state().value.alive) return false
        // species в локальный val → smart-cast к Elemental ниже (через Entity<*> компилятор сам этого не знает).
        val firstSpecies = first.state().value.species
        if (firstSpecies !is Species.Elemental) return false
        val element = firstSpecies.element

        // Доступные обратные каналы — реверс полей захвата (N — продукт какого-то захвата P→N).
        val candidates = buildList {
            alphaGammaReverse[element]?.let { add(Channel.Alpha(it)) }
            protonGammaReverse[element]?.let { add(Channel.ProtonOut(it)) }
            neutronGammaReverse[element]?.let { add(Channel.NeutronOut(it)) }
        }
        if (candidates.isEmpty()) return false

        val firstPosition = first.state().value.position
        val (nearestPhoton, distanceSquare) = reagents
            .drop(1)
            .filter {
                val sp = it.state().value.species
                sp is Species.Elemental && sp.element == PHOTON
            }
            .filter { it.state().value.alive }
            .filter { it.state().value.energy >= PHOTON_ENERGY_THRESHOLD }
            .map { it to it.state().value.position.distanceSquareTo(firstPosition) }
            .minByOrNull { it.second }
            ?: return false

        if (first.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (nearestPhoton.getEnvironment().getEnvTemperature() != TemperatureMode.Star) return false
        if (distanceSquare >= element.details.radius * PHOTON.details.radius * 2f) return false
        if (!chance(RATE, entityGenerator.random)) return false

        atom = first
        photon = nearestPhoton
        atomEl = element
        chosen = candidates.random(entityGenerator.random)
        return true
    }

    override fun weight() = 0f

    override fun produce(): ReactionOutcome {
        val a = atom!!
        val ph = photon!!
        val channel = chosen!!
        val parent = channel.parent
        val ejected = channel.ejected

        val atomElement = atomEl!!   // запомнили в matchesAtoms
        val position = a.state().value.position
        val direction = a.state().value.direction
        val velocity = a.state().value.velocity
        val radius = parent.details.radius

        // Перенос электронной оболочки на продукт (как в захватах): продукт-родитель легче (Z падает),
        // наследует электроны N с клампом по своему Z; лишние улетают свободными e⁻ (shake-off).
        // Вылетающая частица (α/p/n) — голая. В звезде N голое → всё нулевое.
        val parentElectrons = minOf(a.state().value.electrons, parent.details.p)
        val shakeOff = a.state().value.electrons - parentElectrons
        // Поглощённый фотон отдаёт энергию связи (порог); излишек — в кинетику продукта.
        val leftover = ph.state().value.energy - PHOTON_ENERGY_THRESHOLD

        val spawnList = mutableListOf<() -> Entity<*>>()
        spawnList += {
            entityGenerator.createEntity(
                parent, position, direction, velocity,
                energy = a.state().value.energy + leftover,
                a.getEnvironment(),
                electrons = parentElectrons,
            )
        }
        spawnList += {
            entityGenerator.createEntity(
                ejected,
                Position(position.x + 1.5f * direction.x * radius, position.y + 1.5f * direction.y * radius),
                direction, 20f, energy = 0f, environment = a.getEnvironment(),
                electrons = 0,
            )
        }
        repeat(shakeOff) {
            spawnList += {
                entityGenerator.createEntity(
                    ELECTRON, Position(position.x, position.y + radius),
                    randomDirection(entityGenerator.random), 20f, energy = 0f, environment = a.getEnvironment(),
                    electrons = 1,
                )
            }
        }

        val electronTail = if (shakeOff > 0) " + $shakeOff${ELECTRON.details.symbol}" else ""
        return ReactionOutcome(
            consumed = listOf(a, ph),
            spawn = spawnList,
            description = "$id: ${atomElement.symbol(a.state().value.electrons)} + ${PHOTON.details.symbol} → ${
                parent.symbol(
                    parentElectrons
                )
            } + ${ejected.symbol(0)}$electronTail",
        )
    }

    companion object {
        // Порог фотодиссоциации = энергия, излучаемая прямым радиативным захватом (resultPhotonEnergy=1000).
        // Фотон должен вернуть эту энергию связи, чтобы развалить ядро.
        private const val PHOTON_ENERGY_THRESHOLD = 1000f
        // Доля контактов «ядро+подходящий γ», на которых развал реально идёт (тюнингуемо, чтобы не
        // разматывать цепочку мгновенно). Веса не различаем — выбор канала равновероятный.
        private const val RATE = 0.1f

        // Реверс полей захвата: продукт → родитель. (γ,X) на N возвращает P, у которого P.(x)GammaResult == N.
        private val alphaGammaReverse: Map<Element, Element> =
            Element.entries.mapNotNull { p -> p.details.alphaGammaResult?.let { it to p } }.toMap()
        private val protonGammaReverse: Map<Element, Element> =
            Element.entries.mapNotNull { p -> p.details.protonGammaResult?.let { it to p } }.toMap()
        private val neutronGammaReverse: Map<Element, Element> =
            Element.entries.mapNotNull { p -> p.details.neutronGammaResult?.let { it to p } }.toMap()
    }
}