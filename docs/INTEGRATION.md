# Integration and verification

## Offline builds

`scripts/verify.ps1` runs Maven verification with sources/Javadocs and stores downloaded build dependencies in this workspace's `.m2/repository`. No Domino runtime is needed. The unit tests exercise detached capture, immutability, formula parsing/evaluation, independent reference mismatch reporting, lookup behavior, form lifecycle, resource failures, ownership/recycling and concurrent cache refresh/rendering.

`scripts/run-example.ps1` renders bundled synthetic fixtures and regenerates the function matrix/status report. `scripts/run-example.ps1 -RequireReady` is deliberately unsuccessful until broad compatibility is implemented and live reference evidence has been supplied; a successful example render is not a production release gate.

## Compile against the installed Domino API

After supplying the two JAR paths:

```powershell
mvn -Pdomino-integration '-Dnotes.jar=C:\Domino\Notes.jar' '-Dcorba.jar=C:\Domino\jvm\lib\ext\glassfish-corba-omgapi.jar' verify
```

Use your actual paths. This includes the typed facade and `DominoApiContractTest`; it checks signatures without creating a session. The profile has **not yet been compiled in this workspace**, because those JARs are unavailable. Compilation/signature checks alone do not exercise the exporter, native date conversion or view iterator on a server.

Do not share `Session`, `Document`, `View`, or any other native Domino wrapper with worker threads. Initialize and terminate Domino thread/session resources according to your hosting application. Import on the initialized owning thread, publish detached snapshots, and render on ordinary worker threads. The library never initializes native threads or closes caller-owned sessions.

## Capture and cache loading

1. Export/capture relevant data, code documents and the design elements under the intended loader identity.
2. Preserve and inspect `DesignCapture.diagnostics()` and each document's diagnostics. Opaque content is recoverable in source DXL, but not necessarily printable.
3. Capture every lookup view required by formulas using `captureView(view, hostZone)`. Construct `SnapshotLookups` with a current location, aliases, document/profile maps and translations. `documentKey` and `profileKey` supply its map key format. Explicit aliases are needed for different spellings of a database location.
4. Port preparation dependencies into named hooks such as `OrderPrint:webqueryopen`. A no-op hook is appropriate only after checking the real event has no effect on output.
5. Publish a `PrintCache.Bundle` only when the whole load is consistent. Refresh is synchronized and publication is atomic, but NSF reads across multiple notes are not transactional. Avoid modifying the source fixtures during verification; applications needing stronger guarantees should capture version metadata and retry a changed load.
6. Supply `EvaluationContext` locale, zone, identity/roles/database context and a fixed instant. Use `withAttributes` with a merged map; it replaces the previous attributes. Set numeric `isnewdoc` only for genuinely new-document cases.

Loading identity is retained in imported metadata; the cache's explicit scope must agree with your application policy. There is no automatic per-user ACL filtering after capture. Unsupported external operations fail instead of triggering I/O. Caller hooks/custom functions can violate that guarantee unless you enforce their contract.

## Live formula comparison

`FormulaReferenceVerifier` accepts case ID, function, coverage scenario, formula source and offline context. An independently supplied `Oracle` returns the native typed result and working fields. Type, list order, value and field mutations are compared. Full datetimes compare instants; partial dates/times compare components. Exceptions are failures. Error messages/codes are not automatically declared equivalent. Numbers currently require exact equality; evaluate any numeric tolerance policy before expanding the verifier.

`DominoFormulaReference` is an optional typed oracle using `Session.evaluate`. Its document factory must supply a **fresh disposable unsaved test document for each case**, populated to match the offline context. The oracle owns/recycles that returned document and never saves it. Use an isolated test NSF and review formulas used by the harness. Configure the native environment to match the offline clock/locale/identity where applicable; time-dependent and context-dependent functions need dedicated reference cases. Native exceptions are reported as failed cases.

```java
var verifier = new FormulaReferenceVerifier(engine);
var case1 = new FormulaReferenceVerifier.Case("abs-negative", "Abs", "scalar",
    "@Abs(-2)", offlineContext);
var oracle = new DominoFormulaReference(session, testCase -> {
    // Create a disposable fixture with precisely the testCase.context() fields.
    return buildFreshFixture(testDatabase, testCase);
});
var report = verifier.verify("14.0.1", List.of(case1), oracle);
report.requireMatching();
var gate = new CompatibilityCatalog(engine.functions()).assess(report.evidence());
gate.requireReady(); // One scalar case is insufficient for broad release.
```

`buildFreshFixture` is application-provided fixture preparation, not a library method. The harness's coverage labels and environment metadata are audit inputs; review that each case actually exercises its claimed boundary/options/errors. Do not count synthetic JUnit oracle stubs as native evidence. `CompatibilityCatalog` requires scalar/list/boundary/errors/options evidence for each applicable function, no failed cases and an implementation. Export and retain full reports with the exact Domino build and fixture versions.

## HTML comparison

`HtmlReferenceComparator.compare(generatedHtml, capturedHtml)` reports exact markup equality and normalized visible-text equality. It never runs scripts or downloads resources. It is a content smoke check using the JDK HTML parser, not a browser/CSS/layout engine. Inspect table structure, field formatting, image placement, fonts, hide rules and print output separately against the reference. Different markup can be acceptable, but text agreement does not establish visual fidelity.

## Sources used for implementation

* [HCL Domino 14 release information](https://help.hcl-software.com/domino/14.0.0/admin/wn_140.html)
* [HCL Formula function inventory](https://help.hcl-software.com/dom_designer/14.0.0/basic/H_FUNCTIONS_LISTED.html)
* [HCL Java DateTime API](https://help.hcl-software.com/dom_designer/14.0.0/basic/H_NOTESDATETIME_CLASS_JAVA.html)
* [HCL Java ViewColumn API](https://help.hcl-software.com/dom_designer/14.0.0/basic/H_NOTESVIEWCOLUMN_CLASS_JAVA.html)

The exact installation, locale conventions, undocumented API edge cases and supported DXL schema must still be checked against the supplied Domino runtime and captures.
