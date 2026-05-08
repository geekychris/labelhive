package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Judgment;
import com.datalabel.domain.Unit;
import com.datalabel.dto.AdminUnitDto;
import com.datalabel.dto.GoldJudgmentBreakdown;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.service.UnitService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs/{jobId}/units")
public class UnitController {

    private final UnitService units;
    private final AppUserRepository users;
    private final JudgmentRepository judgments;
    private final ObjectMapper mapper;

    public UnitController(UnitService units, AppUserRepository users,
                          JudgmentRepository judgments, ObjectMapper mapper) {
        this.units = units; this.users = users;
        this.judgments = judgments; this.mapper = mapper;
    }

    @GetMapping
    public List<AdminUnitDto> list(@PathVariable Long jobId) {
        List<Unit> all = units.list(jobId);
        Map<Long, String> nameById = lookupPromoters(all);
        Map<Long, int[]> goldOutcomes = goldOutcomesByUnit(jobId);
        Map<Long, List<GoldJudgmentBreakdown>> breakdownByUnit = goldBreakdownByUnit(jobId);

        return all.stream().map(u -> {
            int[] mm = goldOutcomes.get(u.getId());
            Integer matches    = u.isGold() && mm != null ? mm[0] : null;
            Integer mismatches = u.isGold() && mm != null ? mm[1] : null;
            List<GoldJudgmentBreakdown> bd = u.isGold() ? breakdownByUnit.get(u.getId()) : null;
            return AdminUnitDto.from(u, nameById.get(u.getPromotedByUserId()), matches, mismatches, bd);
        }).toList();
    }

    @PostMapping
    public List<AdminUnitDto> upload(@PathVariable Long jobId, @RequestBody JsonNode body) {
        return units.upload(jobId, body).stream().map(AdminUnitDto::from).toList();
    }

    @GetMapping("/{unitId}")
    public AdminUnitDto get(@PathVariable Long unitId) {
        Unit u = units.get(unitId);
        String name = u.getPromotedByUserId() == null ? null
                : users.findById(u.getPromotedByUserId()).map(AppUser::getUsername).orElse(null);
        return AdminUnitDto.from(u, name);
    }

    private Map<Long, String> lookupPromoters(List<Unit> all) {
        Map<Long, String> out = new HashMap<>();
        all.stream().map(Unit::getPromotedByUserId).filter(java.util.Objects::nonNull)
                .distinct().forEach(id -> users.findById(id).ifPresent(u -> out.put(id, u.getUsername())));
        return out;
    }

    private Map<Long, int[]> goldOutcomesByUnit(Long jobId) {
        Map<Long, int[]> out = new HashMap<>();
        for (Object[] row : judgments.aggregateGoldOutcomesForJob(jobId)) {
            Long unitId = ((Number) row[0]).longValue();
            int matches    = ((Number) row[1]).intValue();
            int mismatches = ((Number) row[2]).intValue();
            out.put(unitId, new int[]{matches, mismatches});
        }
        return out;
    }

    /**
     * Group gold judgments per unit, then per-answer-shape, into a list of
     * {@code (answer, correct, count)} rows. Stable order: most-frequent first,
     * ties broken by serialized form.
     */
    private Map<Long, List<GoldJudgmentBreakdown>> goldBreakdownByUnit(Long jobId) {
        // unitId → answerKey → entry
        Map<Long, LinkedHashMap<String, Mut>> raw = new HashMap<>();
        for (Judgment j : judgments.findGoldByJobId(jobId)) {
            String key;
            try { key = mapper.writeValueAsString(j.getAnswer()); }
            catch (Exception e) { key = String.valueOf(j.getAnswer()); }
            var perUnit = raw.computeIfAbsent(j.getUnitId(), k -> new LinkedHashMap<>());
            var entry = perUnit.computeIfAbsent(key, k -> new Mut(j.getAnswer(),
                    j.getGoldCorrect() != null && j.getGoldCorrect()));
            entry.count++;
        }
        Map<Long, List<GoldJudgmentBreakdown>> out = new HashMap<>();
        for (var e : raw.entrySet()) {
            var list = new ArrayList<>(e.getValue().values());
            list.sort((a, b) -> Integer.compare(b.count, a.count)); // most frequent first
            List<GoldJudgmentBreakdown> rows = new ArrayList<>(list.size());
            for (Mut m : list) rows.add(new GoldJudgmentBreakdown(m.answer, m.correct, m.count));
            out.put(e.getKey(), rows);
        }
        return out;
    }

    private static class Mut {
        final JsonNode answer; final boolean correct; int count;
        Mut(JsonNode answer, boolean correct) { this.answer = answer; this.correct = correct; this.count = 0; }
    }
}
