package maratmingazovr.ai.carsonella.chemistry.graph

import maratmingazovr.ai.carsonella.Lang

/**
 * Известная молекула как КЛЮЧ и как имя. Ключом её адресуют уровни, палитра и картинки; имена лежат
 * рядом, оба языка на одной строке — пропуск или расхождение видно глазом.
 *
 * Почему перечисление, а не строка: цель уровня и содержимое палитры проверяются компилятором. Со
 * строкой опечатка стреляла в рантайме («в реестре нет записи»), теперь она не собирается. Реестр
 * обязан покрыть КАЖДУЮ константу — это проверяется при инициализации, поэтому [MoleculeRegistry.byId]
 * не возвращает null.
 *
 * Порядок константы = порядок сборки в реестре, от простого к сложному.
 *
 * Описания сюда не поехали: они по несколько сотен символов, в конструкторе перечисления это была бы
 * нечитаемая простыня. Они остаются в реестре и получат язык отдельно.
 */
enum class MoleculeId(val ru: String, val en: String) {
    DIHYDROGEN       ("Водород", "Dihydrogen"),
    DIOXYGEN         ("Кислород", "Dioxygen"),
    DINITROGEN       ("Азот", "Dinitrogen"),
    HYDROXYL         ("Гидроксил", "Hydroxyl"),
    DICARBON         ("Дикарбон", "Dicarbon"),
    WATER            ("Вода", "Water"),
    HYDROPEROXYL     ("Гидропероксил", "Hydroperoxyl"),
    HYDROGEN_PEROXIDE("Перекись водорода", "Hydrogen peroxide"),
    TRIOXIDANE       ("Триоксидан", "Trioxidane"),
    TETRAOXIDANE     ("Тетраоксидан", "Tetraoxidane"),
    IMIDOGEN         ("Имидоген", "Imidogen"),
    AMINO_RADICAL    ("Аминорадикал", "Amino radical"),
    AMMONIA          ("Аммиак", "Ammonia"),
    CARBON_DIOXIDE   ("Углекислый газ", "Carbon dioxide"),
    CYANO            ("Циано", "Cyano"),
    HYDROGEN_CYANIDE ("Циановодород", "Hydrogen cyanide"),
    METHYLIDYNE      ("Метилидин", "Methylidyne"),
    METHYLENE        ("Метилен", "Methylene"),
    METHYL           ("Метил", "Methyl"),
    METHANE          ("Метан", "Methane"),
    ETHYNYL          ("Этинил", "Ethynyl"),
    ACETYLENE        ("Ацетилен", "Acetylene"),
    VINYL            ("Винил", "Vinyl"),
    ETHYLENE         ("Этилен", "Ethylene"),
    ETHYL            ("Этил", "Ethyl"),
    ETHANE           ("Этан", "Ethane"),
    BUTANE           ("Бутан", "Butane"),
    ISOPROPYL        ("Изопропил", "Isopropyl"),
    ISOBUTANE        ("Изобутан", "Isobutane"),
    BUTENE_1         ("Бутен-1", "1-Butene"),
    BUTENE_2         ("Бутен-2", "2-Butene"),
    ISOPROPENYL      ("Изопропенил", "Isopropenyl"),
    ISOBUTYLENE      ("Изобутилен", "Isobutylene"),
    TRIMETHYLENE     ("Триметилен", "Trimethylene"),
    CYCLOPROPANE     ("Циклопропан", "Cyclopropane"),
    OXIRANE          ("Оксиран", "Oxirane"),
    BENZENE          ("Бензол", "Benzene"),
    FORMYL           ("Формил", "Formyl"),
    FORMALDEHYDE     ("Формальдегид", "Formaldehyde"),
    METHANOL         ("Метанол", "Methanol"),
    FORMIC_ACID      ("Муравьиная кислота", "Formic acid"),
    ETHANOL          ("Этанол", "Ethanol"),
    ;

    fun name(lang: Lang): String = when (lang) {
        Lang.RU -> ru
        Lang.EN -> en
    }
}
