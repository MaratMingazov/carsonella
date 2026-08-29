package maratmingazovr.ai.carsonella.chemistry.registry

import maratmingazovr.ai.carsonella.TranslatedText
import maratmingazovr.ai.carsonella.chemistry.Details
import maratmingazovr.ai.carsonella.chemistry.ElementType
import maratmingazovr.ai.carsonella.chemistry.SUPERSCRIPT_DIGITS
import maratmingazovr.ai.carsonella.chemistry.atomEnergyLevelsTable
import maratmingazovr.ai.carsonella.chemistry.chargeSuffix
import maratmingazovr.ai.carsonella.chemistry.electronegativityTable
import maratmingazovr.ai.carsonella.chemistry.elementDetails

sealed interface ElementOrMolecule

enum class AtomElement(val title: TranslatedText, val description: TranslatedText) : ElementOrMolecule {
    // --- субатомные частицы ---
    PHOTON(
        TranslatedText(ru = "Фотон", en = "Photon"),
        TranslatedText(
            ru = "Фотон - это частица света. Массы у него нет вовсе, поэтому он всегда летит с одной и той же скоростью - 300 тысяч километров в секунду, и от Солнца до нас добирается за 8 минут. Живёт он ровно от рождения до встречи: родился, когда электрон отдал лишнюю энергию, и исчез, когда его кто-то поймал.",
            en = "A photon is a particle of light. It has no mass at all, so it always travels at one and the same speed - 300 thousand kilometres per second - and it takes 8 minutes to get here from the Sun. It lives exactly from birth to encounter: born when an electron gave up its spare energy, gone the moment something catches it.",
        ),
    ),
    ELECTRON(
        TranslatedText(ru = "Электрон", en = "Electron"),
        TranslatedText(
            ru = "Электрон в 1836 раз легче протона. Он не шарик и не бегает по орбите: вокруг ядра он размазан облаком, и спросить, где он именно, нельзя. Зато вся химия - это он: связь между атомами и есть поделённые электроны. И ток в проводах тоже они.",
            en = "An electron is 1836 times lighter than a proton. It is not a little ball and it does not run along an orbit: around the nucleus it is smeared into a cloud, and there is no asking where exactly it is. But all of chemistry is electrons: a bond between atoms is simply electrons shared. And the current in wires is them too.",
        ),
    ),
    NEUTRON(
        TranslatedText(ru = "Нейтрон", en = "Neutron"),
        TranslatedText(
            ru = "Заряда у нейтрона нет, поэтому ядра его не отталкивают - он влетает в них беспрепятственно, и так рождаются новые элементы. Внутри ядра он живёт сколько угодно, а вылетев наружу распадается минут за пятнадцать: превращается в протон и электрон.",
            en = "A neutron has no charge, so nuclei do not push it away - it flies straight in, and that is how new elements are born. Inside a nucleus it lives as long as you like, but out on its own it falls apart in about fifteen minutes: it turns into a proton and an electron.",
        ),
    ),
    POSITRON(
        TranslatedText(ru = "Позитрон", en = "Positron"),
        TranslatedText(
            ru = "Позитрон - это электрон наоборот: та же частица, но заряд у неё плюс. Встретив электрон, оба исчезают, а вместо них рождаются два фотона - вещество целиком превращается в свет. Позитроны родятся в недрах звёзд, а ещё работают в больницах: ПЭТ-сканер видит как раз эти вспышки.",
            en = "A positron is an electron the other way round: the same particle, but its charge is plus. When it meets an electron both vanish, and two photons are born instead - matter turns into light entirely. Positrons are born in the cores of stars, and they also work in hospitals: a PET scanner sees exactly these flashes.",
        ),
    ),

