# Carsonella

Kotlin Multiplatform + Compose Multiplatform приложение-симулятор физики и химии. Пользователь видит «вселенную» на Canvas, перетаскивает из палитры частицы, атомы, молекулы и «реакторы» — и наблюдает реакции в реальном времени.

## Цель проекта

1. **Изучить, как устроен мир.** Как из элементарных частиц образуются атомы, из атомов — молекулы, из молекул — более сложные соединения, и как в итоге это приводит к появлению жизни и эволюции.
2. **Воплотить это в виде небольшой игры-стратегии.** С одной стороны эволюция идёт своим чередом — можно просто наблюдать. С другой — игрок может вмешиваться в этот мир (пока не решено как именно: возможно копить ресурсы, строить какие-то объекты) и направлять ход эволюции.

## Визуальный стиль и настроение

Целевая эстетика — **созерцательная, расслабляющая, «медитативный микромир»** в духе *Osmos* и *Eufloria*. Игра, в которую играют не спеша, любуясь происходящим.

- Тёмный градиентный фон, ощущение бесконечного космоса.
- Частицы — полупрозрачные светящиеся сферы с мягким halo (радиальные градиенты, аддитивный blend, лёгкий bloom).
- За движущимися частицами тянется затухающий световой след (trails).
- Звёздные реакции — не «вспышка экшена», а тихий всплеск света.
- Минимум UI на канве, ambient-музыка / тишина, никакого хардкора и спешки.

Опционально (космическая фаза): реалистичные цвета звёзд по температуре и туманности на фоне в духе снимков Hubble/JWST.

## Таргеты

- Android
- iOS
- JVM Desktop
- WasmJs (браузер)
- `server` — Ktor (заготовка)

## Модули

- **`shared`** — доменная модель: элементы, сущности, поведения, правила реакций. Общая для всех платформ.
- **`composeApp`** — UI и игровой мир (`World`, рендереры, панели, drag&drop).
- **`server`** — Ktor-заготовка.
- **`iosApp`** — XCode-обёртка для iOS.

## Архитектура домена (`shared/.../chemistry/`)

### Сущности

`Entity<State>` — общий интерфейс. Поведения подмешиваются через делегирование (`behavior/*.kt`):

- `DeathNotifiable` — сообщить миру о смерти
- `NeighborsAware` — получать актуальный список соседей
- `ReactionRequester` — отправлять запрос на реакцию в мир
- `EnvironmentAware` — знать своё окружение
- `LogWritable` — писать в общий лог

Конкретные классы: `SubAtom`, `Atom`, `Molecule`, `Star`, `SpaceModule`, `RecombinationModule`. У каждой синхронный `step()`, который вызывается миром раз за tick: двигает сущность, считает силы, при необходимости шлёт `requestReaction(...)`. Никаких собственных корутин или `delay` — мир — единственное «время».

### Элементы

`Element` (enum) + `Details` — большая таблица элементов с параметрами:

- масса, число электронов/протонов/нейтронов, радиус
- `energyLevels` — энергетические уровни (для фотоионизации и спонтанной эмиссии)
- `ion` — ион, который получится при потере электрона
- `recombinationElement` — что получится при захвате электрона
- `alphaReactionResult` — продукт альфа-захвата (нуклеосинтез в звёздах)
- `energyBondDissociation` / `dissociationElements` — для фотодиссоциации молекул

### Окружение

`IEnvironment` — у каждой частицы есть «среда» (центр, радиус, `TemperatureMode.Space | Star`, дети). Любая частица сама может быть средой для других: звезда «содержит» свои протоны/электроны, и альфа-синтез работает только внутри звезды.

### Реакции (`chemistry/chemical_reaction/`)

`ChemicalReactionResolver` хранит список `ReactionRule` и для каждого запроса выбирает правило с максимальным `weight()` (рандом среди равных по весу). Каждое правило возвращает `ReactionOutcome { consumed, spawn, updateState, description }`.

#### Что моделируется сейчас (физическая цепочка)

