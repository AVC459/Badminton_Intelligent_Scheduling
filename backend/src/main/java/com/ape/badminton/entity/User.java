package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 比赛发起者（系统唯一身份）
 *
 * v3.0 起取消 ADMIN / REFEREE / NORMAL 角色分类：注册账号即是比赛发起者，
 * 可创建多种类型赛事、维护自己的选手库、执行抽签编排与比分录入。
 */
@Data
@TableName("sys_local_users")
public class User {
    @TableId
    private String user_id;
    private String username;

    /**
     * 密码：只允许写入（接收前端注册/登录请求），序列化返回给前端时自动忽略，防止泄露
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String real_name;
    private String phone;
    private Date create_time;
}
