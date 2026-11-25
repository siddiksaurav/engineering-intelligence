# F16 Spec v1.6 alignment: notes

## Differences from the plan

- Batch 1 (`0b277f3`, `e8f821c`, `f274bbe`): as planned. `ComplexityTable.complexityOf(depths, problemsAffectEachOther, mistakesStayHidden, reachesAcrossProjects)` writes "two or more" without a number constant (more than one), so `SpecNumbersTest` has nothing to object to. The fact of a problem reads `Problem name: answer exists, "quote"`. Two v1 enum assertions left `PromptLibraryTest` with the v1 enums; the v1 files and their pinned hashes stay. The implementer wrote tests first but ran only the green run.
