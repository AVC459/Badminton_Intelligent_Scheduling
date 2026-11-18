package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 选手库条目
 *
 * 选手库按项目类型分为 5 个分支（category）：
 *   MS 男单 / WS 女单 / MD 男双 / WD 女双 / XD 混双
 *
 * 单打分支一条记录 = 一名选手；双打分支一条记录 = 一个组合
 * （name 存「主选手/搭档」，搭档信息另存 partner_name / partner_student_id）。
 *
 * creator_id 表示该条目归属于哪位比赛发起者（选手库按发起者隔离）；
 * team 是同单位规避算法的核心依据。
 *
 * partner_team 是搭档自己的单位。混双/跨院组队时两人学院往往不同，
 * 因此搭档单位必须独立存储，不能默认沿用主选手的 team。
 */
@Data
@TableName("biz_local_players")
public class Player {
    @TableId
    private String player_id;
    /** 所属发起者（选手库归属） */
    private String creator_id;
    /** 项目类型分支：MS/WS/MD/WD/XD */
    private String category;
    /** 学号（双打为主选手学号） */
    private String student_id;
    /** 姓名（双打为「主选手/搭档」） */
    private String name;
    /** M 男 / F 女（双打为组合主选手性别） */
    private String gender;
    /** 所属单位/学院 */
    private String team;
    private String phone;
    /** 水平分，用于公平性度量 */
    private Integer level;
    private Boolean is_seed;
    private Integer seed_rank;
    /** 双打搭档姓名（单打为空） */
    private String partner_name;
    /** 双打搭档学号（单打为空） */
    private String partner_student_id;
    /** 双打搭档所在单位（混双/跨院组队时与 team 不同，单打为空） */
    private String partner_team;
    private Date create_time;
}
