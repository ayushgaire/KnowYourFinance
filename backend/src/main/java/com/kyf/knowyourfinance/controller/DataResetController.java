package com.kyf.knowyourfinance.controller;

import com.kyf.knowyourfinance.dto.DataSummary;
import com.kyf.knowyourfinance.dto.ResetResult;
import com.kyf.knowyourfinance.service.DataResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /api/data/summary?userId=1 says how much data a user has.
 * DELETE /api/data?userId=1&confirm=DELETE removes it. The confirm word
 * is required so a stray request can never wipe anything by accident.
 * includeName=true also removes the saved name.
 */
@RestController
@RequestMapping("/api/data")
public class DataResetController {

    static final String CONFIRM_WORD = "DELETE";

    private final DataResetService service;

    public DataResetController(DataResetService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DataSummary summary(@RequestParam Long userId) {
        checkUser(userId);
        return service.summary(userId);
    }

    @DeleteMapping
    public ResetResult reset(@RequestParam Long userId,
                             @RequestParam(required = false) String confirm,
                             @RequestParam(defaultValue = "false") boolean includeName) {
        checkUser(userId);
        if (!CONFIRM_WORD.equals(confirm)) {
            throw new IllegalArgumentException("Type DELETE to confirm that you want to remove all your data.");
        }
        return service.reset(userId, includeName);
    }

    private static void checkUser(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("Choose a valid user.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
