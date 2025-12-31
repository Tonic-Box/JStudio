package com.tonic.ui.query.planner;

import com.tonic.ui.query.planner.probe.ProbeResult;

import java.util.function.Predicate;

/**
 * Post-execution filter that evaluates predicates against collected probe results.
 * Applied after execution to filter results based on dynamic criteria.
 */
public interface PostFilter {

    boolean test(ProbeResult result);

    default PostFilter and(PostFilter other) {
        return result -> this.test(result) && other.test(result);
    }

    default PostFilter or(PostFilter other) {
        return result -> this.test(result) || other.test(result);
    }

    default PostFilter negate() {
        return result -> !this.test(result);
    }

    static PostFilter alwaysTrue() {
        return result -> true;
    }

    static PostFilter alwaysFalse() {
        return result -> false;
    }

    static PostFilter fromPredicate(Predicate<ProbeResult> predicate) {
        return predicate::test;
    }
}
