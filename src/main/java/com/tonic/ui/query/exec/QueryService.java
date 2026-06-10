package com.tonic.ui.query.exec;

import com.tonic.parser.ClassPool;
import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.parser.ParseException;
import com.tonic.ui.query.parser.QueryParser;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.QueryPlanner;
import com.tonic.ui.query.planner.ResultRow;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QueryService {

    private final ClassPool classPool;
    private final QueryParser parser;
    private final ExecutorService executorService;

    private QueryBatchRunner currentRunner;
    private Set<String> userClassNames;

    public QueryService(ClassPool classPool) {
        this.classPool = classPool;
        this.parser = new QueryParser();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "QueryRunner");
            t.setDaemon(true);
            return t;
        });
    }

    public void setUserClassNames(Set<String> userClassNames) {
        this.userClassNames = userClassNames;
    }

    public Query parse(String queryText) throws ParseException {
        return parser.parse(queryText);
    }

    public ProbePlan plan(Query query) {
        QueryPlanner planner = new QueryPlanner();
        return planner.plan(query);
    }

    public CompletableFuture<QueryResult> executeAsync(
            String queryText,
            QueryConfig config,
            QueryBatchRunner.ProgressListener progressListener) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(queryText, config, progressListener);
            } catch (ParseException e) {
                return new QueryResult(
                    queryText, null, null, null,
                    0, false, e.getMessage()
                );
            }
        }, executorService);
    }

    public QueryResult execute(
            String queryText,
            QueryConfig config,
            QueryBatchRunner.ProgressListener progressListener) throws ParseException {

        long startTime = System.currentTimeMillis();

        Query query = parse(queryText);
        ProbePlan plan = plan(query);

        QueryBatchRunner runner = new QueryBatchRunner(classPool);
        currentRunner = runner;

        if (userClassNames != null) {
            runner.setUserClassNames(userClassNames);
        }

        if (config != null) {
            runner.setTimeBudgetMs(config.timeBudgetMs());
        }

        QueryBatchRunner.QueryBatchResult batchResult = runner.run(plan, progressListener);
        currentRunner = null;

        long totalTime = System.currentTimeMillis() - startTime;

        return new QueryResult(
            queryText,
            query,
            plan,
            batchResult.matchingRows(),
            totalTime,
            !batchResult.wasCancelled(),
            null
        );
    }

    public void cancel() {
        if (currentRunner != null) {
            currentRunner.cancel();
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public static final class QueryResult {
        private final String queryText;
        private final Query query;
        private final ProbePlan plan;
        private final List<ResultRow> results;
        private final long executionTimeMs;
        private final boolean completed;
        private final String error;

        public QueryResult(String queryText, Query query, ProbePlan plan,
                           List<ResultRow> results, long executionTimeMs,
                           boolean completed, String error) {
            this.queryText = queryText;
            this.query = query;
            this.plan = plan;
            this.results = results;
            this.executionTimeMs = executionTimeMs;
            this.completed = completed;
            this.error = error;
        }

        public Query query() { return query; }
        public List<ResultRow> results() { return results; }
        public long executionTimeMs() { return executionTimeMs; }
        public boolean completed() { return completed; }
        public String error() { return error; }

        public boolean hasError() {
            return error != null;
        }

        public int resultCount() {
            return results != null ? results.size() : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueryResult)) return false;
            QueryResult that = (QueryResult) o;
            return executionTimeMs == that.executionTimeMs &&
                   completed == that.completed &&
                   Objects.equals(queryText, that.queryText) &&
                   Objects.equals(query, that.query) &&
                   Objects.equals(plan, that.plan) &&
                   Objects.equals(results, that.results) &&
                   Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queryText, query, plan, results, executionTimeMs, completed, error);
        }
    }

    /** Runtime knobs for a query run. Currently just the wall-clock budget that aborts long scans. */
    public static final class QueryConfig {
        private final long timeBudgetMs;

        public QueryConfig(long timeBudgetMs) {
            this.timeBudgetMs = timeBudgetMs;
        }

        public long timeBudgetMs() {
            return timeBudgetMs;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueryConfig)) return false;
            return timeBudgetMs == ((QueryConfig) o).timeBudgetMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeBudgetMs);
        }

        public static class Builder {
            private long timeBudgetMs = 60_000;

            public Builder timeBudgetMs(long ms) {
                this.timeBudgetMs = ms;
                return this;
            }

            public QueryConfig build() {
                return new QueryConfig(timeBudgetMs);
            }
        }
    }
}
