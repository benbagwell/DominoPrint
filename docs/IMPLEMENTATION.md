# Implementation status

This repository implements an experimental vertical slice of the requested architecture. It does not yet fulfill full applicable offline Formula language parity or certify your real Designer print forms.

| Area | Implemented now | Required before production compatibility |
| --- | --- | --- |
| Detached document | Immutable values/binaries/rich-text tree, raw/structured source, diagnostics | Native capture fixtures; additional MIME/OLE/composite decoders; type edge cases |
| Code cache | Configurable one-document-per-code mapping, multivalue translations, duplicate detection | Real mapping and missing-code policy |
| Lookups | Ordered cached views, aliases, doc/profile reads, limits/options | Native collation/matching/date/index comparisons; complete dependency inventory |
| Formula runtime | Parser, lazy controls/loops, isolated working fields, broad common function set | All remaining applicable functions, complete options, native error semantics and differential evidence |
| Form compilation | Common DXL tree, formulas/dependencies, subforms/shared fields, audit | Real form schema fixtures, missing layout constructs and full attribute fidelity |
| Rendering | HTML/text from one computed tree, cached binaries, display/lifecycle/hide rules | Matched loopback HTML, visual checks, script hooks and application-specific behavior |
| Thread safety | Immutable snapshots/programs, private render state, pinned atomic refresh | Caller hook/extension contract and consuming app's authorization/capture consistency |
| Native adapter | Reflective import, optional typed API/oracle/signature tests | Actual Notes.jar compilation plus initialized-runtime tests |

Run the example to regenerate `examples/output/release-status.txt` for exact missing-function names/counts. `docs/COMPATIBILITY.md` is generated from the function inventory and registry. Functions marked implemented are explicitly unverified; their option limitations are in `FunctionRegistry` specifications and method errors. The broader release check remains red.

The bundled tests are regression checks for behavior implemented here. They are not tests against Domino; the sample DXL and reference-oracle stubs are synthetic. No live database, native server or existing loopback output was available during this implementation.

The next implementation phase should use the [handoff specification](HANDOFF.md) to drive native corrections, fixture-based render comparisons and new function/layout coverage. The inventory prevents those remaining requirements from silently disappearing from scope.
