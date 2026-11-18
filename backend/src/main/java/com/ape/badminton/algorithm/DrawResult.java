package com.ape.badminton.algorithm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽签结果 / 规模试算结果
 */
@Data
public class DrawResult {
    /** 赛制：SINGLE_ELIM / ROUND_ROBIN / GROUP_KNOCKOUT */
    private String format;
    /** 参赛人数 */
    private int participants;
    /** 签位规模 2^n */
    private int bracketSize;
    /** 轮空数 */
    private int byeCount;
    /** 轮数 */
    private int rounds;
    /** 小组数（仅分组赛制） */
    private int groupCount;
    /** 每组出线人数（仅分组赛制） */
    private int groupAdvance;
    /** 预计总场次数 */
    private int matchCount;
    /** 签位表：下标为签位号，值为 playerId / "BYE" / null */
    private List<String> drawPositions = new ArrayList<>();
    /** 抽签过程留痕 */
    private List<DrawStep> steps = new ArrayList<>();
    /** 公平性报告 */
    private FairnessReport fairness;
    /** 是否成功规避全部首轮同单位相遇 */
    private boolean avoidanceResolved;

    public void addStep(String step, String message) {
        steps.add(new DrawStep(steps.size() + 1, step, message, String.join("|", drawPositions.stream()
                .map(v -> v == null ? "-" : v).toArray(String[]::new))));
    }
}
