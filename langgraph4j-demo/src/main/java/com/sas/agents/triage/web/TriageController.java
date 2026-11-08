package com.sas.agents.triage.web;

import com.sas.agents.triage.state.TriageState;
import com.sas.agents.triage.web.TriageDtos.TriageRequest;
import com.sas.agents.triage.web.TriageDtos.TriageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TriageController {

    private final CompiledGraph<TriageState> triageGraph;

    @PostMapping("/api/v1/support/triage")
    public TriageResponse triage(@Valid @RequestBody TriageRequest request) {
        Map<String, Object> initialState = Map.of(TriageState.KEY_INPUT, request.message());

        // invoke() runs the graph to completion (END reached) and returns the
        // final accumulated state — analogous to calling a @Transactional
        // service method and getting back the committed result.
        Optional<TriageState> finalState = triageGraph.invoke(initialState);

        TriageState result = finalState.orElseThrow(() ->
                new IllegalStateException("Graph completed without producing a final state"));

        return new TriageResponse(
                result.intent().map(Enum::name).orElse("UNKNOWN"),
                result.confidence(),
                result.resolution().orElse("No resolution produced."),
                result.escalated(),
                result.messages()
        );
    }
}
