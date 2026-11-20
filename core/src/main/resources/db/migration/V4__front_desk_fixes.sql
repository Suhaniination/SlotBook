-- Fix T1.5: Set default plan for any member who has null plan_id
update members set plan_id = (select id from plans where is_default = true limit 1) where plan_id is null and role = 'MEMBER';

-- Fix T1.3: Cancel duplicate confirmed bookings, keeping the earliest one for each member and slot
update bookings
set status = 'CANCELLED', cancelled_at = now()
where id in (
    select b.id
    from bookings b
    join (
        select slot_id, member_id, min(id) as first_id
        from bookings
        where status = 'CONFIRMED'
        group by slot_id, member_id
        having count(*) > 1
    ) dup on b.slot_id = dup.slot_id and b.member_id = dup.member_id and b.id != dup.first_id
    where b.status = 'CONFIRMED'
);

-- Prevent future duplicate confirmed bookings for the same member in the same slot
create unique index if not exists bookings_member_slot_unique on bookings (slot_id, member_id) where status = 'CONFIRMED';

-- Fix T1.7: Reconcile historical October refunds to match payment gateway amounts
update payments set amount = 121.91 where id in (950001, 950007, 950013, 950019, 950025);
update payments set amount = 300.00 where id in (950002, 950008, 950014, 950020, 950026);
update payments set amount = 116.96 where id in (950005, 950011, 950017, 950023, 950029);
update payments set amount = 67.73  where id in (950006, 950012, 950018, 950024, 950030);
