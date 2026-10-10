# Domino Print

A Java 17 library for detaching Domino documents, caching code translations and Designer forms, and rendering HTML and plain text without HTTP loopback or native Domino calls during rendering.

**Status: experimental implementation.** The import/cache/render pipeline is implemented and tested with synthetic fixtures. Full offline Formula language compatibility and production layout fidelity remain unfinished. The generated compatibility inventory and release gate expose missing functions and missing live Domino evidence. Passing unit tests does not certify Domino equivalence.

## Build and try it

From this directory in PowerShell, with JDK 17 and Maven:

```powershell
./scripts/verify.ps1
./scripts/run-example.ps1
```

The example writes [HTML](examples/output/order.html), [plain text](examples/output/order.txt), and a release-status report. Its DXL inputs are synthetic examples, not captures from a Domino database. The four library modules have no third-party runtime dependencies. `HtmlReferenceComparator` uses the JDK's `java.desktop` HTML parser; the rest of the runtime uses `java.base` and `java.xml`.

```text
domino-model    Immutable document/item/date/binary/rich-text snapshots; secure DXL import
domino-formula  Formula parser, immutable compiled programs, isolated evaluation, lookup contracts
domino-render   Designer DXL compiler, code/view snapshots, atomic cache, HTML/text renderer
domino-adapter  Thread-confined Domino exporter and view capture; optional typed facade
domino-examples Runnable offline example
```

JARs, source JARs and Javadoc JARs are produced under each module's `target` directory. Use `io.github.benbagwell.dominoprint:domino-adapter:0.1.0-SNAPSHOT` in the consuming project after `mvn install`; it brings the other three library modules transitively. The sample module is not required by a consuming application. Proprietary HCL JARs are supplied by your Domino installation and are never redistributed here.

## Main API

Capture on an initialized Domino thread, using the caller's `lotus.domino.Session`:

```java
var importer = new DominoImporter(session);
DocumentSnapshot data = importer.capture(document);
List<DocumentSnapshot> codeDocuments = importer.captureDocuments(codeCollection);
DominoImporter.DesignCapture designs = importer.captureDesigns(database);
```

The default adapter uses reflection so offline builds do not require Notes.jar. `Domino14Importer` supplies a compile-time typed facade when the `domino-integration` profile is built. The caller owns the session and the document/database/view/collection arguments. The adapter recycles only handles it obtains, including exporters, entries, columns and collection-owned document wrappers. It rejects use on a thread other than its creating thread.

Publish a complete snapshot before using worker threads:

```java
var codes = CodeTranslations.fromDocuments(codeDocuments,
    new CodeTranslations.Mapping("CodeSet", "Code", "Translation"));
var scope = new PrintCache.Scope("server/apps.nsf", "CN=Loader/O=Acme");
var cache = new PrintCache(scope);
cache.refresh(() -> new PrintCache.Bundle(scope, designs.forms(), codes,
    SnapshotLookups.empty(codes), designs.resources(), Map.of(), Map.of()));

var renderer = new PrintRenderer(new FormulaEngine());
RenderResult result = renderer.render(cache, "OrderPrint", data);
String html = result.html();
String text = result.plainText();
```

Replace the sample field names and cache identities with your real names. Configure `SnapshotLookups` with captured views, document/profile snapshots and explicit database aliases when formulas use `@DbLookup`, `@DbColumn`, `@GetDocField` or `@GetProfileField`. `SnapshotLookups.empty` supports the translation extension only. Each view row must retain its native order and its first sorted key; lookup column numbers exclude constant/UI-only columns. `DominoImporter.captureView(view, zone)` captures those values, briefly disables auto-update, and restores the previous setting. Supply the Domino host's zone for partial date/time values.

`@Translate("Status"; Status)` is a library extension for code-set translation. Existing Domino formulas should continue to use their original cached lookup views; the extension is not an HCL function.

Snapshot objects and compiled programs are immutable. A render creates its own field map, diagnostics, assets and output tree. Concurrent renders share one pinned cache version; a failed refresh retains the previous version. Source reads are not an NSF transaction: provide version checks/retry around the whole load when cross-document consistency matters. The adapter detects changes between its structured and raw exports of an individual document.

Cache scopes record source and loading identity. **The consuming application must enforce authorization and partition caches by visibility.** A cache containing data exported by a privileged identity must not be shared with users who cannot read that data. The renderer does not re-check ACLs or Readers fields.

## Current coverage and limits

* Document capture retains items, duplicate item names, flags, text/number/date lists, date ranges, decoded file binaries, structured rich text, structured DXL and a raw note export. Unsupported values, MIME and OLE/composite records are retained as opaque source with diagnostics. Encrypted content is limited to what the exporting identity can access. Rendering an undecoded rich-text construct fails explicitly.
* Formula evaluation supports assignments, lists, arithmetic/comparisons, lazy branches, loops, local field changes, common text/number/date/name functions and cached reads. [The compatibility matrix](docs/COMPATIBILITY.md) enumerates all functions in the bundled Domino 14 inventory, including unimplemented ones. Some implemented functions have incomplete option support; see registry notes and explicit `UNSUPPORTED_OPTION` errors. The release check requires native differential evidence.
* Forms support common paragraphs/fonts, tables, fields, computed text, hide-when formulas, sections, subforms/shared fields, inline/cached pictures, links, attachments, number/date display and keyword aliases. Both outputs use the same computed layout tree. Unsupported layout nodes and formula dependencies block publication or rendering. Exact Notes pagination, CSS fidelity, all DXL attributes, pass-through HTML, embedded views, OLE and arbitrary script execution are not implemented.
* Read-mode rendering preserves saved computed fields, computes display fields, and evaluates missing computed fields on alternate print forms. Editable defaults and composed fields run only in new-document context. Input translation/validation does not run during read rendering. These lifecycle choices still need comparison with your loopback output. Current hide flags combine read/web/print behavior.
* `NoCache` and `ReCache` read the pinned snapshot. They cannot request fresh native data during rendering. Locale, zone, identity attributes and a fixed clock belong to `EvaluationContext`; defaults are US/UTC and should be replaced with application context.
* WebQueryOpen/open-event dependencies require an explicit Java `PreparationHook`. Hooks and registered custom functions must themselves be thread-safe and perform no I/O. The framework cannot enforce that contract for caller-written code.

See [integration and verification](docs/INTEGRATION.md), [the files to supply later](docs/HANDOFF.md), and [the implementation status](docs/IMPLEMENTATION.md).
