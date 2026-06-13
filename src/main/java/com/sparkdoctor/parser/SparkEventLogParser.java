package com.sparkdoctor.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.analysis.FailureDetector;
import com.sparkdoctor.analysis.LowShuffleParallelismDetector;
import com.sparkdoctor.analysis.OversizedShufflePartitionDetector;
import com.sparkdoctor.analysis.RecommendationEngine;
import com.sparkdoctor.analysis.RetryWasteDetector;
import com.sparkdoctor.analysis.ShufflePartitionSkewDetector;
import com.sparkdoctor.analysis.SpillPressureDetector;
import com.sparkdoctor.analysis.SpillSkewDetector;
import com.sparkdoctor.analysis.SqlPlanDuplicateSubtreeDetector;
import com.sparkdoctor.analysis.SqlPlanExchangeDetector;
import com.sparkdoctor.analysis.SqlPlanPossibleMissedExchangeReuseDetector;
import com.sparkdoctor.analysis.SpeculationDetector;
import com.sparkdoctor.analysis.TaskDurationSkewDetector;
import com.sparkdoctor.analysis.TinyTaskDetector;
import com.sparkdoctor.analysis.WorkerImbalanceDetector;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.FailedJob;
import com.sparkdoctor.model.FailedStage;
import com.sparkdoctor.model.ParsedEventLog;
import com.sparkdoctor.model.StageAnalysis;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanMetric;
import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanOperatorSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SparkEventLogParser {
    private static final String APPLICATION_START = "SparkListenerApplicationStart";
    private static final String APPLICATION_END = "SparkListenerApplicationEnd";
    private static final String JOB_START = "SparkListenerJobStart";
    private static final String JOB_END = "SparkListenerJobEnd";
    private static final String STAGE_SUBMITTED = "SparkListenerStageSubmitted";
    private static final String STAGE_COMPLETED = "SparkListenerStageCompleted";
    private static final String TASK_END = "SparkListenerTaskEnd";
    private static final String SQL_EXECUTION_START = "SparkListenerSQLExecutionStart";
    private static final String SQL_ADAPTIVE_EXECUTION_UPDATE = "SparkListenerSQLAdaptiveExecutionUpdate";
    private static final String SQL_EXECUTION_END = "SparkListenerSQLExecutionEnd";
    private static final String SQL_DRIVER_ACCUM_UPDATES = "SparkListenerDriverAccumUpdates";

    private final EventLogReader eventLogReader;
    private final ObjectMapper objectMapper;
    private final TaskDurationSkewDetector taskDurationSkewDetector;
    private final ShufflePartitionSkewDetector shufflePartitionSkewDetector;
    private final OversizedShufflePartitionDetector oversizedShufflePartitionDetector;
    private final LowShuffleParallelismDetector lowShuffleParallelismDetector;
    private final SpillPressureDetector spillPressureDetector;
    private final SpillSkewDetector spillSkewDetector;
    private final TinyTaskDetector tinyTaskDetector;
    private final RetryWasteDetector retryWasteDetector;
    private final SpeculationDetector speculationDetector;
    private final WorkerImbalanceDetector workerImbalanceDetector;
    private final SqlPlanExchangeDetector sqlPlanExchangeDetector;
    private final SqlPlanDuplicateSubtreeDetector sqlPlanDuplicateSubtreeDetector;
    private final SqlPlanPossibleMissedExchangeReuseDetector sqlPlanPossibleMissedExchangeReuseDetector;
    private final FailureDetector failureDetector;
    private final RecommendationEngine recommendationEngine;

    public SparkEventLogParser() {
        this(
                new EventLogReader(),
                new ObjectMapper(),
                new TaskDurationSkewDetector(),
                new ShufflePartitionSkewDetector(),
                new OversizedShufflePartitionDetector(),
                new LowShuffleParallelismDetector(),
                new SpillPressureDetector(),
                new SpillSkewDetector(),
                new TinyTaskDetector(),
                new RetryWasteDetector(),
                new SpeculationDetector(),
                new WorkerImbalanceDetector(),
                new SqlPlanExchangeDetector(),
                new SqlPlanDuplicateSubtreeDetector(),
                new SqlPlanPossibleMissedExchangeReuseDetector(),
                new FailureDetector(),
                new RecommendationEngine());
    }

    SparkEventLogParser(
            EventLogReader eventLogReader,
            ObjectMapper objectMapper,
            TaskDurationSkewDetector taskDurationSkewDetector,
            ShufflePartitionSkewDetector shufflePartitionSkewDetector,
            OversizedShufflePartitionDetector oversizedShufflePartitionDetector,
            LowShuffleParallelismDetector lowShuffleParallelismDetector,
            SpillPressureDetector spillPressureDetector,
            SpillSkewDetector spillSkewDetector,
            TinyTaskDetector tinyTaskDetector,
            RetryWasteDetector retryWasteDetector,
            SpeculationDetector speculationDetector,
            WorkerImbalanceDetector workerImbalanceDetector,
            SqlPlanExchangeDetector sqlPlanExchangeDetector,
            SqlPlanDuplicateSubtreeDetector sqlPlanDuplicateSubtreeDetector,
            SqlPlanPossibleMissedExchangeReuseDetector sqlPlanPossibleMissedExchangeReuseDetector,
            FailureDetector failureDetector,
            RecommendationEngine recommendationEngine) {
        this.eventLogReader = eventLogReader;
        this.objectMapper = objectMapper;
        this.taskDurationSkewDetector = taskDurationSkewDetector;
        this.shufflePartitionSkewDetector = shufflePartitionSkewDetector;
        this.oversizedShufflePartitionDetector = oversizedShufflePartitionDetector;
        this.lowShuffleParallelismDetector = lowShuffleParallelismDetector;
        this.spillPressureDetector = spillPressureDetector;
        this.spillSkewDetector = spillSkewDetector;
        this.tinyTaskDetector = tinyTaskDetector;
        this.retryWasteDetector = retryWasteDetector;
        this.speculationDetector = speculationDetector;
        this.workerImbalanceDetector = workerImbalanceDetector;
        this.sqlPlanExchangeDetector = sqlPlanExchangeDetector;
        this.sqlPlanDuplicateSubtreeDetector = sqlPlanDuplicateSubtreeDetector;
        this.sqlPlanPossibleMissedExchangeReuseDetector = sqlPlanPossibleMissedExchangeReuseDetector;
        this.failureDetector = failureDetector;
        this.recommendationEngine = recommendationEngine;
    }

    public ApplicationSummary parseApplicationSummary(Path eventLogPath) throws IOException {
        return parse(eventLogPath).applicationSummary();
    }

    public ApplicationSummary parseApplicationSummary(List<String> eventLines) throws IOException {
        return parse(eventLines).applicationSummary();
    }

    public ParsedEventLog parse(Path eventLogPath) throws IOException {
        try (Stream<String> lines = eventLogReader.lines(eventLogPath)) {
            return parse(lines);
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    public ParsedEventLog parse(List<String> eventLines) throws IOException {
        return parse(eventLines.stream());
    }

    public ParsedEventLog parse(Stream<String> eventLines) throws IOException {
        String appId = null;
        String appName = null;
        Long startTimeMillis = null;
        Long endTimeMillis = null;
        Set<Integer> jobIds = new HashSet<>();
        Set<Integer> completedJobIds = new HashSet<>();
        Set<Integer> failedJobIds = new HashSet<>();
        Set<Integer> stageIds = new LinkedHashSet<>();
        Set<Integer> completedStageIds = new HashSet<>();
        Set<Integer> failedStageIds = new HashSet<>();
        Map<Integer, FailedJob> failedJobs = new LinkedHashMap<>();
        Map<Integer, FailedStage> failedStages = new LinkedHashMap<>();
        Set<Long> successfulTaskIdsWithoutStage = new HashSet<>();
        Map<StageKey, StageAccumulator> stageAttempts = new LinkedHashMap<>();
        Map<Integer, Integer> latestStageAttemptIds = new HashMap<>();
        Map<TaskAttemptKey, ParsedTaskAttempt> successfulTaskAttempts = new LinkedHashMap<>();
        Map<Long, SqlExecutionAccumulator> sqlExecutions = new LinkedHashMap<>();
        Map<Integer, Long> stageSqlExecutionIds = new HashMap<>();

        var iterator = eventLines.iterator();
        while (iterator.hasNext()) {
            String eventLine = iterator.next();
            JsonNode event = objectMapper.readTree(eventLine);
            String eventType = event.path("Event").asText();

            if (APPLICATION_START.equals(eventType)) {
                appId = textOrNull(event, "App ID");
                appName = textOrNull(event, "App Name");
                startTimeMillis = longOrNull(event, "Timestamp");
            } else if (APPLICATION_END.equals(eventType)) {
                endTimeMillis = longOrNull(event, "Timestamp");
            } else if (JOB_START.equals(eventType)) {
                Integer jobId = intOrNull(event, "Job ID");
                if (jobId != null) {
                    jobIds.add(jobId);
                }
                recordStageSqlExecutionIds(event, stageSqlExecutionIds);
            } else if (JOB_END.equals(eventType)) {
                Integer jobId = intOrNull(event, "Job ID");
                if (jobId != null) {
                    jobIds.add(jobId);
                    if (isSuccessfulJobEnd(event)) {
                        completedJobIds.add(jobId);
                        failedJobIds.remove(jobId);
                        failedJobs.remove(jobId);
                    } else {
                        completedJobIds.remove(jobId);
                        failedJobIds.add(jobId);
                        failedJobs.put(jobId, new FailedJob(jobId, jobResult(event)));
                    }
                }
            } else if (STAGE_SUBMITTED.equals(eventType)) {
                JsonNode stageInfo = event.get("Stage Info");
                Integer stageId = stageId(event, stageInfo);
                if (stageId != null) {
                    Integer stageAttemptId = stageAttemptId(event, stageInfo);
                    stageIds.add(stageId);
                    updateLatestStageAttemptId(latestStageAttemptIds, stageId, stageAttemptId);
                    stageAttempts.computeIfAbsent(stageKey(stageId, stageAttemptId), key -> new StageAccumulator(key.stageId()))
                            .updateDetails(
                                    textOrNull(stageInfo, "Stage Name"),
                                    intOrNull(stageInfo, "Number of Tasks"));
                }
            } else if (STAGE_COMPLETED.equals(eventType)) {
                JsonNode stageInfo = event.get("Stage Info");
                Integer stageId = stageId(event, stageInfo);
                if (stageId != null) {
                    Integer stageAttemptId = stageAttemptId(event, stageInfo);
                    StageKey stageKey = stageKey(stageId, stageAttemptId);
                    stageIds.add(stageId);
                    updateLatestStageAttemptId(latestStageAttemptIds, stageId, stageAttemptId);
                    stageAttempts.computeIfAbsent(stageKey, key -> new StageAccumulator(key.stageId()))
                            .updateDetails(
                                    textOrNull(stageInfo, "Stage Name"),
                                    intOrNull(stageInfo, "Number of Tasks"));
                    if (isSuccessfulStageCompleted(stageInfo)) {
                        completedStageIds.add(stageId);
                        failedStageIds.remove(stageId);
                        failedStages.remove(stageId);
                        recordStageSqlAccumulatorValues(stageInfo, stageSqlExecutionIds, sqlExecutions);
                    } else {
                        completedStageIds.remove(stageId);
                        failedStageIds.add(stageId);
                        StageAccumulator stage =
                                stageAttempts.computeIfAbsent(stageKey, key -> new StageAccumulator(key.stageId()));
                        failedStages.put(
                                stageId,
                                new FailedStage(
                                        stageId,
                                        textOrNull(stageInfo, "Stage Name"),
                                        textOrNull(stageInfo, "Failure Reason"),
                                        stage.failedTaskAttempts(),
                                        stage.failedTaskAttemptDurationMillis(),
                                        stage.failedTaskAttemptReasons()));
                    }
                }
            } else if (TASK_END.equals(eventType)) {
                Integer stageId = stageId(event, event.get("Stage Info"));
                Integer stageAttemptId = stageAttemptId(event, event.get("Stage Info"));
                Long taskIndex = taskIndex(event);
                Long taskId = taskId(event);
                Long taskDurationMillis = taskDurationMillis(event);
                if (stageId != null) {
                    updateLatestStageAttemptId(latestStageAttemptIds, stageId, stageAttemptId);
                }
                if (!isSuccessfulTaskEnd(event)) {
                    if (stageId != null) {
                        stageAttempts.computeIfAbsent(stageKey(stageId, stageAttemptId), key -> new StageAccumulator(key.stageId()))
                                .addFailedTaskAttempt(taskDurationMillis, taskEndReason(event));
                    }
                    continue;
                }
                if (stageId == null || taskIndex == null) {
                    if (taskId != null) {
                        successfulTaskIdsWithoutStage.add(taskId);
                    }
                    continue;
                }

                Long shuffleReadBytes = shuffleReadBytes(event);
                TaskSpillMetrics spillMetrics = spillMetrics(event);
                StageAccumulator stage = stageAttempts.computeIfAbsent(
                        stageKey(stageId, stageAttemptId), key -> new StageAccumulator(key.stageId()));
                if (isSpeculativeTaskAttempt(event)) {
                    stage.addSpeculativeTaskAttempt(taskDurationMillis);
                }
                TaskAttemptKey taskAttemptKey = new TaskAttemptKey(stageId, stageAttemptId == null ? 0 : stageAttemptId, taskIndex);
                if (successfulTaskAttempts.containsKey(taskAttemptKey)) {
                    stage.addDuplicateSuccessfulTaskAttempt();
                }
                successfulTaskAttempts.put(
                        taskAttemptKey,
                        new ParsedTaskAttempt(
                                stageId,
                                stageAttemptId == null ? 0 : stageAttemptId,
                                taskDurationMillis,
                                shuffleReadBytes,
                                spillMetrics,
                                executorId(event),
                                host(event)));
            } else if (isSqlEvent(eventType, SQL_EXECUTION_START)) {
                Long executionId = longOrNull(event, "executionId");
                if (executionId != null) {
                    sqlExecutions.computeIfAbsent(executionId, SqlExecutionAccumulator::new).start(event);
                }
            } else if (isSqlEvent(eventType, SQL_ADAPTIVE_EXECUTION_UPDATE)) {
                Long executionId = longOrNull(event, "executionId");
                if (executionId != null) {
                    sqlExecutions.computeIfAbsent(executionId, SqlExecutionAccumulator::new).update(event);
                }
            } else if (isSqlEvent(eventType, SQL_EXECUTION_END)) {
                Long executionId = longOrNull(event, "executionId");
                if (executionId != null) {
                    sqlExecutions.computeIfAbsent(executionId, SqlExecutionAccumulator::new).end(event);
                }
            } else if (isSqlEvent(eventType, SQL_DRIVER_ACCUM_UPDATES)) {
                Long executionId = longOrNull(event, "executionId");
                if (executionId != null) {
                    sqlExecutions.computeIfAbsent(executionId, SqlExecutionAccumulator::new)
                            .recordDriverAccumulatorUpdates(event);
                }
            }
        }

        for (ParsedTaskAttempt taskAttempt : successfulTaskAttempts.values()) {
            StageAccumulator stage = stageAttempts.computeIfAbsent(
                    new StageKey(taskAttempt.stageId(), taskAttempt.stageAttemptId()),
                    key -> new StageAccumulator(key.stageId()));
            if (taskAttempt.taskDurationMillis() != null) {
                stage.addTaskDuration(taskAttempt.taskDurationMillis());
            }
            stage.addWorkerTask(taskAttempt.taskDurationMillis(), taskAttempt.executorId(), taskAttempt.host());
            if (taskAttempt.shuffleReadBytes() != null) {
                stage.addShuffleReadBytes(taskAttempt.shuffleReadBytes());
            }
            if (taskAttempt.spillMetrics() != null) {
                stage.addSpillBytes(
                        taskAttempt.spillMetrics().memoryBytesSpilled(),
                        taskAttempt.spillMetrics().diskBytesSpilled());
            }
        }

        List<StageAnalysis> stageAnalyses = stageIds.stream()
                .map(stageId -> latestStageAttempt(stageAttempts, latestStageAttemptIds, stageId))
                .map(StageAccumulator::toStageAnalysis)
                .toList();
        int analyzedTaskCount = successfulTaskIdsWithoutStage.size()
                + (int) successfulTaskAttempts.keySet().stream()
                        .filter(taskAttemptKey -> latestStageAttemptIds.getOrDefault(
                                        taskAttemptKey.stageId(), 0)
                                == taskAttemptKey.stageAttemptId())
                        .count();
        List<SqlExecution> sqlExecutionAnalyses =
                sqlExecutions.values().stream().map(SqlExecutionAccumulator::toSqlExecution).toList();
        List<Bottleneck> bottlenecks = new ArrayList<>();
        bottlenecks.addAll(taskDurationSkewDetector.detect(stageAnalyses));
        bottlenecks.addAll(shufflePartitionSkewDetector.detect(stageAnalyses));
        bottlenecks.addAll(oversizedShufflePartitionDetector.detect(stageAnalyses));
        bottlenecks.addAll(lowShuffleParallelismDetector.detect(stageAnalyses));
        bottlenecks.addAll(spillPressureDetector.detect(stageAnalyses));
        bottlenecks.addAll(spillSkewDetector.detect(stageAnalyses));
        bottlenecks.addAll(tinyTaskDetector.detect(stageAnalyses));
        bottlenecks.addAll(retryWasteDetector.detect(stageAnalyses));
        bottlenecks.addAll(speculationDetector.detect(stageAnalyses));
        bottlenecks.addAll(workerImbalanceDetector.detect(stageAnalyses));
        bottlenecks.addAll(sqlPlanExchangeDetector.detect(sqlExecutionAnalyses));
        List<Bottleneck> possibleMissedExchangeReuseBottlenecks =
                sqlPlanPossibleMissedExchangeReuseDetector.detect(sqlExecutionAnalyses);
        Set<Long> sqlExecutionsWithPossibleMissedExchangeReuse =
                sqlExecutionIds(possibleMissedExchangeReuseBottlenecks, "possible_missed_exchange_reuse");
        bottlenecks.addAll(sqlPlanDuplicateSubtreeDetector.detect(sqlExecutionAnalyses).stream()
                .filter(bottleneck -> !hasSqlExecutionId(bottleneck, sqlExecutionsWithPossibleMissedExchangeReuse))
                .toList());
        bottlenecks.addAll(possibleMissedExchangeReuseBottlenecks);
        List<FailedJob> failedJobDetails = List.copyOf(failedJobs.values());
        List<FailedStage> failedStageDetails = List.copyOf(failedStages.values());
        bottlenecks.addAll(failureDetector.detect(failedJobDetails, failedStageDetails));

        return new ParsedEventLog(
                new ApplicationSummary(appId, appName, startTimeMillis, endTimeMillis),
                new AnalysisSummary(
                        jobIds.size(),
                        completedJobIds.size(),
                        failedJobIds.size(),
                        stageIds.size(),
                        completedStageIds.size(),
                        failedStageIds.size(),
                        analyzedTaskCount,
                        bottlenecks.size()),
                stageAnalyses,
                sqlExecutionAnalyses,
                failedJobDetails,
                failedStageDetails,
                bottlenecks,
                recommendationEngine.recommend(bottlenecks));
    }

    private void updateLatestStageAttemptId(Map<Integer, Integer> latestStageAttemptIds, int stageId, Integer stageAttemptId) {
        latestStageAttemptIds.put(stageId, stageAttemptId == null ? 0 : stageAttemptId);
    }

    private StageKey stageKey(int stageId, Integer stageAttemptId) {
        return new StageKey(stageId, stageAttemptId == null ? 0 : stageAttemptId);
    }

    private StageAccumulator latestStageAttempt(
            Map<StageKey, StageAccumulator> stageAttempts, Map<Integer, Integer> latestStageAttemptIds, int stageId) {
        int latestStageAttemptId = latestStageAttemptIds.getOrDefault(stageId, 0);
        return stageAttempts.computeIfAbsent(new StageKey(stageId, latestStageAttemptId), key -> new StageAccumulator(key.stageId()));
    }

    private Set<Long> sqlExecutionIds(List<Bottleneck> bottlenecks, String bottleneckType) {
        Set<Long> sqlExecutionIds = new HashSet<>();
        for (Bottleneck bottleneck : bottlenecks) {
            if (!bottleneckType.equals(bottleneck.type())) {
                continue;
            }
            Object sqlExecutionId = bottleneck.evidence().get("sqlExecutionId");
            if (sqlExecutionId instanceof Number numericSqlExecutionId) {
                sqlExecutionIds.add(numericSqlExecutionId.longValue());
            }
        }
        return sqlExecutionIds;
    }

    private boolean hasSqlExecutionId(Bottleneck bottleneck, Set<Long> sqlExecutionIds) {
        Object sqlExecutionId = bottleneck.evidence().get("sqlExecutionId");
        return sqlExecutionId instanceof Number numericSqlExecutionId
                && sqlExecutionIds.contains(numericSqlExecutionId.longValue());
    }

    private boolean isSqlEvent(String eventType, String sqlEventType) {
        return eventType.equals(sqlEventType) || eventType.endsWith("." + sqlEventType);
    }

    private void recordStageSqlExecutionIds(JsonNode event, Map<Integer, Long> stageSqlExecutionIds) {
        Long executionId = longOrNull(event.get("Properties"), "spark.sql.execution.id");
        if (executionId == null) {
            return;
        }

        JsonNode stageIds = event.get("Stage IDs");
        if (stageIds != null && stageIds.isArray()) {
            for (JsonNode stageId : stageIds) {
                stageSqlExecutionIds.put(stageId.asInt(), executionId);
            }
        }

        JsonNode stageInfos = event.get("Stage Infos");
        if (stageInfos != null && stageInfos.isArray()) {
            for (JsonNode stageInfo : stageInfos) {
                Integer stageId = intOrNull(stageInfo, "Stage ID");
                if (stageId != null) {
                    stageSqlExecutionIds.put(stageId, executionId);
                }
            }
        }
    }

    private void recordStageSqlAccumulatorValues(
            JsonNode stageInfo,
            Map<Integer, Long> stageSqlExecutionIds,
            Map<Long, SqlExecutionAccumulator> sqlExecutions) {
        Integer stageId = intOrNull(stageInfo, "Stage ID");
        if (stageId == null) {
            return;
        }

        Long executionId = stageSqlExecutionIds.get(stageId);
        if (executionId == null) {
            return;
        }

        sqlExecutions.computeIfAbsent(executionId, SqlExecutionAccumulator::new)
                .recordSqlAccumulables(stageInfo.get("Accumulables"));
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    private Integer intOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asInt();
    }

    private Integer stageId(JsonNode event, JsonNode stageInfo) {
        Integer directStageId = intOrNull(event, "Stage ID");
        if (directStageId != null) {
            return directStageId;
        }

        if (stageInfo == null || stageInfo.isNull()) {
            return null;
        }

        return intOrNull(stageInfo, "Stage ID");
    }

    private Long taskId(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        return longOrNull(taskInfo, "Task ID");
    }

    private String executorId(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        return textOrNull(taskInfo, "Executor ID");
    }

    private String host(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        return textOrNull(taskInfo, "Host");
    }

    private boolean isSpeculativeTaskAttempt(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        return taskInfo != null && taskInfo.path("Speculative").asBoolean(false);
    }

    private Long taskIndex(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        Long taskIndex = longOrNull(taskInfo, "Index");
        if (taskIndex != null) {
            return taskIndex;
        }

        return longOrNull(taskInfo, "Task ID");
    }

    private Integer stageAttemptId(JsonNode event, JsonNode stageInfo) {
        Integer directStageAttemptId = intOrNull(event, "Stage Attempt ID");
        if (directStageAttemptId != null) {
            return directStageAttemptId;
        }

        if (stageInfo == null || stageInfo.isNull()) {
            return null;
        }

        return intOrNull(stageInfo, "Stage Attempt ID");
    }

    private boolean isSuccessfulTaskEnd(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo != null && taskInfo.has("Successful") && !taskInfo.path("Successful").asBoolean()) {
            return false;
        }

        JsonNode taskEndReason = event.get("Task End Reason");
        if (taskEndReason == null || taskEndReason.isNull()) {
            return true;
        }

        String reason = textOrNull(taskEndReason, "Reason");
        return reason == null || "Success".equals(reason);
    }

    private String taskEndReason(JsonNode event) {
        JsonNode taskEndReason = event.get("Task End Reason");
        if (taskEndReason == null || taskEndReason.isNull()) {
            return null;
        }

        return textOrNull(taskEndReason, "Reason");
    }

    private boolean isSuccessfulJobEnd(JsonNode event) {
        JsonNode jobResult = event.get("Job Result");
        if (jobResult == null || jobResult.isNull()) {
            return false;
        }

        return "JobSucceeded".equals(textOrNull(jobResult, "Result"));
    }

    private String jobResult(JsonNode event) {
        JsonNode jobResult = event.get("Job Result");
        return textOrNull(jobResult, "Result");
    }

    private boolean isSuccessfulStageCompleted(JsonNode stageInfo) {
        return textOrNull(stageInfo, "Failure Reason") == null;
    }

    private Long taskDurationMillis(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        Long launchTimeMillis = longOrNull(taskInfo, "Launch Time");
        Long finishTimeMillis = longOrNull(taskInfo, "Finish Time");
        if (launchTimeMillis == null || finishTimeMillis == null) {
            return null;
        }

        return finishTimeMillis - launchTimeMillis;
    }

    private Long shuffleReadBytes(JsonNode event) {
        JsonNode taskMetrics = event.get("Task Metrics");
        if (taskMetrics == null || taskMetrics.isNull()) {
            return null;
        }

        JsonNode shuffleReadMetrics = taskMetrics.get("Shuffle Read Metrics");
        if (shuffleReadMetrics == null || shuffleReadMetrics.isNull()) {
            return null;
        }

        Long localBytesRead = longOrNull(shuffleReadMetrics, "Local Bytes Read");
        Long remoteBytesRead = longOrNull(shuffleReadMetrics, "Remote Bytes Read");
        return valueOrZero(localBytesRead) + valueOrZero(remoteBytesRead);
    }

    private TaskSpillMetrics spillMetrics(JsonNode event) {
        JsonNode taskMetrics = event.get("Task Metrics");
        if (taskMetrics == null || taskMetrics.isNull()) {
            return null;
        }

        Long memoryBytesSpilled = longOrNull(taskMetrics, "Memory Bytes Spilled");
        Long diskBytesSpilled = longOrNull(taskMetrics, "Disk Bytes Spilled");
        if (memoryBytesSpilled == null && diskBytesSpilled == null) {
            return null;
        }

        return new TaskSpillMetrics(valueOrZero(memoryBytesSpilled), valueOrZero(diskBytesSpilled));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private List<SqlPlanOperatorSummary> operatorSummaries(JsonNode sparkPlanInfo) {
        if (sparkPlanInfo == null || sparkPlanInfo.isNull()) {
            return List.of();
        }

        Map<String, Integer> operatorCounts = new LinkedHashMap<>();
        recordOperatorCounts(sparkPlanInfo, operatorCounts);
        return operatorCounts.entrySet().stream()
                .map(entry -> new SqlPlanOperatorSummary(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> {
                    int countComparison = Integer.compare(right.count(), left.count());
                    return countComparison == 0 ? left.name().compareTo(right.name()) : countComparison;
                })
                .toList();
    }

    private void recordOperatorCounts(JsonNode sparkPlanInfo, Map<String, Integer> operatorCounts) {
        String nodeName = textOrNull(sparkPlanInfo, "nodeName");
        if (nodeName != null && !nodeName.isBlank()) {
            operatorCounts.merge(nodeName, 1, Integer::sum);
        }

        JsonNode children = sparkPlanInfo.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                recordOperatorCounts(child, operatorCounts);
            }
        }
    }

    private SqlPlanNode planNode(JsonNode sparkPlanInfo) {
        if (sparkPlanInfo == null || sparkPlanInfo.isNull()) {
            return null;
        }

        List<SqlPlanMetric> metrics = new ArrayList<>();
        JsonNode metricNodes = sparkPlanInfo.get("metrics");
        if (metricNodes != null && metricNodes.isArray()) {
            for (JsonNode metricNode : metricNodes) {
                metrics.add(new SqlPlanMetric(
                        textOrNull(metricNode, "name"),
                        longOrNull(metricNode, "accumulatorId"),
                        textOrNull(metricNode, "metricType")));
            }
        }

        List<SqlPlanNode> children = new ArrayList<>();
        JsonNode childNodes = sparkPlanInfo.get("children");
        if (childNodes != null && childNodes.isArray()) {
            for (JsonNode childNode : childNodes) {
                SqlPlanNode child = planNode(childNode);
                if (child != null) {
                    children.add(child);
                }
            }
        }

        return new SqlPlanNode(
                textOrNull(sparkPlanInfo, "nodeName"),
                textOrNull(sparkPlanInfo, "simpleString"),
                metrics,
                children);
    }

    private record TaskSpillMetrics(long memoryBytesSpilled, long diskBytesSpilled) {}

    private record StageKey(int stageId, int stageAttemptId) {}

    private record TaskAttemptKey(int stageId, int stageAttemptId, long taskIndex) {}

    private record ParsedTaskAttempt(
            int stageId,
            int stageAttemptId,
            Long taskDurationMillis,
            Long shuffleReadBytes,
            TaskSpillMetrics spillMetrics,
            String executorId,
            String host) {}

    private final class SqlExecutionAccumulator {
        private final long id;
        private Long rootExecutionId;
        private String description;
        private String details;
        private Long startTimeMillis;
        private Long endTimeMillis;
        private String physicalPlanDescription;
        private String latestPhysicalPlanDescription;
        private String errorMessage;
        private JsonNode sparkPlanInfo;
        private JsonNode latestSparkPlanInfo;
        private Map<Long, String> sqlMetricValues = new LinkedHashMap<>();

        private SqlExecutionAccumulator(long id) {
            this.id = id;
        }

        private void start(JsonNode event) {
            rootExecutionId = longOrNull(event, "rootExecutionId");
            description = textOrNull(event, "description");
            details = textOrNull(event, "details");
            startTimeMillis = longOrNull(event, "time");
            physicalPlanDescription = textOrNull(event, "physicalPlanDescription");
            latestPhysicalPlanDescription = physicalPlanDescription;
            sparkPlanInfo = event.get("sparkPlanInfo");
            latestSparkPlanInfo = sparkPlanInfo;
        }

        private void update(JsonNode event) {
            String updatedPhysicalPlanDescription = textOrNull(event, "physicalPlanDescription");
            if (updatedPhysicalPlanDescription != null) {
                latestPhysicalPlanDescription = updatedPhysicalPlanDescription;
            }
            JsonNode updatedSparkPlanInfo = event.get("sparkPlanInfo");
            if (updatedSparkPlanInfo != null && !updatedSparkPlanInfo.isNull()) {
                latestSparkPlanInfo = updatedSparkPlanInfo;
            }
        }

        private void end(JsonNode event) {
            endTimeMillis = longOrNull(event, "time");
            errorMessage = textOrNull(event, "errorMessage");
        }

        private void recordDriverAccumulatorUpdates(JsonNode event) {
            JsonNode accumUpdates = event.get("accumUpdates");
            if (accumUpdates == null || !accumUpdates.isArray()) {
                return;
            }

            for (JsonNode accumUpdate : accumUpdates) {
                if (!accumUpdate.isArray() || accumUpdate.size() < 2) {
                    continue;
                }
                sqlMetricValues.put(accumUpdate.get(0).asLong(), accumUpdate.get(1).asText());
            }
        }

        private void recordSqlAccumulables(JsonNode accumulables) {
            if (accumulables == null || !accumulables.isArray()) {
                return;
            }

            for (JsonNode accumulable : accumulables) {
                if (!"sql".equals(textOrNull(accumulable, "Metadata"))) {
                    continue;
                }
                Long accumulatorId = longOrNull(accumulable, "ID");
                String value = textOrNull(accumulable, "Value");
                if (accumulatorId != null && value != null) {
                    sqlMetricValues.put(accumulatorId, value);
                }
            }
        }

        private SqlExecution toSqlExecution() {
            Long durationMillis =
                    startTimeMillis == null || endTimeMillis == null ? null : endTimeMillis - startTimeMillis;
            JsonNode planForSummaries = latestSparkPlanInfo == null ? sparkPlanInfo : latestSparkPlanInfo;
            return new SqlExecution(
                    id,
                    rootExecutionId,
                    description,
                    details,
                    startTimeMillis,
                    endTimeMillis,
                    durationMillis,
                    physicalPlanDescription,
                    latestPhysicalPlanDescription,
                    errorMessage,
                    operatorSummaries(planForSummaries),
                    sparkPlanInfo,
                    latestSparkPlanInfo,
                    planNode(sparkPlanInfo),
                    planNode(latestSparkPlanInfo),
                    sqlMetricValues);
        }
    }
}
