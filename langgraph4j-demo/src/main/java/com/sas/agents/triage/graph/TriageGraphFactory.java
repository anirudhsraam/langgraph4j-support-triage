package com.sas.agents.triage.graph;

import com.sas.agents.triage.node.ClassifyIntentNode;
import com.sas.agents.triage.node.EscalateToHumanNode;
import com.sas.agents.triage.node.RequestClarificationNode;
import com.sas.agents.triage.node.SpecialistHandlerNode;
import com.sas.agents.triage.state.TriageState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.jetbrains.annotations.NotNull;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Assembles the support-triage graph:
 *
 * <pre>
 *              START
 *                |
 *          classify_intent
 *           /    |    |   \
 *      billing technical general  clarify (low confidence)
 *          \      |     |     /        |
 *           \     |     |    /   attempts >= 2 --> escalate --> END
 *            \    |     |   /    attempts <  2 --> classify_intent (loop back)
 *                 END
 * </pre>
 * <p>
 * This mirrors how you'd wire a Spring State Machine or a BPMN flow: nodes
 * are pure functions over state, edges are routing decisions, and the whole
 * thing compiles once at startup into an immutable, reusable {@link CompiledGraph}.
 */
public final class TriageGraphFactory {

    private static final String NODE_CLASSIFY = "classify_intent";
    private static final String NODE_BILLING = "billing_handler";
    private static final String NODE_TECHNICAL = "technical_handler";
    private static final String NODE_GENERAL = "general_handler";
    private static final String NODE_CLARIFY = "request_clarification";
    private static final String NODE_ESCALATE = "escalate_to_human";

    private static final String ROUTE_BILLING = "billing";
    private static final String ROUTE_TECHNICAL = "technical";
    private static final String ROUTE_GENERAL = "general";
    private static final String ROUTE_CLARIFY = "clarify";

    private static final double CONFIDENCE_THRESHOLD = 0.55;
    private static final int MAX_CLARIFICATION_ATTEMPTS = 2;

    private TriageGraphFactory() {
    }

    public static CompiledGraph<TriageState> build(ChatLanguageModel chatModel) throws GraphStateException {

        AsyncNodeAction<TriageState> classify = node_async(new ClassifyIntentNode(chatModel));

        AsyncNodeAction<TriageState> billing = node_async(new SpecialistHandlerNode(

                ROUTE_BILLING, """
                You are a billing support specialist. Help with charges, invoices,
                refunds, and subscription changes. Be precise about what you can
                and cannot do without account verification.
                """, chatModel));

        AsyncNodeAction<TriageState> technical = node_async(new SpecialistHandlerNode(
                ROUTE_TECHNICAL, """
                You are a technical support specialist for a backend/API product.
                Ask for error messages, request IDs, or logs when relevant, and
                give concrete troubleshooting steps.
                """, chatModel));

        AsyncNodeAction<TriageState> general = node_async(new SpecialistHandlerNode(
                ROUTE_GENERAL, """
                You are a friendly general support agent. Answer clearly and
                point the customer to billing or technical support if the
                question turns out to belong there.
                """, chatModel));

        AsyncNodeAction<TriageState> clarify = node_async(new RequestClarificationNode());
        AsyncNodeAction<TriageState> escalate = node_async(new EscalateToHumanNode());

        StateGraph<TriageState> graph = new StateGraph<>(TriageState.SCHEMA, TriageState::new)
                .addNode(NODE_CLASSIFY, classify)
                .addNode(NODE_BILLING, billing)
                .addNode(NODE_TECHNICAL, technical)
                .addNode(NODE_GENERAL, general)
                .addNode(NODE_CLARIFY, clarify)
                .addNode(NODE_ESCALATE, escalate)

                .addEdge(START, NODE_CLASSIFY)

                // Conditional routing straight out of the classifier
                .addConditionalEdges(
                        NODE_CLASSIFY,
                        edge_async(TriageGraphFactory::routeAfterClassification),
                        java.util.Map.of(
                                ROUTE_BILLING, NODE_BILLING,
                                ROUTE_TECHNICAL, NODE_TECHNICAL,
                                ROUTE_GENERAL, NODE_GENERAL,
                                ROUTE_CLARIFY, NODE_CLARIFY
                        )
                )

                // Clarification either loops back to classification or escalates
                .addConditionalEdges(
                        NODE_CLARIFY,
                        edge_async(TriageGraphFactory::routeAfterClarification),
                        java.util.Map.of(
                                "retry", NODE_CLASSIFY,
                                "escalate", NODE_ESCALATE
                        )
                )

                .addEdge(NODE_BILLING, END)
                .addEdge(NODE_TECHNICAL, END)
                .addEdge(NODE_GENERAL, END)
                .addEdge(NODE_ESCALATE, END);

        return graph.compile();
    }

    private static String routeAfterClassification(@NotNull TriageState state) {
        if (state.confidence() < CONFIDENCE_THRESHOLD) {
            return ROUTE_CLARIFY;
        }
        return state.intent()
                .map(intent -> switch (intent) {
                    case BILLING -> ROUTE_BILLING;
                    case TECHNICAL -> ROUTE_TECHNICAL;
                    case GENERAL -> ROUTE_GENERAL;
                    case UNCLEAR -> ROUTE_CLARIFY;
                })
                .orElse(ROUTE_CLARIFY);
    }

    @NotNull
    private static String routeAfterClarification(TriageState state) {
        return state.clarificationAttempts() >= MAX_CLARIFICATION_ATTEMPTS ? "escalate" : "retry";
    }
}
