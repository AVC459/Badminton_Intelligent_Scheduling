package com.ape.badminton.service.impl;

import com.ape.badminton.common.BizException;
import com.ape.badminton.entity.Entry;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.EntryMapper;
import com.ape.badminton.mapper.PlayerMapper;
import com.ape.badminton.service.EntryService;
import com.ape.badminton.service.TournamentService;
import com.ape.badminton.utils.IdGen;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntryServiceImpl implements EntryService {

    @Autowired
    private EntryMapper entryMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentService tournamentService;

    @Override
    public List<Entry> listByTournament(String tournamentId) {
        QueryWrapper<Entry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId).orderByDesc("seed_flag").orderByAsc("seed_rank").orderByAsc("create_time");
        return entryMapper.selectList(w);
    }

    @Override
    public Entry getById(String entryId) {
        return entryId == null ? null : entryMapper.selectById(entryId);
    }

    @Override
    public int add(String tournamentId, List<String> playerIds) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            throw new BizException(40001, "缺少赛事ID");
        }
        if (playerIds == null || playerIds.isEmpty()) {
            throw new BizException(40001, "请至少选择一名选手");
        }
        int added = 0;
        for (String pid : playerIds) {
            if (pid == null || pid.trim().isEmpty()) continue;
            if (playerMapper.selectById(pid) == null) continue;
            QueryWrapper<Entry> w = new QueryWrapper<>();
            w.eq("tournament_id", tournamentId).eq("player_id", pid);
            if (entryMapper.selectOne(w) != null) continue;
            Entry e = new Entry();
            e.setEntry_id(IdGen.next("e"));
            e.setTournament_id(tournamentId);
            e.setPlayer_id(pid);
            e.setSeed_flag(false);
            e.setSeed_rank(0);
            e.setCreate_time(new Date());
            if (entryMapper.insert(e) > 0) added++;
        }
        if (added > 0) syncParticipantCount(tournamentId);
        return added;
    }

    @Override
    public boolean remove(String entryId) {
        if (entryId == null) return false;
        Entry e = entryMapper.selectById(entryId);
        if (e == null) return false;
        boolean ok = entryMapper.deleteById(entryId) > 0;
        if (ok) syncParticipantCount(e.getTournament_id());
        return ok;
    }

    @Override
    public int clear(String tournamentId) {
        QueryWrapper<Entry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        int n = entryMapper.delete(w);
        if (n > 0) syncParticipantCount(tournamentId);
        return n;
    }

    /**
     * 参赛名单变动后同步赛事的「参赛人数」，保证赛事列表里的人数与名单一致。
     * （抽签编排时也会写入该字段，两者口径相同，均为名单实际人数）
     */
    private void syncParticipantCount(String tournamentId) {
        if (tournamentId == null) return;
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) return;
        t.setActual_participants(countByTournament(tournamentId));
        tournamentService.update(t);
    }

    @Override
    public boolean setSeed(String entryId, Boolean seedFlag, Integer seedRank) {
        Entry e = entryMapper.selectById(entryId);
        if (e == null) throw new BizException(40400, "参赛记录不存在");
        if (Boolean.TRUE.equals(seedFlag)) {
            e.setSeed_flag(true);
            e.setSeed_rank(seedRank == null || seedRank <= 0 ? 99 : seedRank);
        } else {
            e.setSeed_flag(false);
            e.setSeed_rank(0);
        }
        return entryMapper.updateById(e) > 0;
    }

    @Override
    public List<Player> players(String tournamentId) {
        List<Entry> entries = listByTournament(tournamentId);
        Map<String, Player> cache = new HashMap<>();
        List<Player> players = new ArrayList<>();
        for (Entry e : entries) {
            Player p = cache.get(e.getPlayer_id());
            if (p == null) {
                p = playerMapper.selectById(e.getPlayer_id());
                if (p == null) continue;
                cache.put(e.getPlayer_id(), p);
            }
            // 赛事级种子覆盖选手库默认种子（同一实例可能被多个赛事复用，做一次浅拷贝）
            Player view = new Player();
            view.setPlayer_id(p.getPlayer_id());
            view.setCreator_id(p.getCreator_id());
            view.setCategory(p.getCategory());
            view.setStudent_id(p.getStudent_id());
            view.setName(p.getName());
            view.setGender(p.getGender());
            view.setTeam(p.getTeam());
            view.setPhone(p.getPhone());
            view.setLevel(p.getLevel());
            view.setPartner_name(p.getPartner_name());
            view.setPartner_student_id(p.getPartner_student_id());
            view.setCreate_time(p.getCreate_time());
            view.setIs_seed(Boolean.TRUE.equals(e.getSeed_flag()));
            view.setSeed_rank(e.getSeed_rank() == null ? 0 : e.getSeed_rank());
            players.add(view);
        }
        return players;
    }

    @Override
    public List<String> playerIds(String tournamentId) {
        List<String> ids = new ArrayList<>();
        for (Entry e : listByTournament(tournamentId)) {
            ids.add(e.getPlayer_id());
        }
        return ids;
    }

    @Override
    public int countByTournament(String tournamentId) {
        QueryWrapper<Entry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        return Math.toIntExact(entryMapper.selectCount(w));
    }
}
