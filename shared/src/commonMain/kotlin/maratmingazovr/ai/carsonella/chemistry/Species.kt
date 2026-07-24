package maratmingazovr.ai.carsonella.chemistry

import maratmingazovr.ai.carsonella.chemistry.graph.MoleculeGraph
import kotlin.math.round


sealed interface Species {
    val mass: Float
    val protons: Int
    val radius: Float
    fun displaySymbol(electrons: Int): String
    fun energyLevels(electrons: Int): List<Float> // Энергетическая лестница (эВ): уровни возбуждения, последний = порог ионизации.
    fun describe(s: EntityState): String

    data class Elemental(val element: Element) : Species {
        override val mass: Float get() = if (element == Element.ELECTRON) 1f else (element.details.p + element.details.n).toFloat()
        override val protons: Int get() = element.details.p
        override val radius: Float get() = element.details.radius
        override fun displaySymbol(electrons: Int): String = element.symbol(electrons)
        override fun energyLevels(electrons: Int): List<Float> = element.energyLevels(electrons)
        override fun describe(s: EntityState): String = when (element.details.type) {
            ElementType.Atom -> """
                |${element.label(s.electrons)}
                |Protons: ${element.details.p}
                |Neutrons: ${element.details.n}
                |Electrons: ${s.electrons}
                |Energy ${round(s.energy * 100) / 100}
            """.trimMargin()

            ElementType.SubAtom -> {
                val base = """
                    |${element.label(s.electrons)}: ${s.id}
                    |Position (${s.position.x.toInt()}, ${s.position.y.toInt()})
                    |Velocity ${round(s.velocity * 100) / 100}
                    |Energy ${round(s.energy * 100) / 100}
                """.trimMargin()
                // Спектр осмыслен только у фотона (у него energy — это E=hν) — см. SubAtom.
                if (element == Element.PHOTON) "$base\nСпектр: ${lightBandFromEnergyEv(s.energy).label}" else base
            }

            ElementType.Star -> """
                |${element.label(s.electrons)}: ${s.id}
                |Position (${s.position.x.toInt()}, ${s.position.y.toInt()})
                |Velocity ${round(s.velocity * 100) / 100}
                |Energy ${round(s.energy * 100) / 100}
            """.trimMargin()
        }
    }

    data class Molecular(val graph: MoleculeGraph) : Species {
        override val mass: Float get() = graph.mass
        override val protons: Int get() = graph.protons
        override val radius: Float get() = MOLECULE_RADIUS
        override fun displaySymbol(electrons: Int): String = graph.formulaPretty + chargeSuffix(graph.protons - electrons)
        override fun energyLevels(electrons: Int): List<Float> = graph.energyLevels

        override fun describe(s: EntityState): String {
            val lines = mutableListOf(
                graph.formulaPretty,
                "Energy ${round(s.energy * 100) / 100}",
            )
            // Слабейшая связь = порог диссоциации (рвётся первой). null у одноатомного осколка
            // или если тип связи не в каталоге BondEnergy — тогда строку не показываем.
            graph.weakestBondAndEnergy?.let { (_, energy) ->
                lines += "Weakest bond ${round(energy * 100) / 100} eV"
            }
            return lines.joinToString("\n")
        }
    }
}

// Пока константа (как старый дефолт Details.radius); при желании позже выведем из размера графа.
private const val MOLECULE_RADIUS = 20f