    // --- атомы ---
    HYDROGEN(
        TranslatedText(ru = "Водород", en = "Hydrogen"),
        TranslatedText(
            ru = "Проще атома не бывает: один протон и один электрон, больше в нём ничего нет. Почти всё вещество Вселенной сделано из него. А на Земле свободного водорода почти не осталось - он такой лёгкий, что улетает из атмосферы в космос; весь наш водород связан в воде и в живом.",
            en = "There is no simpler atom: one proton and one electron, and nothing else in it. Almost all the matter in the Universe is made of it. On Earth, though, there is hardly any free hydrogen left - it is so light that it escapes the atmosphere into space; all of ours is locked up in water and in living things.",
        ),
    ),
    DEUTERIUM(TranslatedText(ru="Дейтерий", en="Deuterium"), TranslatedText(ru = "", en = "")),
    HELIUM_3(TranslatedText(ru="Гелий", en="Helium"), TranslatedText(ru = "", en = "")),
    HELIUM_4(TranslatedText(ru="Гелий", en="Helium"), TranslatedText(ru = "", en = "")),
    LITHIUM_7(TranslatedText(ru="Литий", en="Lithium"), TranslatedText(ru = "", en = "")),
    LITHIUM_8(TranslatedText(ru="Литий", en="Lithium"), TranslatedText(ru = "", en = "")),
    BERYLLIUM_7(TranslatedText(ru="Бериллий", en="Beryllium"), TranslatedText(ru = "", en = "")),
    BERYLLIUM_8(TranslatedText(ru="Бериллий", en="Beryllium"), TranslatedText(ru = "", en = "")),
    BORON_8(TranslatedText(ru="Бор", en="Boron"), TranslatedText(ru = "", en = "")),
    CARBON_12(
        TranslatedText(ru = "Углерод", en = "Carbon"),
        TranslatedText(
            ru = "Углерод держит сразу четыре связи и умеет цепляться сам за себя - цепочками, кольцами, сетками любой длины. Поэтому вся жизнь построена на нём, и соединений у него больше, чем у всех остальных элементов вместе взятых. И грифель карандаша, и алмаз - это он: разница только в том, как соединены атомы.",
            en = "Carbon holds four bonds at once and can latch onto itself - in chains, rings and nets of any length. That is why all life is built on it, and why it has more compounds than every other element put together. Pencil lead and diamond are both carbon: the only difference is how the atoms are joined.",
        ),
    ),
    CARBON_13(TranslatedText(ru="Углерод", en="Carbon"), TranslatedText(ru = "", en = "")),
    CARBON_14(TranslatedText(ru="Углерод", en="Carbon"), TranslatedText(ru = "", en = "")),
    NITROGEN_13(TranslatedText(ru="Азот", en="Nitrogen"), TranslatedText(ru = "", en = "")),
    NITROGEN_14(
        TranslatedText(ru = "Азот", en = "Nitrogen"),
        TranslatedText(
            ru = "Азот - это про белки: из них сделано всё живое, и в каждом есть его атомы. У атома три свободные связи, но в воздухе они заняты друг другом - два азота держит тройная связь, самая прочная из обычных. Поэтому воздуха вокруг полно, а растениям он недоступен: разорвать эту связь умеют только молнии и бактерии.",
            en = "Nitrogen is about proteins: everything alive is made of them, and every one has nitrogen atoms in it. The atom has three free bonds, but in the air they are taken up by each other - two nitrogens are held by a triple bond, the strongest of the ordinary ones. So the air is full of it and plants still cannot reach it: only lightning and bacteria can break that bond.",
        ),
    ),
    NITROGEN_15(TranslatedText(ru="Азот", en="Nitrogen"), TranslatedText(ru = "", en = "")),
    OXYGEN_15(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    OXYGEN_16(
        TranslatedText(ru = "Кислород", en = "Oxygen"),
        TranslatedText(
            ru = "Во Вселенной кислород третий по распространённости - после водорода и гелия, а на Земле он вообще первый: почти половина веса земной коры это он. Одному ему не сидится: до полной оболочки не хватает двух электронов, и он хватается за первого встречного. Камни под ногами, вода в стакане и воздух в лёгких - везде он.",
            en = "In the Universe oxygen comes third - after hydrogen and helium - and on Earth it comes first: nearly half the weight of the crust is oxygen. It cannot sit still on its own: it is two electrons short of a full shell, so it grabs the first thing it meets. The rocks underfoot, the water in a glass, the air in your lungs - it is in all of them.",
        ),
    ),
    OXYGEN_17(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    OXYGEN_18(TranslatedText(ru="Кислород", en="Oxygen"), TranslatedText(ru = "", en = "")),
    FLUORINE_17(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    FLUORINE_18(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    FLUORINE_19(TranslatedText(ru="Фтор", en="Fluorine"), TranslatedText(ru = "", en = "")),
    NEON_20(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    NEON_21(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    NEON_22(TranslatedText(ru="Неон", en="Neon"), TranslatedText(ru = "", en = "")),
    SODIUM_21(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    SODIUM_22(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    SODIUM_23(TranslatedText(ru="Натрий", en="Sodium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_23(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_24(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_25(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    MAGNESIUM_26(TranslatedText(ru="Магний", en="Magnesium"), TranslatedText(ru = "", en = "")),
    ALUMINUM_25(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    ALUMINUM_26(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    ALUMINUM_27(TranslatedText(ru="Алюминий", en="Aluminum"), TranslatedText(ru = "", en = "")),
    SILICON_28(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_29(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_30(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    SILICON_31(TranslatedText(ru="Кремний", en="Silicon"), TranslatedText(ru = "", en = "")),
    PHOSPHORUS_31(TranslatedText(ru="Фосфор", en="Phosphorus"), TranslatedText(ru = "", en = "")),
    SULFUR_31(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_32(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_33(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_34(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    SULFUR_35(TranslatedText(ru="Сера", en="Sulfur"), TranslatedText(ru = "", en = "")),
    CHLORINE_35(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    CHLORINE_36(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    CHLORINE_37(TranslatedText(ru="Хлор", en="Chlorine"), TranslatedText(ru = "", en = "")),
    ARGON_36(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_37(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_38(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    ARGON_39(TranslatedText(ru="Аргон", en="Argon"), TranslatedText(ru = "", en = "")),
    POTASSIUM_39(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    POTASSIUM_40(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    POTASSIUM_41(TranslatedText(ru="Калий", en="Potassium"), TranslatedText(ru = "", en = "")),
    CALCIUM_40(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_41(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_42(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_43(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_44(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    CALCIUM_45(TranslatedText(ru="Кальций", en="Calcium"), TranslatedText(ru = "", en = "")),
    SCANDIUM_45(TranslatedText(ru="Скандий", en="Scandium"), TranslatedText(ru = "", en = "")),
    SCANDIUM_46(TranslatedText(ru="Скандий", en="Scandium"), TranslatedText(ru = "", en = "")),
    TITANIUM_44(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_46(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_47(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_48(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_49(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_50(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    TITANIUM_51(TranslatedText(ru="Титан", en="Titanium"), TranslatedText(ru = "", en = "")),
    VANADIUM_48(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    VANADIUM_51(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    VANADIUM_52(TranslatedText(ru="Ванадий", en="Vanadium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_48(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_52(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_53(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_54(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    CHROMIUM_55(TranslatedText(ru="Хром", en="Chromium"), TranslatedText(ru = "", en = "")),
    MANGANESE_52(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    MANGANESE_55(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    MANGANESE_56(TranslatedText(ru="Марганец", en="Manganese"), TranslatedText(ru = "", en = "")),
    IRON_52(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_56(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_57(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_58(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    IRON_59(TranslatedText(ru="Железо", en="Iron"), TranslatedText(ru = "", en = "")),
    COBALT_56(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    COBALT_59(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    COBALT_60(TranslatedText(ru="Кобальт", en="Cobalt"), TranslatedText(ru = "", en = "")),
    NICKEL_56(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_60(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_61(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_62(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    NICKEL_63(TranslatedText(ru="Никель", en="Nickel"), TranslatedText(ru = "", en = "")),
    COPPER_63(TranslatedText(ru="Медь", en="Copper"), TranslatedText(ru = "", en = "")),
    COPPER_64(TranslatedText(ru="Медь", en="Copper"), TranslatedText(ru = "", en = "")),
    ZINC_64(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_65(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_66(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_67(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_68(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    ZINC_69(TranslatedText(ru="Цинк", en="Zinc"), TranslatedText(ru = "", en = "")),
    GALLIUM_69(TranslatedText(ru="Галлий", en="Gallium"), TranslatedText(ru = "", en = "")),
    GALLIUM_70(TranslatedText(ru="Галлий", en="Gallium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_70(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_71(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_72(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_73(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_74(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    GERMANIUM_75(TranslatedText(ru="Германий", en="Germanium"), TranslatedText(ru = "", en = "")),
    ARSENIC_75(TranslatedText(ru="Мышьяк", en="Arsenic"), TranslatedText(ru = "", en = "")),
    ARSENIC_76(TranslatedText(ru="Мышьяк", en="Arsenic"), TranslatedText(ru = "", en = "")),
    SELENIUM_76(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_77(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_78(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    SELENIUM_79(TranslatedText(ru="Селен", en="Selenium"), TranslatedText(ru = "", en = "")),
    BROMINE_79(TranslatedText(ru="Бром", en="Bromine"), TranslatedText(ru = "", en = "")),
    BROMINE_80(TranslatedText(ru="Бром", en="Bromine"), TranslatedText(ru = "", en = "")),
    KRYPTON_80(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_81(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_82(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_83(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_84(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    KRYPTON_85(TranslatedText(ru="Криптон", en="Krypton"), TranslatedText(ru = "", en = "")),
    RUBIDIUM_85(TranslatedText(ru="Рубидий", en="Rubidium"), TranslatedText(ru = "", en = "")),
    RUBIDIUM_86(TranslatedText(ru="Рубидий", en="Rubidium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_86(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_87(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_88(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    STRONTIUM_89(TranslatedText(ru="Стронций", en="Strontium"), TranslatedText(ru = "", en = "")),
    YTTRIUM_89(TranslatedText(ru="Иттрий", en="Yttrium"), TranslatedText(ru = "", en = "")),
    YTTRIUM_90(TranslatedText(ru="Иттрий", en="Yttrium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_90(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_91(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_92(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    ZIRCONIUM_93(TranslatedText(ru="Цирконий", en="Zirconium"), TranslatedText(ru = "", en = "")),
    NIOBIUM_93(TranslatedText(ru="Ниобий", en="Niobium"), TranslatedText(ru = "", en = "")),
    NIOBIUM_94(TranslatedText(ru="Ниобий", en="Niobium"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_94(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_95(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_96(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_97(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_98(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    MOLYBDENUM_99(TranslatedText(ru="Молибден", en="Molybdenum"), TranslatedText(ru = "", en = "")),
    TECHNETIUM_99(TranslatedText(ru="Технеций", en="Technetium"), TranslatedText(ru = "", en = "")),
    TECHNETIUM_100(TranslatedText(ru="Технеций", en="Technetium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_100(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_101(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_102(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RUTHENIUM_103(TranslatedText(ru="Рутений", en="Ruthenium"), TranslatedText(ru = "", en = "")),
    RHODIUM_103(TranslatedText(ru="Родий", en="Rhodium"), TranslatedText(ru = "", en = "")),
    RHODIUM_104(TranslatedText(ru="Родий", en="Rhodium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_104(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_105(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_106(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    PALLADIUM_107(TranslatedText(ru="Палладий", en="Palladium"), TranslatedText(ru = "", en = "")),
    SILVER_107(TranslatedText(ru="Серебро", en="Silver"), TranslatedText(ru = "", en = "")),
    SILVER_108(TranslatedText(ru="Серебро", en="Silver"), TranslatedText(ru = "", en = "")),
    CADMIUM_108(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_109(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_110(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_111(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_112(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_113(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_114(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    CADMIUM_115(TranslatedText(ru="Кадмий", en="Cadmium"), TranslatedText(ru = "", en = "")),
    INDIUM_115(TranslatedText(ru="Индий", en="Indium"), TranslatedText(ru = "", en = "")),
    INDIUM_116(TranslatedText(ru="Индий", en="Indium"), TranslatedText(ru = "", en = "")),
    TIN_116(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_117(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_118(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_119(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_120(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    TIN_121(TranslatedText(ru="Олово", en="Tin"), TranslatedText(ru = "", en = "")),
    ANTIMONY_121(TranslatedText(ru="Сурьма", en="Antimony"), TranslatedText(ru = "", en = "")),
    ANTIMONY_122(TranslatedText(ru="Сурьма", en="Antimony"), TranslatedText(ru = "", en = "")),
    TELLURIUM_122(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_123(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_124(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_125(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_126(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    TELLURIUM_127(TranslatedText(ru="Теллур", en="Tellurium"), TranslatedText(ru = "", en = "")),
    IODINE_127(TranslatedText(ru="Йод", en="Iodine"), TranslatedText(ru = "", en = "")),
    IODINE_128(TranslatedText(ru="Йод", en="Iodine"), TranslatedText(ru = "", en = "")),
    XENON_128(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_129(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_130(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_131(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_132(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    XENON_133(TranslatedText(ru="Ксенон", en="Xenon"), TranslatedText(ru = "", en = "")),
    CESIUM_133(TranslatedText(ru="Цезий", en="Cesium"), TranslatedText(ru = "", en = "")),
    CESIUM_134(TranslatedText(ru="Цезий", en="Cesium"), TranslatedText(ru = "", en = "")),
    BARIUM_134(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_135(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_136(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_137(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_138(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    BARIUM_139(TranslatedText(ru="Барий", en="Barium"), TranslatedText(ru = "", en = "")),
    LANTHANUM_139(TranslatedText(ru="Лантан", en="Lanthanum"), TranslatedText(ru = "", en = "")),
    LANTHANUM_140(TranslatedText(ru="Лантан", en="Lanthanum"), TranslatedText(ru = "", en = "")),
    CERIUM_140(TranslatedText(ru="Церий", en="Cerium"), TranslatedText(ru = "", en = "")),
    CERIUM_141(TranslatedText(ru="Церий", en="Cerium"), TranslatedText(ru = "", en = "")),
    PRASEODYMIUM_141(TranslatedText(ru="Празеодим", en="Praseodymium"), TranslatedText(ru = "", en = "")),
    PRASEODYMIUM_142(TranslatedText(ru="Празеодим", en="Praseodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_142(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_143(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_144(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_145(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_146(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    NEODYMIUM_147(TranslatedText(ru="Неодим", en="Neodymium"), TranslatedText(ru = "", en = "")),
    PROMETHIUM_147(TranslatedText(ru="Прометий", en="Promethium"), TranslatedText(ru = "", en = "")),
    PROMETHIUM_148(TranslatedText(ru="Прометий", en="Promethium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_148(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_149(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_150(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    SAMARIUM_151(TranslatedText(ru="Самарий", en="Samarium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_151(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_152(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_153(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    EUROPIUM_154(TranslatedText(ru="Европий", en="Europium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_154(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_155(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_156(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_157(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_158(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    GADOLINIUM_159(TranslatedText(ru="Гадолиний", en="Gadolinium"), TranslatedText(ru = "", en = "")),
    TERBIUM_159(TranslatedText(ru="Тербий", en="Terbium"), TranslatedText(ru = "", en = "")),
    TERBIUM_160(TranslatedText(ru="Тербий", en="Terbium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_160(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_161(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_162(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_163(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_164(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    DYSPROSIUM_165(TranslatedText(ru="Диспрозий", en="Dysprosium"), TranslatedText(ru = "", en = "")),
    HOLMIUM_165(TranslatedText(ru="Гольмий", en="Holmium"), TranslatedText(ru = "", en = "")),
    HOLMIUM_166(TranslatedText(ru="Гольмий", en="Holmium"), TranslatedText(ru = "", en = "")),
    ERBIUM_166(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_167(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_168(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    ERBIUM_169(TranslatedText(ru="Эрбий", en="Erbium"), TranslatedText(ru = "", en = "")),
    THULIUM_169(TranslatedText(ru="Тулий", en="Thulium"), TranslatedText(ru = "", en = "")),
    THULIUM_170(TranslatedText(ru="Тулий", en="Thulium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_170(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_171(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_172(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_173(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_174(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    YTTERBIUM_175(TranslatedText(ru="Иттербий", en="Ytterbium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_175(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_176(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    LUTETIUM_177(TranslatedText(ru="Лютеций", en="Lutetium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_177(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_178(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_179(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_180(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    HAFNIUM_181(TranslatedText(ru="Гафний", en="Hafnium"), TranslatedText(ru = "", en = "")),
    TANTALUM_181(TranslatedText(ru="Тантал", en="Tantalum"), TranslatedText(ru = "", en = "")),
    TANTALUM_182(TranslatedText(ru="Тантал", en="Tantalum"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_182(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_183(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_184(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    TUNGSTEN_185(TranslatedText(ru="Вольфрам", en="Tungsten"), TranslatedText(ru = "", en = "")),
    RHENIUM_185(TranslatedText(ru="Рений", en="Rhenium"), TranslatedText(ru = "", en = "")),
    RHENIUM_186(TranslatedText(ru="Рений", en="Rhenium"), TranslatedText(ru = "", en = "")),
    OSMIUM_186(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_187(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_188(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_189(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_190(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    OSMIUM_191(TranslatedText(ru="Осмий", en="Osmium"), TranslatedText(ru = "", en = "")),
    IRIDIUM_191(TranslatedText(ru="Иридий", en="Iridium"), TranslatedText(ru = "", en = "")),
    IRIDIUM_192(TranslatedText(ru="Иридий", en="Iridium"), TranslatedText(ru = "", en = "")),
    PLATINUM_192(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_193(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_194(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_195(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_196(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    PLATINUM_197(TranslatedText(ru="Платина", en="Platinum"), TranslatedText(ru = "", en = "")),
    GOLD_197(TranslatedText(ru="Золото", en="Gold"), TranslatedText(ru = "", en = "")),
    GOLD_198(TranslatedText(ru="Золото", en="Gold"), TranslatedText(ru = "", en = "")),
    MERCURY_198(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_199(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_200(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_201(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_202(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    MERCURY_203(TranslatedText(ru="Ртуть", en="Mercury"), TranslatedText(ru = "", en = "")),
    THALLIUM_203(TranslatedText(ru="Таллий", en="Thallium"), TranslatedText(ru = "", en = "")),
    THALLIUM_204(TranslatedText(ru="Таллий", en="Thallium"), TranslatedText(ru = "", en = "")),
    LEAD_204(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_205(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_206(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_207(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_208(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    LEAD_209(TranslatedText(ru="Свинец", en="Lead"), TranslatedText(ru = "", en = "")),
    BISMUTH_209(TranslatedText(ru="Висмут", en="Bismuth"), TranslatedText(ru = "", en = "")),
    BISMUTH_210(TranslatedText(ru="Висмут", en="Bismuth"), TranslatedText(ru = "", en = "")),
    POLONIUM_210(TranslatedText(ru="Полоний", en="Polonium"), TranslatedText(ru = "", en = "")),

    Star(TranslatedText(ru="Звезда", en="Star"), TranslatedText(ru = "", en = ""));

    val details: Details get() = detailsMap[this]!!

    // Символ/лейбл как функция от числа электронов (рефакторинг ионизации, шаг 2B).
    // Для атомов вычисляем заряд (p − electrons); у не-атомов символ/лейбл фиксированы — отдаём литерал.
    fun symbol(electrons: Int): String =
        if (details.type == ElementType.Atom) baseSymbolMap.getValue(this) + chargeSuffix(details.p - electrons)
        else details.symbol

    fun label(electrons: Int): String =
        if (details.type == ElementType.Atom) "${title.en} (${symbol(electrons)})"
        else details.label

    /**
     * Химический символ без масс-индекса и заряда: ²H→H, ¹²C→C, ³He→He. Изотопы схлопываются — этим
     * отличается от [symbol], который заряд показывает, и от baseSymbolMap, который срезает только его.
     * Нужен там, где важен ЭЛЕМЕНТ, а не изотоп: брутто-формула молекулы и подпись в кружке атома.
     */
    val bareSymbol: String get() = bareSymbolMap.getValue(this)

    // Энергетические уровни как функция от числа электронов (рефакторинг ионизации, 2C2b-4).
    // Зависят только от Z, не от N → одна лестница на элемент (atomEnergyLevelsByZ по details.p), общая
    // для всех изотопов и только для атомов. Голым/несуществующим состояниям — пустой список («нельзя ионизировать»).
    fun energyLevels(electrons: Int): List<Float> =
        if (details.type == ElementType.Atom) {
            atomEnergyLevelsByZ[details.p]?.getOrNull(electrons) ?: emptyList()
        } else {
            emptyList()
        }

    // Электроотрицательность по Полингу; null — значения нет (благородные газы) или элемент тяжёлый.
    val electronegativity: Float? get() = electronegativityByZ[details.p]

    // Валентность = сколько ковалентных связей атом может образовать: заполнение оболочек (2/8/8)
    fun valence(electrons: Int): Int {
        if (details.p - electrons > MAX_BONDING_CHARGE) return 0   // если атом теряет больше 1 электрона, то в ковалентную связь он уже вступать не может
        var remaining = electrons   // сколько электронов ещё не разложено по оболочкам
        for (capacity in intArrayOf(2, 8, 8)) {
            if (remaining <= capacity) {
                return when {
                    remaining == capacity -> 0        // полная внешняя оболочка — благородный газ
                    remaining <= 4 -> remaining        // отдаёт/делит валентные электроны
                    else -> 8 - remaining              // достраивает до октета
                }
            }
            remaining -= capacity
        }
        return 0   // electrons > 18 — октет не применим → ковалентно не связывается
    }

    companion object {
        // Каталог Details вынесен в ElementDetails.kt. Делёж на light/heavy/heaviest — ради лимита JVM 64KB на байткод метода.
        private val detailsMap: Map<AtomElement, Details> = elementDetails()

        // Энергетические лестницы ионизации по Z (одна на элемент, общая для изотопов). Опора energyLevels(electrons).
        private val atomEnergyLevelsByZ: Map<Int, List<List<Float>>> = atomEnergyLevelsTable()

        // Электроотрицательность по Z — тоже одна на элемент. Опора electronegativity.
        private val electronegativityByZ: Map<Int, Float> = electronegativityTable()

        // База для symbol(e). Пока каталог не свёрнут — выводим из существующего symbol срезанием
        // (на шаге 2C станет хранимым полем изотопа). Считается один раз.
        private val baseSymbolMap: Map<AtomElement, String> =
            entries.filter { it.details.type == ElementType.Atom }.associateWith { stripCharge(it.details.symbol) }

        // Опора bareSymbol. Без фильтра по типу, в отличие от соседей: подпись нужна и частицам, и звёздам.
        // Считается один раз — спрашивают на каждый атом каждый кадр (рендер) и на каждую формулу.
        private val bareSymbolMap: Map<AtomElement, String> =
            entries.associateWith { element -> element.details.symbol.filter { it.isLetter() } }

        // Базовый символ нуклида без заряда: срезаем хвостовой "⁺" и хвостовые надстрочные цифры заряда.
        // Массовый индекс-префикс ("¹²C") не трогается — он стоит перед буквой элемента.
        private fun stripCharge(symbol: String): String = if (symbol.endsWith("⁺")) symbol.dropLast(1).trimEnd { it in SUPERSCRIPT_DIGITS } else symbol


        /**
         * Потолок заряда, при котором атом ещё ведёт ковалентную химию (см. [AtomElement.valence]).
         * +1 — реальные однозарядные катионы: CH₃⁺, NH₄⁺, H₃O⁺. Двухзарядные молекулярные катионы существуют,
         * но это экзотика — обычно разлетаются кулоновским взрывом, а не связываются; выше +2 ковалентной химии нет.
         */
        private const val MAX_BONDING_CHARGE = 1
    }

}