```
звезда (StarEmission)
    → нуклеосинтез внутри звезды:
        · pp-I (StarPPChain): p+p→D⁺, D⁺+p→³He²⁺, ³He²⁺+³He²⁺→⁴He²⁺+2p
        · pp-II (StarAlphaReaction + StarPPChain): ³He²⁺+⁴He²⁺→⁷Be⁴⁺, ⁷Be⁴⁺+e⁻→⁷Li³⁺, ⁷Li³⁺+p→2 ⁴He²⁺
        · альфа-захват (StarAlphaReaction): ⁴He→⁸Be→¹²C→¹⁶O→…→⁵⁶Ni →(β⁺)→ ⁵⁶Co →(β⁺)→ ⁵⁶Fe (стабильная вершина железного пика), плюс боковая ¹⁵N+α→¹⁹F
        · CNO-I (StarCNOCycle + BetaPlusDecay): ¹²C+p→¹³N, ¹³N→¹³C+e⁺, ¹³C+p→¹⁴N, ¹⁴N+p→¹⁵O, ¹⁵O→¹⁵N+e⁺, ¹⁵N+p→¹²C+⁴He
        · CNO-II (StarCNOCycle + BetaPlusDecay): утечка ¹⁵N+p→¹⁶O, ¹⁶O+p→¹⁷F, ¹⁷F→¹⁷O+e⁺, ¹⁷O+p→¹⁴N+⁴He
        · CNO-III (StarCNOCycle + BetaPlusDecay): утечка ¹⁷O+p→¹⁸F, ¹⁸F→¹⁸O+e⁺, ¹⁸O+p→¹⁵N+⁴He
        · горение углерода (StarCarbonBurning): ¹²C+¹²C → ²⁰Ne/²³Na/²⁴Mg
        · горение кислорода (StarOxygenBurning): ¹⁶O+¹⁶O → ²⁸Si/³¹P/³¹S
    → излучение в космос (StarEmission выбрасывает случайного живого ребёнка звезды наружу)
    → фотоионизация / рекомбинация (PhotoIonization, RecombinationReaction, SpontaneousEmission)
    → аннигиляция позитронов от β⁺-распада с электронами (Annihilation: e⁻ + e⁺ → 2γ)
    → молекулообразование (AtomPlusAtomToMolecule: H+H→H₂, O+O→O₂, O+H₂→H₂O, …)
    → распад молекул фотонами (PhotoDissociation)
```

Это уже физически довольно правдоподобная база — от субатомных частиц через звёздный нуклеосинтез до простых молекул.

**Заметки к цепочке:**

- **`StarEmission` совмещает две роли** через `if/else` (генерация топлива vs выброс наружу). Можно при желании разнести на `StarFuelGeneration` + `StarOutflow`.
- **Внутренний цикл «атом ↔ возбуждённый атом ↔ ион»**: `PhotoIonization` (поглощение) ↔ `SpontaneousEmission` (сброс) и `PhotoIonization` (отрыв) ↔ `RecombinationReaction`. В консоли логов это видно как постоянное «туда-сюда».

### Чего из реальной физики ещё нет

Карта дальнейших шагов по физике (зонтичные термины с разбором по семействам — CNO-циклы, α-индуцированные реакции, радиоактивный распад, нестабильные интермедиаты, BBN, spallation, s/r-процессы, нейтрино, гравитация и т.д.) вынесена в [`ROADMAP.md`](ROADMAP.md).

## Игровой цикл (`composeApp/.../world/`)

`World` владеет:

- `entities` (`SnapshotStateList<Entity<*>>`)
- палитрой элементов
- корневым `Environment` (радиус 10000)
- `_pendingRequests: MutableList<ReactionRequest>` — буфер запросов реакций
- `random: Random(seed=1L)` — единый сидированный RNG для всей симуляции
- `EntityGenerator`, `ChemicalReactionResolver`

Один tick (`tickMs = 16L`) внутри единственного `_scope.launch`:

1. **Step phase** — `entities.toList().forEach { it.step() }`. Каждая сущность синхронно делает свой ход; может дописать запрос в `_pendingRequests`.
2. **Resolve phase** — берётся снимок `_pendingRequests`, список очищается, каждый запрос проходит через `ChemicalReactionResolver.resolve`, результат применяется (`consumed.destroy()`, `spawn()`, `updateState()`).
3. `delay(tickMs)` — ждём следующий кадр.

