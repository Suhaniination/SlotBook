package com.slotbook.core.admin;

import com.slotbook.contract.CurrentMember;
import com.slotbook.core.config.Db;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
public class AdminController {

    public record AttendanceRow(long bookingId, long memberId, String memberName, String email, String status, Instant bookedAt) {
    }

    private final CurrentMember current;
    private final JdbcClient jdbc;

    public AdminController(CurrentMember current, JdbcClient jdbc) {
        this.current = current;
        this.jdbc = jdbc;
    }

    /** Everyone with a confirmed booking for a slot, in booking order. */
    @GetMapping("/api/admin/slots/{slotId}/attendance")
    public List<AttendanceRow> attendance(@PathVariable long slotId) {
        current.requireAdmin();
        return jdbc.sql("""
                        select b.id, b.member_id, m.name, m.email, b.status, b.created_at
                        from bookings b join members m on m.id = b.member_id
                        where b.slot_id = :slot and b.status = 'CONFIRMED'
                        order by b.created_at
                        """)
                .param("slot", slotId)
                .query((rs, i) -> new AttendanceRow(rs.getLong("id"), rs.getLong("member_id"), rs.getString("name"),
                        rs.getString("email"), rs.getString("status"), Db.instant(rs, "created_at")))
                .list();
    }

    /** Refunds paid out in a month (?month=2026-10), for reconciling with the payment gateway's report. */
    @GetMapping("/api/admin/reports/refunds")
    public Map<String, Object> refunds(@RequestParam String month) {
        current.require();
        YearMonth ym = YearMonth.parse(month);
        List<Double> amounts = jdbc.sql("""
                        select amount from payments
                        where kind = 'REFUND' and created_at >= :from and created_at < :to
                        """)
                .param("from", ym.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .param("to", ym.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .query(Double.class)
                .list();

        double total = 0;
        for (double amount : amounts) {
            total += amount;
        }
        total = Math.floor(total * 100) / 100;

        return Map.of("month", month, "refunds", amounts.size(), "total", total);
    }
}
