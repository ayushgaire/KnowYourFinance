package com.kyf.knowyourfinance.controller;

import com.kyf.knowyourfinance.service.BudgetService;
import com.kyf.knowyourfinance.model.TransactionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.YearMonth;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BudgetController.class)
class BudgetControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BudgetService service;
    String url = "/api/budgets/DINING?userId=1&year=2026&month=11";
    @Test void listsForRequestedUserAndMonth() throws Exception {
        when(service.progress(1L, YearMonth.of(2026,11))).thenReturn(List.of());
        mvc.perform(get("/api/budgets?userId=1&year=2026&month=11")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).progress(1L, YearMonth.of(2026,11));
    }
    @Test void rejectsInvalidPeriodsAndUsers() throws Exception {
        for (String query : List.of("userId=1&year=2026&month=13", "userId=1&year=0&month=1", "userId=0&year=2026&month=1"))
            mvc.perform(get("/api/budgets?"+query)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsMissingOrUnknownParameters() throws Exception {
        mvc.perform(get("/api/budgets?userId=1&year=2026")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/budgets/UNKNOWN?userId=1&year=2026&month=11").contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":100}"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsInvalidLimitsBeforeSaving() throws Exception {
        for (String amount : List.of("null", "0", "-1", "1.001", "1000000000"))
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":"+amount+"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.limitAmount").exists());
        verifyNoInteractions(service);
    }
    @Test void rejectsMalformedLimit() throws Exception {
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsIncomeCategory() throws Exception {
        mvc.perform(put(url.replace("DINING", "INCOME")).contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":100}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void savesValidLimit() throws Exception {
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":100.25}"))
                .andExpect(status().isOk());
        verify(service).save(eq(1L), eq(YearMonth.of(2026,11)), eq(TransactionCategory.DINING), eq(new java.math.BigDecimal("100.25")));
    }
    @Test void deletionReturnsCorrectStatus() throws Exception {
        when(service.remove(1L, YearMonth.of(2026,11), TransactionCategory.DINING)).thenReturn(true, false);
        mvc.perform(delete(url)).andExpect(status().isNoContent());
        mvc.perform(delete(url)).andExpect(status().isNotFound());
    }
}
