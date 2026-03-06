package com.careertalk.interview.controller;

import com.careertalk.interview.dto.TurnSttRequest;
import com.careertalk.interview.dto.TurnSttResponse;
import com.careertalk.interview.service.TurnSttService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interview/turns")
public class InterviewTurnSttController {

    private final TurnSttService turnSttService;

    @PostMapping("/{turnId}/stt")
    public TurnSttResponse runStt(@PathVariable Long turnId,
                                  @RequestBody(required = false) TurnSttRequest req) throws Exception {
        if (req == null) req = new TurnSttRequest();
        return turnSttService.runStt(turnId, req);
    }
}