# What to provide for the Domino compatibility phase

The offline implementation can be built and exercised now. The following inputs are needed to finish the native integration and determine whether the library reproduces your actual print forms. Anonymize sensitive data while preserving item types, list sizes, flags, formula inputs and layout structure.

## 1. Runtime and execution environment

Provide local absolute paths to `Notes.jar` and `glassfish-corba-omgapi.jar` from the same Domino installation. Do not put the proprietary JARs in a public repository. Also provide:

| Information | Required detail |
| --- | --- |
| Domino | Full release, fix pack/hotfix, OS and architecture |
| Java | Actual Java version and bitness used by the consuming application |
| Execution | Java agent, task/service, XPages, or other host; thread initialization/session creation strategy |
| Regional settings | Locale, date/time separators and patterns, currency, decimal/grouping separators, time zone/DST |
| Identity | Effective loader identity and roles; intended render audience/visibility groups; no passwords or private IDs |
| Dependencies | Relevant NSF replica IDs and explicit server/path/view aliases; accessible representative test databases |

Notes.jar is sufficient for compiling the typed adapter and checking signatures; **a functional Domino runtime and appropriately initialized test session are also required for live evaluations**. An external standalone native runner needs the matching Domino native libraries on its library path. We will use your existing session creation mechanism rather than inventing credentials or initialization.

## 2. Designer form exports

Export representative Designer forms as **structured form DXL**, including every referenced static/computed subform, shared field, image resource, paragraph definition, formula and event. Supply raw note DXL as a preservation/reference supplement when possible. Export with attachments, pictures, OLE and miscellaneous file objects included. Do not send only the flattened HTML.

Include examples covering, where used:

* Editable, computed, computed-for-display and computed-when-composed fields; stored and missing computed values.
* Computed text, hide-when formulas and flags; nested/static/computed subforms; keyword label/value aliases.
* Number/currency/date formatting, multivalue separators, partial dates/times and time zones.
* Tables, merged cells, fonts/colors/margins, images/captions, document links, attachments and sections.
* Pass-through HTML, embedded views, OLE/MIME and other constructs requiring new decoders/renderers.

Provide enabled WebQueryOpen/QueryOpen/PostOpen/OnLoad code, the agents/scripts it calls, and an explanation of each field it modifies. For agents dependent on other NSF data, include those inputs. We need to decide which dependencies become Java preparation hooks and which are irrelevant to print rendering.

## 3. Data and code documents

For each form, provide several anonymized data documents as structured **document DXL** plus raw note DXL, with all accessible attachments and pictures included. Preserve note UNID, sequence and capture timestamps for matching references; anonymized identifiers can be substituted consistently.

Provide code documents and specify:

```text
One document per code: yes/no
Code-set field name:
Code field name:
Translation field name:
Code case sensitivity:
Missing-code behavior:
Duplicate-code behavior:
Multivalue translation display rules:
```

For every `@DbLookup`/`@DbColumn`, include the actual view design and ordered matching entries: first sorted key, all relevant discounted column values, document UNIDs and requested document fields. Include duplicate keys, missing keys, empty values, partial matches, Readers-restricted rows and data exceeding the 64 KB native result boundary if your forms can encounter them. Document/profile reads need matching document/profile snapshots and profile keys. Code documents alone do not reproduce a view's selection formula, order or computed columns.

## 4. Matched reference results

For each data document/form combination, capture the **current loopback response body as UTF-8 HTML**, plus the effective identity, URL/query options, document/form UNIDs and sequences, clock/time zone/locale, preparation-agent behavior and code/view snapshot version used for that capture. Remove secrets from URLs and headers.

Capture the DXL and HTML close together while the fixtures are held stable. A changed document, code document or design makes the comparison ambiguous. Include the current text extraction result if its whitespace/table/list conventions are important. Provide screenshots or PDFs of the reference output when visual layout is important; normalized text alone cannot establish layout fidelity.

Include expected behavior for at least one normal case, one missing/empty/multivalue case, and one conditional/formatting boundary case per important form. Supply native `Session.evaluate` reference cases for heavily used functions and options, including list ordering, errors, date/time types and field mutations. Time-dependent functions require controlled expectations; exact native error mappings need separate classification checks.

## Suggested directory structure

```text
reference-captures/
  manifest.properties
  forms/OrderPrint.dxl
  forms/Part.dxl
  resources/Logo.dxl
  data/order-001.dxl
  raw/order-001-note.dxl
  codes/status-A.dxl
  views/StatusLookup.dxl
  views/StatusLookup-ordered-rows.json
  reference/order-001-OrderPrint.html
  reference/order-001-OrderPrint.txt
  reference/order-001-OrderPrint.png
  preparation/PrepareOrder-agent.txt
```

`reference-captures/` is ignored by the repository to reduce accidental inclusion of captured application data. Provide a manifest mapping each output to the exact inputs. This suggested layout is a handoff format, not an implemented JSON import schema for view rows.

With these inputs, the next phase will compile against your actual Notes.jar, run native differential cases, add missing functions/options used by the forms, implement required layout decoders and hooks, and compare the rendered output. The broader release gate also requires implementing and verifying the remaining applicable functions beyond your initial forms.
