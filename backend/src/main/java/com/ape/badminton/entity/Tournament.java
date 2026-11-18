package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 赛事
 *
 * creator_id 表示发起者，用于「我创建的赛事」；
 * format 取值：
 *   SINGLE_ELIM     单淘汰（支持季军赛）
 *   ROUND_ROBIN     单循环（积分排名）
 *   GROUP_KNOCKOUT  小组循环 + 交叉淘汰
 * status 取值：
 *   DRAFT 草稿 / READY 待编排 / DRAWN 已编排 / ONGOING 进行中 / FINISHED 已结束
 */
@Data
@TableName("biz_local_tournaments")
public class Tournament {
    @TableId
    private String tournament_id;
    /** 发起者ID */
    private String creator_id;
    private String title;
    private String event_type;
    private String format;
    private String status;
    private Integer actual_participants;
    private Integer bracket_size;
    private Integer bye_count;
    private Integer seed_count;
    private Integer group_count;
    private Integer group_advance;
    private Integer best_of;
    private Integer points_per_game;
    private Integer points_cap;
    private Boolean avoid_same_team;
    private String champion_id;
    private String runner_up_id;
    private String third_id;
    private String remark;
    private Date create_time;
}