Никаких `Mutex`, `Channel` или `suspend` в самой симуляции — tick единственный писатель мира.

## UI

- `App.kt` — корень: `Row { LeftPanel | RightPanel }`.
- `LeftPanel` — палитра элементов (drag source) + панель выбранной сущности.
- `RightPanel` — Canvas с миром:
  - hit-test, hover/select частиц
  - WASD / стрелки двигают выбранную частицу импульсом
  - Space «стреляет» копией выделенной частицы в направлении курсора
  - снизу — консоль логов реакций
- `EntityRenderer` диспетчеризует отрисовку по типу состояния; звезда — пульсирующий радиальный градиент.
- `DragAndDrop.kt` — дроп с палитры на канву → `entityGenerator.createEntity(...)`.

## Структура каталогов

```
shared/src/commonMain/kotlin/maratmingazovr/ai/carsonella/
├── Constants.kt, Geomtry.kt, IEnvironment.kt, Platform.kt
└── chemistry/
    ├── Entity.kt              # interface, EntityState, Element, Details
    ├── SubAtom.kt, Atom.kt, Molecule.kt
    ├── Star.kt, SpaceModule.kt (+ RecombinationModule)
    ├── behavior/              # делегаты поведений
    └── chemical_reaction/
        ├── ChemicalReaction.kt
        └── rules/             # все ReactionRule

composeApp/src/commonMain/kotlin/maratmingazovr/ai/carsonella/
├── App.kt, LeftPanel.kt, RightPanel.kt, DragAndDrop.kt
└── world/
    ├── World.kt
    ├── generators/   # IdGenerator, EntityGenerator
    └── renderers/    # EntityRenderer, SubAtomRenderer
```

## Технические TODO (по убыванию приоритета)

1. **SpatialGrid (`Grid2D.kt`) — `getNeighbors()` сейчас O(N²).**
   Каждая сущность фильтрует весь список соседей. Файл уже лежит — нужно оживить, перестраивать в начале tick'а, и `getNeighbors()` пустить через него. Главный пункт по производительности — без него масштабирование закончится быстро.

2. **UI пишет в мир мимо tick'а.** Drag&drop спавн (`App.kt`) + Space-«выстрел» (`RightPanel.kt`) + WASD-импульс (`World.applyForceToEntity`) сейчас правят `entities`/`environment.children`/`state.value` напрямую. Race в наших масштабах редко стреляет (Wasm однопоточный), но архитектурно — pending-список с применением в фазе Cleanup финализирует «tick — единственный писатель».

3. **Тест на детерминированность.** `World.random` сидируется константой `_seed = 1L`, инфраструктура готова — осталось написать `SharedCommonTest.kt`, который запускает мир дважды от одинакового стартового состояния и сверяет последовательность реакций.

4. **`weight()` у всех правил возвращает `0f`.** Резолвер выбирает случайное правило среди подходящих. Задумка «приоритизация по вероятности» не работает — либо запустить, либо честно признать, что выбор просто случайный.

5. **`Entity` дефолтно бросает `Not Supported` на методы среды.** Type-smell: не каждая Entity — среда. Лучше отдельный `EnvironmentProvider` интерфейс, реализуемый только теми, кто реально среда (`Star`, `SpaceModule`).

6. **`Element` как enum + большой `Details`-каталог.** Сейчас норм, но когда дойдёт до органики (десятки/сотни молекул), enum станет узким горлом. Держать в уме переход на `data class Element` + регистр.

7. **`EntityState` с `var`-полями, но используется как immutable через `copyWith`.** Одно или другое — иначе путает читателя. Лучше сделать поля `val`.

8. **Мелочи:**
   - `id = ""` у нескольких правил, `id = "PhotoIonization"` у двух разных правил (`PhotoIonization` и `StarEmission`).
   - Смешение стилей в `Element` enum: `PHOTON` (всекапс) vs `Proton` / `Star` / `Ni` (CamelCase / короткое).

9. **Пустой `server` модуль.** Если планируется мультиплеер/сохранения — это отдельный большой проект. Если нет — можно убрать из `settings.gradle.kts`, чтобы не мозолил.

## Лог разработки

Лог изменений вынесен в [`CHANGELOG.md`](CHANGELOG.md).

