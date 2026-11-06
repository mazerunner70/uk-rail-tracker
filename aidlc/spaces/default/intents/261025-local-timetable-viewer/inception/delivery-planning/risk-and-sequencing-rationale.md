# Risk and Sequencing Rationale

The order is the thin slice first. Cockburn's walking skeleton is the heuristic: prove the three containers, the file, and both pages hang together, before splitting more work. There is no second Bolt, so there is no later ranking. No WSJF score was used.

The unit graph in unit-of-work-dependency is one chain: mca-load, then timetable-query, then screen. One Bolt holds all three. That does not reorder the chain. Construction still builds those units one after another.

## DAG check

The architect confirms the Bolt does not reorder the unit graph. Construction still runs mca-load, then timetable-query, then screen. One Bolt bundles all three; the engine's serial unit-major path matches unit-of-work-dependency.

## Why not three Bolts

A Bolt per unit would leave the empty screen and the pair page until the last unit. The first running slice would not show a view. Question 1 asked for that view in the first Bolt.

## Why not a later network page

The network page is the same screen unit as the pair page. A later Bolt cannot finish it without splitting that unit. Question 6 chose to keep one screen unit and to treat both pages as done in this Bolt.

## Top worry

Question 5 chose nothing in particular. The known risks stay in the stories: the file must survive a rebuild, the query must not rewrite it, and a failed ask must not blank a held list. They are checked in this Bolt's demo, not by an extra Bolt in front.

## Sources

requirements, stories, mockups, components, unit-of-work, unit-of-work-dependency, unit-of-work-story-map, contract-summary, team-practices
