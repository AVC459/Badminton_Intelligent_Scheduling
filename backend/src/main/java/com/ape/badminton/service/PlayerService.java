package com.ape.badminton.service;

import com.ape.badminton.entity.Player;

import java.util.List;

public interface PlayerService {

    /**
     * 我的选手库（按当前登录发起者隔离）
     *
     * @param category 项目类型分支 MS/WS/MD/WD/XD，为空表示不按分支过滤
     */
    List<Player> list(String keyword, String team, Boolean seedOnly, String category);

    Player getById(String playerId);

    boolean add(Player player);

    boolean update(Player player);

    boolean delete(String playerId);

    /** 批量导入，返回成功条数（自动归属当前发起者） */
    int batchImport(List<Player> players);

    /** 我的选手库中出现的全部单位（去重，用于筛选与统计） */
    List<String> listTeams();

    long count();

    /** 指定发起者的选手库规模 */
    long countByCreator(String creatorId);
}
