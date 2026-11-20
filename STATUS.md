# Front Desk Issues Status Log (T1)

This document tracks every issue reported by the front desk (`T1.1` – `T1.7`), detailing the root cause found, the action taken (or reason for taking no action), and verification evidence.

---

## T1.1: Timezone Mismatch for 5:00 AM Class & Today's Tab

- **What was found**:
  - The centre operates in `Asia/Kolkata` (UTC+05:30) per `slotbook.centre-zone` in `application.yml`.
  - A Monday 5:00 AM IST class is UTC Sunday 23:30:00Z.
  - `SlotController.java` was using `Clock clock` (which is configured as UTC in `ClockConfig`) to evaluate `LocalDate.now(clock)` and `from.atStartOfDay(clock.getZone())`. Monday's date range in UTC started at `2026-11-16T00:00:00Z` (Monday 5:30 AM IST), completely excluding the 5:00 AM batch from Monday's "Today" view and including tomorrow's 5:00 AM batch.
  - In `BookingController.java`, `mine()` computed each booking's grouping date via `slot.startsAt().atZone(clock.getZone()).toLocalDate()`, converting Monday 5:00 AM IST (Sunday 23:30 UTC) into Sunday.
- **What was done**:
  - Injected `CentreClock centre` into `SlotController.java` and `BookingController.java`.
  - In `SlotController.java`, changed date boundaries to `from.atStartOfDay(centre.zone()).toInstant()` and `to.plusDays(1).atStartOfDay(centre.zone()).toInstant()`, and default `from` to `LocalDate.ofInstant(centre.now(), centre.zone())`.
  - In `BookingController.java`, grouped bookings by `slot.startsAt().atZone(centre.zone()).toLocalDate()`.
- **How we know it is right**:
  - Automated integration test `FrontDeskTicketsTest.t1_1_morningYogaSlotInCentreTimezone` verified that:
    1. A slot scheduled for 5:00 AM IST on Monday is returned under `/api/slots?date=<Monday>`.
    2. The 5:00 AM slot is NOT included in Sunday's `/api/slots?date=<Sunday>`.
    3. `/api/bookings/mine` groups the booking under Monday's date string.

---

## T1.2: Notification Timestamps Formatting

- **What was found**:
  - In `web/src/pages/Notifications.jsx`, line 23 was rendering timestamps with `new Date(n.createdAt).toLocaleString('en-IN')`, which displays seconds and cluttered formatting (e.g., `20/11/2026, 12:48:08 pm`).
- **What was done**:
  - Updated `web/src/pages/Notifications.jsx` to explicitly format notification timestamps using:
    ```javascript
    new Date(n.createdAt).toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    })
    ```
- **How we know it is right**:
  - Notification items now render clean, readable times (e.g., `20 Nov, 12:48 pm`) with seconds eliminated.

---

## T1.3: Concurrency Race Condition & Double Booking on Slots

- **What was found**:
  - `BookingService.java` performed separate, unsynchronized read-checks (`bookings.hasConfirmed` and `bookings.countConfirmed`) before calling `gateway.charge(memberId, ...)` and `bookings.insert(...)`.
  - Concurrent requests for the same slot (or repeated rapid clicks by the same user) checked capacity and existing bookings before any insert occurred, allowing more than 20 bookings on a 20-seat slot and duplicate bookings for the same member.
  - The database lacked a uniqueness constraint on active bookings for `(slot_id, member_id)`.
  - `V2__seed.sql` specifically seeded duplicates (members 5 and 9) and overbooked Saturday's chess club (22 bookings for 20 seats).
- **What was done**:
  - Added slot-level locking in `BookingService.java` using a `ConcurrentHashMap` of locks keyed by `slotId` to serialize capacity check, payment, and insertion per slot.
  - Added a defensive catch block in `BookingService.java` that triggers `gateway.refund()` if booking insertion fails after charging.
  - Added Flyway migration `V4__front_desk_fixes.sql` that:
    1. Deduplicates existing seed data by marking redundant duplicate confirmed bookings as `CANCELLED`.
    2. Enforces a PostgreSQL partial unique index: `create unique index bookings_member_slot_unique on bookings (slot_id, member_id) where status = 'CONFIRMED';`.
- **How we know it is right**:
  - Unit/integration test `FrontDeskTicketsTest.t1_3_duplicateBookingRejected` verifies that attempting to book a second seat in the same slot returns `409 Conflict` with `ALREADY_BOOKED`.
  - Database schema rejects duplicate confirmed bookings at the constraint level.

---

## T1.4: 20-Hour Cancellation Refund Inquiry

- **What was found**:
  - The published and coded cancellation policy (stated in `RefundCalculator.java`, `MyBookings.jsx`, and backend tests) is:
    - Cancellations made $\ge 24$ hours prior to class start: **90% refund**.
    - Cancellations made $< 24$ hours prior to class start: **50% refund**.
  - The customer cancelled their booking **20 hours** prior to class start. Because 20 hours is less than the 24-hour threshold, receiving a 50% refund is the correct and intended behavior under centre policy.
- **What was done**:
  - Did nothing in code. Modifying the cutoff to 20 hours would violate the business policy and break the contract visible to users in `MyBookings.jsx` ("You get 90% back if you cancel at least 24 hours before the class, otherwise 50%.").
  - Recommended front-desk customer communication to clarify the 24-hour rule to the member.
