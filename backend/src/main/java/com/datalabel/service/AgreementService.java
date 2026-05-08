package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgreementService {

    private final JobRepository jobs;
    private final UnitRepository units;
    private final JudgmentRepository judgments;
    private final WorkerTrustRepository trusts;
    private final TemplateRegistry templates;

    public AgreementService(JobRepository jobs, UnitRepository units, JudgmentRepository judgments,
                            WorkerTrustRepository trusts, TemplateRegistry templates) {
        this.jobs = jobs; this.units = units; this.judgments = judgments;
        this.trusts = trusts; this.templates = templates;
    }

    public Map<String, Object> jobReport(Long jobId) {
        Job job = jobs.findById(jobId).orElseThrow();
        LabelingTemplate template = templates.get(job.getTemplateType());

        List<Unit> all = units.findByJobId(jobId);
        long total = all.size();
        long gold = all.stream().filter(Unit::isGold).count();
        long completed = all.stream().filter(u -> u.getStatus() == UnitStatus.COMPLETED).count();

        // Mean pairwise agreement across non-gold completed units (a la inter-rater agreement).
        double sum = 0; int contributingUnits = 0;
        for (Unit u : all) {
            if (u.isGold()) continue;
            List<Judgment> js = judgments.findByUnitId(u.getId());
            if (js.size() < 2) continue;
            double s = 0; int p = 0;
            for (int i = 0; i < js.size(); i++)
                for (int k = i + 1; k < js.size(); k++) {
                    s += template.agreementScore(job.getTemplateConfig(),
                            js.get(i).getAnswer(), js.get(k).getAnswer());
                    p++;
                }
            sum += (s / p);
            contributingUnits++;
        }
        double meanAgreement = contributingUnits == 0 ? 0 : sum / contributingUnits;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("totalUnits", total);
        out.put("goldUnits", gold);
        out.put("completedUnits", completed);
        out.put("meanPairwiseAgreement", round(meanAgreement));
        out.put("agreementMethod", "pairwise (template-defined: %s)".formatted(job.getTemplateType()));
        out.put("workerTrust", trusts.findByJobId(jobId).stream().map(t -> Map.of(
                "userId", t.getUserId(),
                "goldAttempts", t.getGoldAttempts(),
                "goldCorrect", t.getGoldCorrect(),
                "accuracy", t.getAccuracy(),
                "status", t.getStatus()
        )).toList());

        if (TemplateType.SEARCH_RELEVANCE.equals(job.getTemplateType())) {
            out.put("ndcg", computeNdcg(job, all));
        }
        return out;
    }

    /**
     * Live job-monitor view: completeness, throughput, per-worker leaderboard.
     * Distinct from {@link #jobReport} in that it focuses on operational metrics
     * (how fast / how much) rather than agreement statistics.
     */
    public Map<String, Object> monitor(Long jobId) {
        Job job = jobs.findById(jobId).orElseThrow();
        List<Unit> all = units.findByJobId(jobId);

        long total = all.size();
        long gold = all.stream().filter(Unit::isGold).count();
        long completed = all.stream().filter(u -> u.getStatus() == UnitStatus.COMPLETED).count();
        long openNonGold = all.stream().filter(u -> !u.isGold() && u.getStatus() == UnitStatus.OPEN).count();

        long judgmentsCollected = all.stream().filter(u -> !u.isGold()).mapToLong(Unit::getJudgmentCount).sum();
        long judgmentsNeeded = (total - gold) * job.getMinJudgments();
        double percentComplete = judgmentsNeeded == 0 ? 1.0 : Math.min(1.0, (double) judgmentsCollected / judgmentsNeeded);

        // Per-worker stats from raw judgments — uses one query then groups in memory.
        List<Judgment> allJudgments = judgments.findByJobId(jobId);
        Map<Long, List<Judgment>> byUser = new HashMap<>();
        for (Judgment j : allJudgments) byUser.computeIfAbsent(j.getUserId(), k -> new ArrayList<>()).add(j);

        Map<Long, WorkerTrust> trustByUser = new HashMap<>();
        for (WorkerTrust t : trusts.findByJobId(jobId)) trustByUser.put(t.getUserId(), t);

        List<Map<String, Object>> perWorker = new ArrayList<>();
        for (var e : byUser.entrySet()) {
            List<Judgment> theirs = e.getValue();
            int n = theirs.size();
            long withTime = theirs.stream().filter(j -> j.getTimeMs() != null).count();
            double meanMs = withTime == 0 ? 0
                    : theirs.stream().filter(j -> j.getTimeMs() != null).mapToInt(Judgment::getTimeMs).sum() / (double) withTime;
            double throughputPerHour = meanMs <= 0 ? 0 : 3_600_000.0 / meanMs;
            WorkerTrust t = trustByUser.get(e.getKey());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", e.getKey());
            row.put("judgmentsTotal", n);
            row.put("goldAttempts", t == null ? 0 : t.getGoldAttempts());
            row.put("goldCorrect",  t == null ? 0 : t.getGoldCorrect());
            row.put("accuracy",     t == null ? null : t.getAccuracy());
            row.put("status",       t == null ? "OK" : t.getStatus().name());
            row.put("meanTimeMs",   Math.round(meanMs));
            row.put("throughputPerHour", Math.round(throughputPerHour * 10) / 10.0);
            perWorker.add(row);
        }
        perWorker.sort((a, b) -> Integer.compare((Integer) b.get("judgmentsTotal"), (Integer) a.get("judgmentsTotal")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("totalUnits", total);
        out.put("goldUnits", gold);
        out.put("completedUnits", completed);
        out.put("openNonGoldUnits", openNonGold);
        out.put("judgmentsCollected", judgmentsCollected);
        out.put("judgmentsNeeded", judgmentsNeeded);
        out.put("percentComplete", round(percentComplete));
        out.put("workers", perWorker);
        return out;
    }

    /** Where did this worker disagree with gold? */
    public List<Map<String, Object>> goldMistakes(Long jobId, Long userId) {
        Job job = jobs.findById(jobId).orElseThrow();
        LabelingTemplate template = templates.get(job.getTemplateType());

        List<Unit> goldUnits = units.findByJobId(jobId).stream().filter(Unit::isGold).toList();
        if (goldUnits.isEmpty()) return List.of();
        Map<Long, Unit> byId = new HashMap<>();
        for (Unit u : goldUnits) byId.put(u.getId(), u);

        List<Judgment> theirGoldJudgments = judgments.findByUserIdAndWasGoldTrueAndUnitIdIn(
                userId, goldUnits.stream().map(Unit::getId).toList());

        List<Map<String, Object>> mistakes = new ArrayList<>();
        for (Judgment j : theirGoldJudgments) {
            Unit u = byId.get(j.getUnitId());
            if (u == null) continue;
            boolean correct = template.answersEqual(job.getTemplateConfig(), u.getGoldAnswer(), j.getAnswer());
            if (!correct) {
                mistakes.add(Map.of(
                        "unitId", u.getId(),
                        "payload", u.getPayload(),
                        "yourAnswer", j.getAnswer(),
                        "goldAnswer", u.getGoldAnswer(),
                        "explanation", u.getGoldExplanation() == null ? "" : u.getGoldExplanation()
                ));
            }
        }
        return mistakes;
    }

    /**
     * Per-query NDCG using each unit's payload "queryId" + worker median grade vs gold grade.
     */
    private Map<String, Object> computeNdcg(Job job, List<Unit> all) {
        // Group units by queryId (config-declared field, default "queryId").
        String queryField = job.getTemplateConfig().path("queryField").asText("queryId");

        Map<String, List<Unit>> byQuery = new LinkedHashMap<>();
        for (Unit u : all) {
            String q = u.getPayload().path(queryField).asText(null);
            if (q == null) continue;
            byQuery.computeIfAbsent(q, k -> new ArrayList<>()).add(u);
        }

        List<Map<String, Object>> per = new ArrayList<>();
        double sum = 0; int counted = 0;
        for (var e : byQuery.entrySet()) {
            List<Unit> qUnits = e.getValue();
            List<int[]> pairs = new ArrayList<>();
            for (Unit u : qUnits) {
                Integer ideal = u.isGold() ? u.getGoldAnswer().get("grade").asInt() : null;
                List<Judgment> js = judgments.findByUnitId(u.getId()).stream()
                        .filter(j -> !j.isWasGold()).toList();
                if (js.isEmpty()) continue;
                List<Integer> grades = new ArrayList<>();
                for (Judgment j : js) grades.add(j.getAnswer().path("grade").asInt());
                Collections.sort(grades);
                int predicted = grades.get(grades.size()/2);
                pairs.add(new int[]{predicted, ideal == null ? predicted : ideal});
            }
            if (pairs.size() < 2) continue;
            double ndcg = ndcg(pairs);
            per.add(Map.of("query", e.getKey(), "ndcg", round(ndcg), "results", pairs.size()));
            sum += ndcg; counted++;
        }
        return Map.of(
                "perQuery", per,
                "meanNdcg", counted == 0 ? 0 : round(sum / counted),
                "queries", per.size()
        );
    }

    /** pairs: [predictedGrade, idealGrade], DCG ranks results by predicted grade. */
    private static double ndcg(List<int[]> pairs) {
        // Predicted ranking: high predicted -> top.
        List<int[]> byPredicted = new ArrayList<>(pairs);
        byPredicted.sort((a, b) -> b[0] - a[0]);
        // Ideal ranking: high ideal -> top.
        List<Integer> ideals = new ArrayList<>();
        for (int[] p : pairs) ideals.add(p[1]);
        ideals.sort(Comparator.reverseOrder());

        double dcg = 0, idcg = 0;
        for (int i = 0; i < pairs.size(); i++) {
            double rel = byPredicted.get(i)[1];
            double iRel = ideals.get(i);
            double denom = log2(i + 2);
            dcg += (Math.pow(2, rel) - 1) / denom;
            idcg += (Math.pow(2, iRel) - 1) / denom;
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }
    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
