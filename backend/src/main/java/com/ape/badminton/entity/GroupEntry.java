package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 小组分组记录（小组赛制）
 */
@Data
@TableName("biz_local_groups")
public class GroupEntry {
    @TableId
    private String group_id;
    private String tournament_id;
    private Integer group_no;
    private String player_id;
    private Integer seed_position;
}