- **How we know it is right**:
  - Verified `RefundCalculatorTest.java`, `BookingService.java`, and UI text in `MyBookings.jsx`; all consistently specify and enforce the 24-hour boundary.

---

## T1.5: "My Plan" Error Screen for New Members

- **What was found**:
  - In `MeController.java`:
    `Plan plan = members.findPlan(member.planId()).orElseThrow();`
  - In `AuthController.java` / `MemberRepository.java`, new signups were inserted with `plan_id = null`.
  - In `V2__seed.sql`, new members who signed up through the signup page (e.g. Rohan Gupta, Kavya Reddy, Imran Sheikh) were seeded with `plan_id = null`.
  - When new members navigated to "My Plan", `findPlan(null)` threw `NoSuchElementException`, causing a 500 error on `/api/me/plan`.
- **What was done**:
  - In `MemberRepository.java`, updated `insert` to assign the default plan (`select id from plans where is_default = true limit 1`) upon signup, and added `findDefaultPlan()`.
  - In `MeController.java`, added a fallback to `members.findDefaultPlan()` if `member.planId()` is null or not found.
  - In Flyway migration `V4__front_desk_fixes.sql`, backfilled existing members who had null `plan_id` to the default plan.
- **How we know it is right**:
  - Test `FrontDeskTicketsTest.t1_5_newMemberPlanAccessible` registers a fresh member through `/api/auth/signup` and immediately queries `/api/me/plan`, asserting a `200 OK` response with plan `"Starter"` and 0 classes used.

---

## T1.6: Parent Bookings for Children

- **What was found**:
  - The domain model enforces a 1:1 relationship between a `member` record and a `booking` seat (`member_id` foreign key and unique confirmed index).
  - Booking on behalf of children or dependents cannot be accomplished under a single account without either:
    1. Allowing parents to register dependent child profiles under their parent account, or
    2. Modifying the booking model to support an `attendee_name` field per seat.
- **What was done**:
  - Did not implement an ad-hoc schema hack in core to prevent breaking module contracts (such as `waitlist`, `attendance`, and `reminders`).
  - Proposed architectural recommendation:
    - **Option A (Multi-Profile)**: Add a `dependents` table (`id`, `parent_id`, `name`, `dob`) and reference `dependent_id` optionally in `bookings`.
    - **Option B (Immediate Workaround)**: Have parents register a separate member account for their child (e.g. `child.parent@...`) using the Starter plan.
- **How we know it is right**:
  - Evaluated against `CONTRACT.md` and module independence constraints.

---

## T1.7: 1-Paisa Rounding Discrepancy & Gateway Refund Reconciliation

- **What was found**:
  - In `RefundCalculator.java`, refunds were computed as:
    `return (long) (refund * 100) / 100.0;`
    This truncated rather than rounded to the nearest paisa. For example:
    - `135.45 * 0.9 = 121.905` was truncated to `121.90` instead of `121.91`.
    - `333.33 * 0.9 = 299.997` was truncated to `299.99` instead of `300.00`.
    - `129.95 * 0.9 = 116.955` was truncated to `116.95` instead of `116.96`.
    - `135.45 * 0.5 = 67.725` was truncated to `67.72` instead of `67.73`.
  - In `V3__october_history.sql`, the historical payments were seeded with truncated values (20 of the 30 entries were off by 1 paisa, resulting in `6853.30` instead of `6853.50`).
  - In `AdminController.java`:
    - The date range filter used `ZoneOffset.UTC` instead of `centre.zone()`.
    - Total was summed using floating point and truncated with `Math.floor(total * 100) / 100`.
- **What was done**:
  - Fixed `RefundCalculator.java` to round to the nearest paisa: `Math.round(refund * 100.0) / 100.0`.
  - Updated `RefundCalculatorTest.java` test expectation for 333.33 early refund from 299.99 to 300.00.
  - In `AdminController.java`, updated month bounds to use `centre.zone()`, wrapped parameters with `Db.ts(...)`, and rounded the total using `Math.round(total * 100.0) / 100.0`.
  - In Flyway migration `V4__front_desk_fixes.sql`, updated the 20 historical October refund payment amounts to match `docs/gateway-refunds-2026-10.csv`.
- **How we know it is right**:
  - Test `FrontDeskTicketsTest.t1_7_reconciledOctoberRefundsReport` calls `/api/admin/reports/refunds?month=2026-10` and asserts that the count is exactly 30 and the total is exactly `6853.50 INR`, fully reconciling with `docs/gateway-refunds-2026-10.csv`.

---

## T4: Instructor Settlement Module (Wave 2)

- **Status**: Planned / Pending implementation.
- **Scope**:
  - Implemented in `modules/settlement/`.
  - Migration ownership: `300.x` (`V300_1__settlement.sql`).
  - ActiveMQ JMS subscription on `Topics.PAYMENTS` (`subscription = "settlement"`, container factory `Topics.LISTENER_FACTORY`).
  - Batching: periodic (`slotbook.settlement.interval-seconds`, default 300) and manual trigger `POST /api/modules/settlement/admin/settle`.
  - Payout: 70% share of net (`charges - refunds`).
  - Verification: `./chaos.cmd` (runs `SettlementChaosRun` with 600 payments under simulated network and database faults).
