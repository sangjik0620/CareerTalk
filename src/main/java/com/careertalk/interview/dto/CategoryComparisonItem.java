package com.careertalk.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryComparisonItem {
    private Integer user;
    private Integer average;
    private Integer previous;
}