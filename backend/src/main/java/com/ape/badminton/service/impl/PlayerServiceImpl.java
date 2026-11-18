package com.ape.badminton.service.impl;

import com.ape.badminton.common.AuthContext;
import com.ape.badminton.common.BizException;
import com.ape.badminton.entity.Entry;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.EntryMapper;
import com.ape.badminton.mapper.PlayerMapper;
import com.ape.badminton.mapper.TournamentMapper;
import com.ape.badminton.service.PlayerService;
import com.ape.badminton.utils.IdGen;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PlayerServiceImpl implements PlayerService {

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private EntryMapper entryMapper;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Override
    public List<Player> list(String keyword, String team, Boolean seedOnly, String category) {
        QueryWrapper<Player> w = new QueryWrapper<>();
        String me = AuthContext.userId();
        if (me != null) {
            w.eq("creator_id", me);
        }
        if (category != null && !category.trim().isEmpty()) {
            w.eq("category", category.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim();
            w.and(q -> q.like("name", k)
                    .or().like("student_id", k)
                    .or().like("team", k)
                    .or().like("partner_name", k));
        }
        if (team != null && !team.trim().isEmpty()) {
            w.eq("team", team.trim());
        }
        if (Boolean.TRUE.equals(seedOnly)) {
            w.eq("is_seed", true);
        }
        w.orderByDesc("is_seed").orderByAsc("seed_rank").orderByAsc("name");
        return playerMapper.selectList(w);
    }

    @Override
    public Player getById(String playerId) {
        Player p = playerId == null ? null : playerMapper.selectById(playerId);
        // 选手库按账号隔离：只能读取自己库中的选手
        if (p == null) return null;
        String me = AuthContext.userId();
        return me != null && me.equals(p.getCreator_id()) ? p : null;
    }

    @Override
    public boolean add(Player player) {
        normalize(player);
        if (player.getPlayer_id() == null || player.getPlayer_id().isEmpty()) {
            player.setPlayer_id(IdGen.next("p"));
        }
        if (player.getCreator_id() == null || player.getCreator_id().isEmpty()) {
            player.setCreator_id(AuthContext.userId());
        }
        player.setCreate_time(new Date());
        return playerMapper.insert(player) > 0;
    }

    @Override
    public boolean update(Player player) {
        requireOwned(player.getPlayer_id());
        normalize(player);
        // 归属不可被前端改写
        player.setCreator_id(AuthContext.userId());
        return playerMapper.updateById(player) > 0;
    }

    /**
     * 删除选手。
     *
     * 选手会被参赛名单（biz_local_entries）引用。若直接删选手而不清名单，
     * 抽签池里就会出现「有参赛记录但查不到姓名」的空壳行。
     *
     * 策略：
     *   - 选手仍在「未结束」的赛事名单中 → 拒绝删除，提示先移出名单或结束赛事；
     *   - 否则 → 连带清理其历史参赛记录后再删选手。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String playerId) {
        requireOwned(playerId);
        QueryWrapper<Entry> w = new QueryWrapper<>();
        w.eq("player_id", playerId);
        List<Entry> entries = entryMapper.selectList(w);
        int active = 0;
        for (Entry e : entries) {
            Tournament t = tournamentMapper.selectById(e.getTournament_id());
            if (t != null && !"FINISHED".equals(t.getStatus())) active++;
        }
        if (active > 0) {
            throw new BizException(40002, "该选手还是 " + active + " 场未结束赛事的参赛选手，请先移出参赛名单");
        }
        entryMapper.delete(new QueryWrapper<Entry>().eq("player_id", playerId));
        return playerMapper.deleteById(playerId) > 0;
    }

    @Override
    public int batchImport(List<Player> players) {
        if (players == null || players.isEmpty()) return 0;
        String me = AuthContext.userId();
        int ok = 0;
        for (Player p : players) {
            if (p == null) continue;
            if (p.getStudent_id() == null || p.getStudent_id().trim().isEmpty()) continue;
            if (p.getName() == null || p.getName().trim().isEmpty()) continue;
            if (p.getPlayer_id() == null || p.getPlayer_id().isEmpty()) {
                p.setPlayer_id(IdGen.next("p"));
            }
            if (p.getCreator_id() == null || p.getCreator_id().isEmpty()) {
                p.setCreator_id(me);
            }
            normalize(p);
            p.setCreate_time(new Date());
            if (playerMapper.insert(p) > 0) ok++;
        }
        return ok;
    }

    @Override
    public List<String> listTeams() {
        List<Player> all = list(null, null, null, null);
        Set<String> teams = new LinkedHashSet<>();
        for (Player p : all) {
            if (p.getTeam() != null && !p.getTeam().trim().isEmpty()) {
                teams.add(p.getTeam().trim());
            }
        }
        return new ArrayList<>(teams);
    }

    @Override
    public long count() {
        String me = AuthContext.userId();
        if (me == null) return 0;
        return countByCreator(me);
    }

    @Override
    public long countByCreator(String creatorId) {
        if (creatorId == null) return 0;
        QueryWrapper<Player> w = new QueryWrapper<>();
        w.eq("creator_id", creatorId);
        return playerMapper.selectCount(w);
    }

    /** 校验选手存在且属于当前发起者的选手库 */
    private void requireOwned(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            throw new BizException(40001, "缺少选手ID");
        }
        Player exist = playerMapper.selectById(playerId);
        String me = AuthContext.userId();
        if (exist == null || me == null || !me.equals(exist.getCreator_id())) {
            throw new BizException(40400, "选手不存在");
        }
    }

    /** 字段归一化与校验 */
    private void normalize(Player p) {
        if (p.getStudent_id() != null) p.setStudent_id(p.getStudent_id().trim());
        if (p.getName() != null) p.setName(p.getName().trim());
        if (p.getTeam() != null) p.setTeam(p.getTeam().trim());
        if (p.getPartner_name() != null) p.setPartner_name(p.getPartner_name().trim());
        if (p.getPartner_student_id() != null) p.setPartner_student_id(p.getPartner_student_id().trim());
        // 项目类型分支：非法值一律归入男单，避免脏数据把分支打乱
        String c = p.getCategory() == null ? "" : p.getCategory().trim().toUpperCase();
        if (!"MS".equals(c) && !"WS".equals(c) && !"MD".equals(c) && !"WD".equals(c) && !"XD".equals(c)) {
            c = "MS";
        }
        p.setCategory(c);
        if (p.getGender() == null || p.getGender().trim().isEmpty()) p.setGender("M");
        if (p.getLevel() == null) p.setLevel(0);
        if (p.getIs_seed() == null) p.setIs_seed(false);
        if (p.getSeed_rank() == null) p.setSeed_rank(0);
        if (Boolean.TRUE.equals(p.getIs_seed()) && (p.getSeed_rank() == null || p.getSeed_rank() <= 0)) {
            p.setSeed_rank(99);
        }
        if (!Boolean.TRUE.equals(p.getIs_seed())) {
            p.setSeed_rank(0);
        }
    }
}
