# LangGraph4j Support Triage Agent

A Spring Boot service demonstrating a production-shaped LangGraph4j agent:
classify an inbound support message's intent, route it to a specialist
handler, and loop back for clarification (with a bounded retry before
escalating to a human) when the classifier isn't confident.

## Why this shape

| Concern                     | How it's handled                                              |
|------------------------------|----------------------------------------------------------------|
| State contract               | `TriageState` — typed accessors over the graph's state map, like a DTO over a `Map<String,Object>` |
| Merge semantics per key       | `TriageState.SCHEMA` — `messages` appends, everything else is last-write-wins |
| Business logic                | One `NodeAction` class per responsibility (`ClassifyIntentNode`, `SpecialistHandlerNode`, `RequestClarificationNode`, `EscalateToHumanNode`) |
| Routing                       | `TriageGraphFactory` — conditional edges are pure `TriageState -> String` functions, unit-testable without a running graph |
| Loop guard                    | `clarificationAttempts` counter + max-attempts check, so the clarify↔classify loop can't run forever |
| Model wiring                  | `ChatModelConfig` — isolated so swapping OpenAI for Anthropic/Bedrock/Ollama is a one-file change |
| Compiled graph lifecycle      | `GraphConfig` — built once as a singleton Spring bean, invoked per-request |
| Testability                   | `TriageGraphFactoryTest` — a scripted fake `ChatLanguageModel` drives the graph deterministically, no network calls |

## Graph shape

```
START -> classify_intent -> [billing | technical | general] -> END
                          -> clarify -> (retry -> classify_intent) | (escalate -> END)
```

## Run it

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

```bash
curl -X POST localhost:8080/api/v1/support/triage \
  -H "Content-Type: application/json" \
  -d '{"message": "I was charged twice for my subscription this month."}'
```

Response:

```json
{
  "intent": "BILLING",
  "confidence": 0.92,
  "resolution": "...",
  "escalated": false,
  "trace": [
    "Classified as BILLING (confidence=0.92)",
    "[billing] ..."
  ]
}
```

## Test without an API key

```bash
mvn test
```

`TriageGraphFactoryTest` swaps in a scripted fake model, so the routing
logic (confident-intent path, low-confidence clarify-then-escalate path) is
verified without hitting OpenAI.

## Extending

- **Add a new lane** (e.g. `REFUND`): add an `Intent` value, add a
  `SpecialistHandlerNode` instance in `TriageGraphFactory.build`, add it to
  `addConditionalEdges`'s routing map, add an `addEdge(NODE_X, END)`.
- **Persist state across turns**: LangGraph4j supports checkpointers for
  multi-turn conversations; swap `graph.compile()` for
  `graph.compile(CompileConfig.builder().checkpointSaver(...).build())`
  and pass a `thread_id` per conversation.
- **Add tool calls** (e.g. look up an order in a DB from within
  `TECHNICAL`'s handler): inject a repository into `SpecialistHandlerNode`
  and call it before building the prompt, same as any Spring service.
