package com.kyf.knowyourfinance.controller;

import com.kyf.knowyourfinance.dto.DataSummary;
import com.kyf.knowyourfinance.dto.ResetResult;
import com.kyf.knowyourfinance.service.DataResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataResetController.class)
class DataResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataResetService service;

    @Test
    void summaryReturnsTheCounts() throws Exception {
        when(service.summary(1L)).thenReturn(new DataSummary(38, 3));

        mockMvc.perform(get("/api/data/summary").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(38))
                .andExpect(jsonPath("$.budgets").value(3));
    }

    @Test
    void deleteWithTheConfirmWordRemovesTheData() throws Exception {
        when(service.reset(1L, false)).thenReturn(new ResetResult(38, 3, false));

        mockMvc.perform(delete("/api/data").param("userId", "1").param("confirm", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedTransactions").value(38))
                .andExpect(jsonPath("$.deletedBudgets").value(3))
                .andExpect(jsonPath("$.nameRemoved").value(false));
    }

    @Test
    void deleteCanAlsoRemoveTheName() throws Exception {
        when(service.reset(1L, true)).thenReturn(new ResetResult(0, 0, true));

        mockMvc.perform(delete("/api/data")
                        .param("userId", "1").param("confirm", "DELETE").param("includeName", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameRemoved").value(true));
    }

    @Test
    void deleteWithoutTheConfirmWordIsRejectedAndDeletesNothing() throws Exception {
        mockMvc.perform(delete("/api/data").param("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Type DELETE to confirm that you want to remove all your data."));

        verify(service, never()).reset(anyLong(), anyBoolean());
    }

    @Test
    void theConfirmWordMustMatchExactly() throws Exception {
        mockMvc.perform(delete("/api/data").param("userId", "1").param("confirm", "delete"))
                .andExpect(status().isBadRequest());

        verify(service, never()).reset(anyLong(), anyBoolean());
    }

    @Test
    void anInvalidUserIsRejected() throws Exception {
        mockMvc.perform(delete("/api/data").param("userId", "0").param("confirm", "DELETE"))
                .andExpect(status().isBadRequest());

        verify(service, never()).reset(anyLong(), anyBoolean());
    }
}
