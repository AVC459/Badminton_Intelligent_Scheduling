package com.ape.badminton.dto;

import com.ape.badminton.algorithm.DrawStep;
import com.ape.badminton.algorithm.FairnessReport;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽签编排结果（返回给小程序展示与留档）
 */
@Data
public class DrawOutcomeVo {
    private String tournament_id;
    private String format;
    private String format_label;
    /** 签位规模 */
    private int bracket_size;
    /** 轮空数 */
    private int bye_count;
    /** 轮数 */
    private int rounds;
    /** 参赛人数 */
    private int participants;
    /** 小组数（小组赛制） */
    private int group_count;
    /** 生成的场次总数 */
    private int match_count;
    /** 签位表：下标为签位号 */
    private List<String> draw_positions = new ArrayList<>();
    /** 签位表对应的展示信息（签位号 → 选手姓名/单位） */
    private List<SeedSlot> slots = new ArrayList<>();
    /** 抽签过程留痕 */
    private List<DrawStep> steps = new ArrayList<>();
    /** 公平性报告 */
    private FairnessReport fairness;
    /** 是否已完全规避首轮同单位相遇 */
    private boolean avoidance_resolved;

    @Data
    public static class SeedSlot {
        private int slot;
        private String player_id;
        private String player_name;
        private String team;
        private boolean seed;
        private int seed_rank;
        private boolean bye;
    }
}
