package com.ape.badminton.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 场次展示对象（含选手姓名、单位、结果文案，便于小程序直接渲染）
 */
@Data
public class MatchVo {
    private String match_id;
    private String tournament_id;
    private String stage;
    private String stage_label;
    private Integer round;
    private String round_name;
    private Integer group_no;
    private String group_name;
    private Integer match_order;

    private String player_a_id;
    private String player_a_name;
    private String player_a_team;
    private String player_b_id;
    private String player_b_name;
    private String player_b_team;

    private String winner_id;
    private String winner_name;
    private String games_json;
    private List<GameScoreVo> games = new ArrayList<>();
    private Integer score_a;
    private Integer score_b;
    private Integer games_a;
    private Integer games_b;

    private String status;
    private String status_label;

    private String next_match_id;
    private String next_slot;
    private String loser_next_id;
    private String loser_slot;
    private String remark;

    @Data
    public static class GameScoreVo {
        private Integer a;
        private Integer b;
    }
}
