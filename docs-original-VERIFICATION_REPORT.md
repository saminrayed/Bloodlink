# Verification Report

Date: 2026-08-06

## Passed checks

- Java 21 production-source compilation using `javac --release 21` and local JavaFX API stubs
- Java 21 test-source compilation using local JUnit API stubs
- Core logic smoke execution:
  - O-negative compatibility
  - AB-positive restriction
  - badge threshold behavior
  - exact 56-day eligibility boundary
  - Bangladeshi phone validation
  - strong-password validation
- XML parsing for `pom.xml` and all FXML files
- FXML controller existence
- Every FXML `fx:id` mapped to its controller source
- Every FXML event handler mapped to a controller method
- Every FXML stylesheet reference resolves
- Java package declarations match filesystem paths
- Runtime and convenience SQL schema copies are identical
- No unfinished implementation markers in active project files

Static checker output:

```text
BloodLink verification PASSED
 - 53 production Java files
 - 4 test Java files
 - 5 FXML screens
```

Run the checker again with:

```bash
python scripts/verify-project.py
```

## Not executed in the generation environment

- Maven dependency resolution
- `mvn test`
- JavaFX visual launch
- MySQL schema execution and JDBC integration

Those steps need Maven, a graphical JavaFX runtime, and a reachable MySQL server on the target computer. The setup and manual verification procedures are included in `docs/SETUP.md` and `docs/TEST_PLAN.md`.
