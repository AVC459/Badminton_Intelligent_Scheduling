package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 对阵 / 场次
 *
 * stage ：GROUP 小组赛 / KNOCKOUT 淘汰赛 / ROUND_ROBIN 循环赛 / THIRD_PLACE 季军赛
 * status：PENDING 待就绪 / READY 可开赛 / DONE 已完成 / BYE 轮空 / WALKOVER 弃权
 */
@Data
@TableName("biz_local_matches")
public class Match {
    @TableId
    private String match_id;
    private String tournament_id;
    private String stage;
    private Integer round;
    private String round_name;
    private Integer group_no;
    private Integer match_order;
    private String player_a_id;
    private String player_b_id;
    private String winner_id;
    private String loser_id;
    /** 各局比分 JSON，例如 [{"a":21,"b":18},{"a":19,"b":21},{"a":21,"b":15}] */
    private String games_json;
    private Integer score_a;
    private Integer score_b;
    private Integer games_a;
    private Integer games_b;
    private String status;
    private String next_match_id;
    private String next_slot;
    private String loser_next_id;
    private String loser_slot;
    private String remark;
}
