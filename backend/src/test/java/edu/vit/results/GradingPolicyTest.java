package edu.vit.results;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.vit.results.service.GradingPolicy;
import edu.vit.results.service.GradingPolicy.Grade;
import org.junit.jupiter.api.Test;

class GradingPolicyTest {

    @Test
    void totalWeightsMseThirtyAndEseSeventy() {
        assertEquals(87.0, GradingPolicy.total(80, 90));
        assertEquals(100.0, GradingPolicy.total(100, 100));
        assertEquals(0.0, GradingPolicy.total(0, 0));
        assertEquals(90.0, GradingPolicy.total(90, 90)); // no 0.3/0.7 float drift
        assertEquals(59.05, GradingPolicy.total(54.5, 61.0));
    }

    @Test
    void gradeBoundaries() {
        assertEquals(Grade.O, GradingPolicy.grade(90, 90));
        assertEquals(Grade.A_PLUS, GradingPolicy.grade(89.99, 90));
        assertEquals(Grade.A, GradingPolicy.grade(70, 70));
        assertEquals(Grade.B_PLUS, GradingPolicy.grade(60, 60));
        assertEquals(Grade.B, GradingPolicy.grade(50, 50));
        assertEquals(Grade.C, GradingPolicy.grade(45, 45));
        assertEquals(Grade.P, GradingPolicy.grade(40, 40));
        assertEquals(Grade.F, GradingPolicy.grade(39.99, 45));
    }

    @Test
    void failsWhenEseIsBelowFortyEvenWithAGoodTotal() {
        double total = GradingPolicy.total(100, 39); // 57.3
        assertEquals(Grade.F, GradingPolicy.grade(total, 39));
    }
}
