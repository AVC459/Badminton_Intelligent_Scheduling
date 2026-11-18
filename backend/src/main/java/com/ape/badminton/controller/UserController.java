package com.ape.badminton.controller;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.AuthContext;
import com.ape.badminton.common.BizException;
import com.ape.badminton.dto.LoginResult;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.entity.User;
import com.ape.badminton.service.PlayerService;
import com.ape.badminton.service.TournamentService;
import com.ape.badminton.service.UserService;
import com.ape.badminton.utils.IdGen;
import com.ape.badminton.utils.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 比赛发起者账号接口
 *
 * 系统只有一种身份：注册账号后即为「比赛发起者」，无角色分级、无权限差异。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private TournamentService tournamentService;

    /** 登录：校验用户名密码，成功后签发 token */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@RequestBody User req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            throw new BizException(40001, "用户名和密码不能为空");
        }
        User user = userService.getUserByUsername(req.getUsername());
        if (user == null || !req.getPassword().equals(user.getPassword())) {
            throw new BizException(40001, "用户名或密码错误");
        }
        String token = TokenUtil.createToken(user);
        return ApiResponse.ok(new LoginResult(token, user));
    }

    /** 注册：注册成功即为比赛发起者 */
    @PostMapping("/register")
    public ApiResponse<Boolean> register(@RequestBody User user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()
                || user.getPassword() == null || user.getPassword().length() < 6) {
            throw new BizException(40001, "用户名不能为空，密码至少 6 位");
        }
        String username = user.getUsername().trim();
        if (userService.getUserByUsername(username) != null) {
            throw new BizException(40002, "用户名已存在");
        }
        user.setUsername(username);
        user.setUser_id(IdGen.next("u"));
        user.setCreate_time(new Date());
        return ApiResponse.ok(userService.addUser(user));
    }

    /** 当前登录的比赛发起者 */
    @GetMapping("/current")
    public ApiResponse<User> current() {
        User u = AuthContext.get();
        if (u == null) throw new BizException(40100, "未登录");
        return ApiResponse.ok(u);
    }

    /**
     * 我的数据概览：选手库规模 + 我创建的赛事 + 权限清单
     * 供「我的」页面一次性取全所需信息。
     */
    @GetMapping("/profile")
    public ApiResponse<java.util.Map<String, Object>> profile() {
        User u = AuthContext.get();
        if (u == null) throw new BizException(40100, "未登录");

        List<Player> players = playerService.list(null, null, null, null);
        // 只统计本人创建的赛事，避免把他人赛事计入我的数据
        List<Tournament> tournaments = tournamentService.list(null, null, true);
        int finished = 0;
        for (Tournament t : tournaments) {
            if ("FINISHED".equals(t.getStatus())) finished++;
        }

        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("user", u);
        m.put("player_count", players.size());
        m.put("tournament_count", tournaments.size());
        m.put("finished_count", finished);
        m.put("role_label", "比赛发起者");
        m.put("permissions", java.util.Arrays.asList(
                "创建各类羽毛球赛事（单淘汰 / 小组循环 + 交叉淘汰）",
                "批量导入 Excel 名单，或手动录入选手档案",
                "维护个人选手库：编辑、标记种子、删除",
                "执行智能抽签编排，自动分区并规避同单位",
                "录入与更正比分，自动判定胜负与晋级"
        ));
        return ApiResponse.ok(m);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            TokenUtil.remove(auth.substring(7));
        }
        return ApiResponse.ok(true);
    }
}
