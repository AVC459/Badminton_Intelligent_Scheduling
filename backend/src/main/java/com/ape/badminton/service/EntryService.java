package com.ape.badminton.service;

import com.ape.badminton.entity.Entry;
import com.ape.badminton.entity.Player;

import java.util.List;

/**
 * 参赛名单服务：比赛发起者从选手库选拔选手进入赛事，作为抽签池。
 */
public interface EntryService {

    /** 某赛事的参赛名单（已带选手档案与赛事级种子信息） */
    List<Entry> listByTournament(String tournamentId);

    /** 按参赛记录ID查询（用于归属校验） */
    Entry getById(String entryId);

    /**
     * 批量加入参赛名单
     *
     * @return 实际新增条数（已存在的会被忽略或更新种子标记）
     */
    int add(String tournamentId, List<String> playerIds);

    /** 移出参赛名单 */
    boolean remove(String entryId);

    /** 清空某赛事参赛名单 */
    int clear(String tournamentId);

    /** 设置 / 取消赛事级种子 */
    boolean setSeed(String entryId, Boolean seedFlag, Integer seedRank);

    /** 参赛选手档案（抽签池），赛事级种子信息已覆盖到 Player 视图上 */
    List<Player> players(String tournamentId);

    /** 参赛名单中的选手ID集合 */
    List<String> playerIds(String tournamentId);

    int countByTournament(String tournamentId);
}
