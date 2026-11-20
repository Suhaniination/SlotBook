package com.slotbook.core;

import com.slotbook.contract.CentreClock;
import com.slotbook.contract.MemberView;
import com.slotbook.core.testing.SlotBookTest;
import com.slotbook.core.testing.TestHost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlotBookTest
class FrontDeskTicketsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestHost host;

    @Autowired
    CentreClock centreClock;

    @Test
    void t1_1_morningYogaSlotInCentreTimezone() throws Exception {
        // Find next Monday in centre timezone
        LocalDate nextMonday = LocalDate.ofInstant(centreClock.now(), centreClock.zone()).plusDays(7);
        while (nextMonday.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            nextMonday = nextMonday.plusDays(1);
        }
        LocalDate previousSunday = nextMonday.minusDays(1);
        ZonedDateTime monday5amIst = ZonedDateTime.of(nextMonday, LocalTime.of(5, 0), centreClock.zone());
        long slot = host.newSlot(10, monday5amIst.toInstant());

        // /api/slots?date=nextMonday must find it
        mvc.perform(get("/api/slots?date=" + nextMonday))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + slot + ")]").exists());

        // /api/slots?date=previousSunday must NOT include Monday's 5:00 AM slot
        mvc.perform(get("/api/slots?date=" + previousSunday))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + slot + ")]").doesNotExist());

        // And /api/bookings/mine must list date as nextMonday, not Sunday
        MemberView member = host.newMember("Morning Yogi");
        host.book(member, slot);
        mvc.perform(get("/api/bookings/mine").header("Authorization", host.auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slot.id == " + slot + ")].date").value(nextMonday.toString()));
    }

    @Test
    void t1_3_duplicateBookingRejected() throws Exception {
        long slot = host.newSlot(5);
        MemberView member = host.newMember("Double Booker");
        String auth = host.auth(member);

        // First booking succeeds
        mvc.perform(post("/api/bookings").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\": " + slot + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BOOKED"));

        // Second booking returns ALREADY_BOOKED with 409 Conflict
        mvc.perform(post("/api/bookings").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\": " + slot + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("ALREADY_BOOKED"));
    }

    @Test
    void t1_5_newMemberPlanAccessible() throws Exception {
        // Sign up a brand new member
        String res = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Fresh Member\", \"email\": \"fresh." + java.util.UUID.randomUUID() + "@test.local\", \"password\": \"demo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = res.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // Visiting /api/me/plan must return 200 with Starter plan rather than 500
        mvc.perform(get("/api/me/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("Starter"))
                .andExpect(jsonPath("$.classesUsedThisMonth").value(0));
    }

    @Test
    void t1_7_reconciledOctoberRefundsReport() throws Exception {
        String adminAuth = host.auth(host.admin());

        // October 2026 report must exactly equal payment gateway CSV (30 refunds, 6853.50 total)
        mvc.perform(get("/api/admin/reports/refunds?month=2026-10").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.refunds").value(30))
                .andExpect(jsonPath("$.total").value(6853.50));
    }
}
