# Corrections Log

## Restoring missing maven-wrapper.properties extension
What the AI did: Discovered that `mvnw.cmd` failed because `.mvn/wrapper/maven-wrapper.properties` was missing on disk, but found `.mvn/wrapper/maven-wrapper.properties.txt` was present in the directory.
How I noticed: Inspected the workspace files with `Get-ChildItem -Recurse .mvn` and checked the contents of `.mvn/wrapper/maven-wrapper.properties.txt`.
What I did: Copied `.mvn/wrapper/maven-wrapper.properties.txt` to `.mvn/wrapper/maven-wrapper.properties` so `mvnw.cmd` could run normally without requiring Maven on system PATH.


## 20-Hour cancellation refund threshold check
What the AI did: Examined whether `RefundCalculator.java` or `BookingService.java` had a bug causing 50% refund at 20 hours instead of a full or 90% refund.
How I noticed: Read `RefundCalculator.java`, `MyBookings.jsx`, and `RefundCalculatorTest.java`. Found that the centre cancellation policy explicitly requires cancellation at least 24 hours prior to class start to receive a 90% refund; otherwise 50% is returned.
What I did: Confirmed the calculation is working as intended under current business policy. Did not alter the threshold in code, avoiding breaking business rules, and documented the policy clarification in `STATUS.md`.


## Correcting paisa truncation and test assertion in RefundCalculator
What the AI did: Investigated why refunds were off by 1 paisa compared to the payment gateway report. Found `RefundCalculator.java` was using `(long) (refund * 100) / 100.0` (truncation) rather than nearest-paisa rounding, and `RefundCalculatorTest.java` had an assertion hardcoded to 299.99 for 333.33 * 0.9.
How I noticed: Compared the historical database values in `V3__october_history.sql` against `docs/gateway-refunds-2026-10.csv`. Found 20 entries differed by exactly 1 paisa (e.g. 121.90 vs 121.91, 299.99 vs 300.00).
What I did: Updated `RefundCalculator.java` to use `Math.round(refund * 100.0) / 100.0` and corrected `RefundCalculatorTest.java` line 13 to expect 300.00. Added Flyway migration `V4__front_desk_fixes.sql` to reconcile the historical October payment rows so monthly total matches 6853.50.


## Postgres JDBC timestamp mapping via Db.ts
What the AI did: In initial controller updates for `AdminController.java` and `MeController.java`, passed `java.time.Instant` objects directly to `jdbc.param(...)`.
How I noticed: Running `mvnw.cmd test` failed with `org.postgresql.util.PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant`.
What I did: Wrapped the `Instant` arguments using `Db.ts(...)` as defined in `Db.java`, converting them to `OffsetDateTime` compatible with the PostgreSQL JDBC driver.

---

## Integrating Wave 2 Ticket T4 (Settlement) into PRD
What the AI did: Examined `TICKETS-2.md` containing the Wave 2 ticket requirement for the Settlement module (`modules/settlement/`).
How I noticed: User alerted that `TICKETS-2.md` was pulled into the repository.
What I did: Inspected `TICKETS-2.md`, analyzed `chaos/src/test/java/com/slotbook/chaos/SettlementChaosRun.java` and `chaos.cmd`, and updated `PRD.md` with complete architectural, message ingestion, calculation (70% net payout), batching lifecycle, schema (`V300_1__settlement.sql`), and API specifications for T4.
