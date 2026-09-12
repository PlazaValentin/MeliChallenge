package com.hackerrank.challenge.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de la pregunta.
 *
 * <pre>
 * OPEN -> ANSWERED -> RESOLVED
 * </pre>
 *
 * <p>
 * Estrictamente secuencial: no se admite OPEN -> RESOLVED. Responder y resolver
 * son dos acciones distintas del vendedor. Operaciones considera "sin resolver"
 * a
 * OPEN y ANSWERED.
 */
public enum QuestionStatus {

    OPEN,
    ANSWERED,
    RESOLVED;

    private static final Map<QuestionStatus, Set<QuestionStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<QuestionStatus, Set<QuestionStatus>> transitions = new EnumMap<>(QuestionStatus.class);
        transitions.put(OPEN, EnumSet.of(ANSWERED));
        transitions.put(ANSWERED, EnumSet.of(RESOLVED));
        transitions.put(RESOLVED, EnumSet.noneOf(QuestionStatus.class));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /** Estado con el que nace toda pregunta. */
    public static QuestionStatus initial() {
        return OPEN;
    }

    public boolean canTransitionTo(QuestionStatus target) {
        return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<QuestionStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    /** Sin resolver es todo lo que Operaciones todavia debe mirar. */
    public boolean isUnresolved() {
        return this != RESOLVED;
    }
}
