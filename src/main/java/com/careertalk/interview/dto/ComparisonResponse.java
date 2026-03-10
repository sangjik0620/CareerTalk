package com.careertalk.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResponse {
    private Integer percentileRank;
    private List<ScoreHistoryItem> scoreHistory;
    private Map<String, CategoryComparisonItem> categoryComparison;
}