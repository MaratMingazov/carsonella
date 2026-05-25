# Carsonella — лог разработки

Короткие записи в формате «дата — что сделали». Новые записи сверху.

Что есть сейчас и как устроено — в [`README.md`](README.md). Что хотим добавить дальше — в [`ROADMAP.md`](ROADMAP.md).

- **2026-05-25** — CNO-IV (¹⁸O→¹⁹F→¹⁶O, ¹⁹F как промежуточный изотоп цикла). Граница корневого `Environment` очерчена на канве (`Stroke`-окружность по `env.center/radius`); env ужат до играбельного размера. Удалён дубль D⁺+e⁻→D из `AtomPlusAtomToMolecule` — теперь живёт только в `RecombinationReaction`. Ренейм под umbrella α-индуцированных реакций: `alphaReactionResult` → `alphaGammaResult`, `StarAlphaReaction` → `StarAlphaGammaReaction` (готовим место для `alphaProtonResult` + `AlphaProtonReaction`).
- **2026-05-24** — Обогащение электронной структуры (`ion` + `energyLevels`) для He-3, He-4, Li-7, O-16; float-устойчивые `PhotoIonization` / `SpontaneousEmission` через epsilon-сравнение. Рекомбинационные цепочки D⁺→D, ³He²⁺→³He, ⁴He²⁺→⁴He. β⁺-каскады ⁵⁶Ni→⁵⁶Co→⁵⁶Fe (закрытие α-цепочки на стабильном ⁵⁶Fe), ⁴⁸Cr→⁴⁸V→⁴⁸Ti, ⁵²Fe→⁵²Mn→⁵²Cr (V, Mn впервые); β⁺ ³¹S→³¹P. README разнесён на README / ROADMAP / CHANGELOG.
- **2026-05-22** — CNO-III: ¹⁷O→¹⁸F→¹⁸O→¹⁵N (¹⁸F как ПЭТ-изотоп). Branching на ¹⁷O+p + новый кейс замыкания ¹⁸O+p.
- **2026-05-21** — Аннигиляция e⁻+e⁺→2γ (`Annihilation`). Фтор: ¹⁹F через ¹⁵N(α,γ)¹⁹F + umbrella CNO-циклов в README. CNO-II: ¹⁵N→¹⁶O→¹⁷F→¹⁷O→¹⁴N (¹⁷F как короткоживущий фтор). Залатаны дыры α-цепочки на ¹⁶O→²⁰Ne и ²⁴Mg→²⁸Si. `StarEmission` выпускает любого живого ребёнка наружу (вместо хардкода p/e⁻/¹⁶O).
- **2026-05-20** — pp-II ветвь (⁷Be⁴⁺+e⁻→⁷Li³⁺, ⁷Li³⁺+p→2⁴He). Позитрон как `SubAtom` (визуально «+» в красном круге). Изотопы CNO (¹³C, ¹³N, ¹⁴N, ¹⁵N, ¹⁵O) + generic `BetaPlusDecay`. `StarCNOCycle` (CNO-I).
- **2026-05-17** — `StarPPChain` свёрнут из трёх `AtomPlusAtomToMolecule` в одно правило. `StarCarbonBurning` и `StarOxygenBurning` выделены в отдельные правила.
- **2026-05-10** — Симуляция переведена на единый tick: убраны `suspend` и `Channel`; RNG централизован (`World.random`, `seed=1L`).