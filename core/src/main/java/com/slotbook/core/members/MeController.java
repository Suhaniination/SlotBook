package com.slotbook.core.members;

import com.slotbook.contract.CentreClock;
import com.slotbook.contract.CurrentMember;
import com.slotbook.contract.MemberView;
import com.slotbook.core.config.Db;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
public class MeController {

    private final CurrentMember current;
    private final MemberRepository members;
    private final JdbcClient jdbc;
    private final CentreClock centre;

    public MeController(CurrentMember current, MemberRepository members, JdbcClient jdbc, CentreClock centre) {
        this.current = current;
        this.members = members;
        this.jdbc = jdbc;
        this.centre = centre;
    }

    @GetMapping("/api/me")
    public MemberView me() {
        return current.require();
    }

    @GetMapping("/api/me/plan")
    public Map<String, Object> myPlan() {
        MemberView me = current.require();
        Member member = members.findById(me.id()).orElseThrow();
        Plan plan = (member.planId() != null ? members.findPlan(member.planId()) : Optional.<Plan>empty())
                .or(members::findDefaultPlan)
                .orElseThrow();

        LocalDate firstOfMonth = LocalDate.ofInstant(centre.now(), centre.zone()).withDayOfMonth(1);
        Instant from = firstOfMonth.atStartOfDay(centre.zone()).toInstant();
        Instant to = firstOfMonth.plusMonths(1).atStartOfDay(centre.zone()).toInstant();
        int used = jdbc.sql("""
                        select count(*) from bookings b join slots s on s.id = b.slot_id
                        where b.member_id = :member and b.status = 'CONFIRMED'
                          and s.starts_at >= :from and s.starts_at < :to
                        """)
                .param("member", me.id())
                .param("from", Db.ts(from))
                .param("to", Db.ts(to))
                .query(Integer.class)
                .single();

        return Map.of(
                "plan", plan.name(),
                "classesPerMonth", plan.classesPerMonth(),
                "monthlyPrice", plan.monthlyPrice(),
                "classesUsedThisMonth", used);
    }
}
