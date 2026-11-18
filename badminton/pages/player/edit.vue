<template>
  <view class="container container--with-footer">
    <!-- ==================== 批量导入 ==================== -->
    <block v-if="mode === 'batch'">
      <wb-section
        class="block"
        title="导入方式"
        icon="upload"
        desc="支持 Excel 名单文件，也可以从表格里直接复制粘贴；项目分支会按名单内容自动判定"
      >
        <view class="seg">
          <view
            class="seg__item"
            :class="{ 'seg__item--active': source === 'excel' }"
            @click="source = 'excel'"
          >
            Excel 文件
          </view>
          <view
            class="seg__item"
            :class="{ 'seg__item--active': source === 'text' }"
            @click="source = 'text'"
          >
            粘贴名单
          </view>
        </view>

        <!-- Excel -->
        <view v-if="source === 'excel'" class="dropzone" @click="chooseExcel">
          <wb-icon name="fileText" :size="26" color="#1e40af" />
          <text class="dropzone__title t-ellipsis">{{ fileName || '点击选择 Excel 文件' }}</text>
          <text class="dropzone__desc">支持 .xlsx / .xls，读取第一个工作表</text>
        </view>

        <!-- 粘贴文本 -->
        <block v-else>
          <textarea
            class="field__input field__textarea paste"
            v-model="raw"
            placeholder="20230001,张三,男,计算机学院,85&#10;20230002,李四,女,外国语学院,78&#10;20230003,王五"
            placeholder-class="ph"
            :maxlength="-1"
          />
          <view class="paste__foot">
            <text class="paste__count t-num">{{ rawLines }} 行输入</text>
            <view class="paste__demo" @click="fillDemo">
              <wb-icon name="sparkle" :size="12" color="#1e40af" />
              <text class="paste__demo-text">填入示例</text>
            </view>
          </view>
        </block>
      </wb-section>

      <!-- 智能识别 -->
      <wb-section class="block" title="智能识别" icon="sparkle" desc="列顺序不定、表头别名、性别写法不一时交由本地大模型归一化">
        <view class="switch-row">
          <view class="switch-row__text">
            <text class="switch-row__title">本地大模型识别（Ollama）</text>
            <text class="switch-row__desc">无法连接时自动降级为规则解析，不影响导入</text>
          </view>
          <view class="sw" :class="{ 'sw--on': useAi }" @click="useAi = !useAi">
            <view class="sw__knob" />
          </view>
        </view>

        <view class="ai-status" :class="ai.available ? 'ai-status--ok' : 'ai-status--off'">
          <wb-icon
            :name="ai.available ? 'check' : 'info'"
            :size="13"
            :color="ai.available ? '#15803d' : '#8b98b0'"
          />
          <text class="ai-status__text">{{ ai.message }}</text>
        </view>

        <view class="btn btn--primary btn--block parse-btn" :class="{ 'btn--disabled': parsing }" @click="parse">
          <text class="btn__text">{{ parsing ? '识别中…' : '开始识别' }}</text>
        </view>

        <!-- 各项目分布：完全取自名单里的「参赛类别」列，系统不推断 -->
        <view v-if="groups.length" class="detect">
          <view class="detect__row">
            <wb-icon name="check" :size="13" color="#15803d" />
            <text class="detect__title">按「参赛类别」列识别到 {{ groups.length }} 个项目</text>
          </view>
          <view class="detect__chips">
            <view v-for="g in groups" :key="g.category" class="dchip dchip--active">
              <text class="dchip__text dchip__text--active">{{ g.label }} {{ g.count }}</text>
            </view>
          </view>
          <text class="detect__hint">
            每行按自己那格「参赛类别」落到对应分支；同一人出现在多行会分别导入，互不影响
          </text>
        </view>

        <!-- 缺少类别列：明确说清怎么改，不再靠猜 -->
        <view v-if="categoryError" class="detect detect--err">
          <view class="detect__row">
            <wb-icon name="alert" :size="13" color="#b45309" />
            <text class="detect__title detect__title--warn">名单缺少「参赛类别」列</text>
          </view>
          <text class="detect__hint">{{ categoryError }}</text>
        </view>

        <view v-else-if="categoryWarning" class="detect detect--err">
          <view class="detect__row">
            <wb-icon name="alert" :size="13" color="#b45309" />
            <text class="detect__title detect__title--warn">部分行未导入</text>
          </view>
          <text class="detect__hint">{{ categoryWarning }}</text>
        </view>
      </wb-section>

      <!-- 种子自动标记 -->
      <wb-section class="block" title="种子设置" icon="star" desc="按水平分从高到低自动标记种子，省去逐个设置">
        <view class="switch-row">
          <view class="switch-row__text">
            <text class="switch-row__title">自动标记种子</text>
            <text class="switch-row__desc">按水平分降序取前 N 名标记为种子</text>
          </view>
          <view class="sw" :class="{ 'sw--on': autoSeed }" @click="autoSeed = !autoSeed">
            <view class="sw__knob" />
          </view>
        </view>
        <view v-if="autoSeed" class="seed-n">
          <text class="seed-n__label">种子人数</text>
          <view class="seed-n__ctrl">
            <view class="seed-n__btn" @click="bumpSeed(-1)">
              <wb-icon name="minus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
            <text class="seed-n__value t-num">{{ seedN }}</text>
            <view class="seed-n__btn" @click="bumpSeed(1)">
              <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </wb-section>

      <!-- 解析预览 -->
      <wb-section
        v-if="parsed.length"
        class="block"
        title="识别结果"
        icon="list"
        :badge="validParsed.length + ' 人可导入'"
      >
        <view class="preview">
          <view v-for="(p, i) in parsedPreview" :key="i" class="prow" :class="{ 'prow--bad': !p.ok }">
            <text class="prow__no t-num">{{ i + 1 }}</text>
            <view class="grow">
              <text class="prow__cat" :class="{ 'prow__cat--bad': !p.catOk }">{{ p.cat }}</text>
              <text class="prow__name t-ellipsis">{{ p.name || '（缺姓名）' }}</text>
              <text class="prow__meta t-ellipsis">{{ p.desc }}</text>
            </view>
            <wb-tag v-if="p.seed" text="种子" tone="accent" size="sm" />
            <wb-icon v-if="!p.ok" name="alert" :size="15" color="#dc2626" />
          </view>
        </view>
        <view v-if="parsed.length > 12" class="preview__more">
          仅预览前 12 条，其余 {{ parsed.length - 12 }} 条将在导入时一并处理
        </view>
        <view v-if="skipped.length" class="preview__more preview__more--warn">
          {{ skipped.length }} 行不符合导入要求（类别识别不了 / 缺学号 / 缺姓名），将自动跳过
        </view>
      </wb-section>

      <view v-else class="hintcard">
        <wb-icon name="info" :size="14" color="#8b98b0" />
        <text class="hintcard__text">
          Excel 需包含「参赛类别」列，每行填 男单 / 女单 / 男双 / 女双 / 混双 之一；
          双打把组合写在姓名格里（如「高科/黄翊轩」）。一份表可混放多个项目，同一人也可参加多项。
          学号与姓名必填。
        </text>
      </view>

      <view class="spacer" />

      <view class="footer-bar">
        <view class="btn btn--ghost btn--sm footer-btn" @click="back">
          <text class="btn__text-ghost">返回</text>
        </view>
        <view
          class="btn btn--primary btn--sm footer-btn footer-btn--main"
          :class="{ 'btn--disabled': submitting || !validParsed.length }"
          @click="doImport"
        >
          <text class="btn__text">
            {{ submitting ? '导入中…' : '导入 ' + validParsed.length + ' 位选手' }}
          </text>
        </view>
      </view>
    </block>

    <!-- ==================== 单个新增 / 编辑 ==================== -->
    <block v-else>
      <wb-section
        class="block"
        title="项目分支"
        icon="layers"
        desc="双打分支的一条记录代表一个组合，需要填写搭档信息"
      >
        <view class="cats">
          <view
            v-for="c in categoryOptions"
            :key="c.value"
            class="cat"
            :class="{ 'cat--active': category === c.value }"
            @click="category = c.value"
          >
            <text class="cat__t">{{ c.label }}</text>
          </view>
        </view>
        <text v-if="doublesMode" class="field__helper">{{ doublesTip }}</text>
      </wb-section>

      <wb-section
        class="block"
        title="基本档案"
        icon="user"
        :desc="doublesMode ? '组合名与主选手信息（抽签与公示依据）' : '学号与姓名是抽签与公示的唯一依据'"
      >
        <view class="field">
          <text class="field__label field__label--required">学号</text>
          <input
            class="field__input"
            v-model="form.student_id"
            placeholder="如 20230001"
            placeholder-class="ph"
            :maxlength="30"
          />
          <text v-if="errors.student_id" class="field__error">{{ errors.student_id }}</text>
        </view>

        <view class="field">
          <text class="field__label field__label--required">姓名</text>
          <input
            class="field__input"
            v-model="form.name"
            placeholder="请输入真实姓名"
            placeholder-class="ph"
            :maxlength="20"
          />
          <text v-if="errors.name" class="field__error">{{ errors.name }}</text>
        </view>

        <!-- 双打分支：一条记录 = 一个组合，需要搭档信息 -->
        <block v-if="doublesMode">
          <view class="field">
            <text class="field__label field__label--required">搭档姓名</text>
            <input
              class="field__input"
              v-model="form.partner_name"
              placeholder="双打搭档姓名"
              placeholder-class="ph"
              :maxlength="20"
            />
          </view>
          <view class="field">
            <text class="field__label">搭档学号</text>
            <input
              class="field__input"
              v-model="form.partner_student_id"
              placeholder="选填"
              placeholder-class="ph"
              :maxlength="30"
            />
          </view>
          <view class="field">
            <text class="field__label">搭档单位</text>
            <input
              class="field__input"
              v-model="form.partner_team"
              placeholder="选填。搭档与你不同学院时填写，如 外国语学院"
              placeholder-class="ph"
              :maxlength="50"
            />
            <text class="field__helper">混双、跨院组队时两人单位通常不同，不填则按你的单位展示</text>
          </view>
        </block>

        <view class="field">
          <text class="field__label">性别</text>
          <view class="segment">
            <view
              v-for="g in genders"
              :key="g.value"
              class="segment__item"
              :class="{ 'segment__item--active': form.gender === g.value }"
              @click="form.gender = g.value"
            >
              {{ g.label }}
            </view>
          </view>
        </view>

        <view class="field">
          <text class="field__label">所属单位</text>
          <input
            class="field__input"
            v-model="form.team"
            placeholder="如 计算机学院"
            placeholder-class="ph"
            :maxlength="30"
          />
          <view v-if="teams.length" class="teams">
            <view
              v-for="tm in teams"
              :key="tm"
              class="teams__chip"
              :class="{ 'teams__chip--active': form.team === tm }"
              @click="form.team = tm"
            >
              {{ tm }}
            </view>
          </view>
          <text class="field__helper">同单位规避算法依赖此字段，建议填写规范全称</text>
        </view>

        <view class="field">
          <text class="field__label">联系电话</text>
          <input
            class="field__input"
            v-model="form.phone"
            type="number"
            placeholder="选填，用于赛前通知"
            placeholder-class="ph"
            :maxlength="15"
          />
        </view>
      </wb-section>

      <wb-section
        class="block"
        title="竞技参数"
        icon="target"
        desc="水平分用于公平性评估，种子位决定抽签分区"
      >
        <view class="field">
          <text class="field__label">水平分（0-100）</text>
          <view class="level">
            <view class="level__btn" @click="bumpLevel(-5)">
              <wb-icon name="minus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
            <input
              class="level__input t-num"
              type="number"
              v-model="form.level"
              placeholder="0"
              placeholder-class="ph"
            />
            <view class="level__btn" @click="bumpLevel(5)">
              <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
          <text class="field__helper">用于衡量各 1/4 区与各小组的实力均衡度，越高实力越强</text>
        </view>

        <view class="switch-row">
          <view class="switch-row__text">
            <text class="switch-row__title">标记为种子选手</text>
            <text class="switch-row__desc">种子将优先分散到不同分区，避免提前相遇</text>
          </view>
          <view class="sw" :class="{ 'sw--on': form.is_seed }" @click="toggleSeed">
            <view class="sw__knob" />
          </view>
        </view>

        <view v-if="form.is_seed" class="field field--gap">
          <text class="field__label">种子序号</text>
          <view class="level">
            <view class="level__btn" @click="bumpSeedRank(-1)">
              <wb-icon name="minus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
            <input
              class="level__input t-num"
              type="number"
              v-model="form.seed_rank"
              placeholder="1"
              placeholder-class="ph"
            />
            <view class="level__btn" @click="bumpSeedRank(1)">
              <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
          <text class="field__helper">1 号为最高种子，抽签时 1、2 号分处上下半区</text>
        </view>
      </wb-section>

      <!-- 已有选手的操作区 -->
      <wb-section v-if="isEdit" class="block" title="档案操作" icon="sliders">
        <view class="ops">
          <view class="op" @click="resetForm">
            <wb-icon name="refresh" :size="15" color="#1e40af" />
            <text class="op__text">撤销修改</text>
          </view>
          <view class="op op--danger" @click="confirmDelete">
            <wb-icon name="trash" :size="15" color="#dc2626" />
            <text class="op__text op__text--danger">删除选手</text>
          </view>
        </view>
      </wb-section>

      <view class="spacer" />

      <view class="footer-bar">
        <view class="btn btn--ghost btn--sm footer-btn" @click="back">
          <text class="btn__text-ghost">返回</text>
        </view>
        <view
          class="btn btn--primary btn--sm footer-btn footer-btn--main"
          :class="{ 'btn--disabled': submitting }"
          @click="submit"
        >
          <text class="btn__text">{{ submitting ? '保存中…' : isEdit ? '保存修改' : '保存选手' }}</text>
        </view>
      </view>
    </block>

    <!-- ==================== 识别中遮罩 ==================== -->
    <!-- 用自定义遮罩而非 uni.showLoading：原生 loading 上放不了按钮，
         本地模型偏慢时用户只能干等；这里给出已等待时长与「跳过」入口 -->
    <view v-if="parsing" class="mask">
      <view class="mask__card">
        <view class="mask__spinner" />
        <text class="mask__title">{{ useAi ? '智能识别中' : '正在解析名单' }}</text>
        <text class="mask__hint">{{ parseHint }}</text>
        <view v-if="useAi" class="mask__skip" @click="skipAi">
          <text class="mask__skip-text">跳过识别，直接用规则解析</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getPlayerById,
  addPlayer,
  updatePlayer,
  deletePlayer,
  batchImportPlayers,
  getTeams,
  getImportAiStatus,
  parsePlayersFromExcel,
  parsePlayersFromText
} from '../../utils/api';
import { requireLogin } from '../../utils/session';
import {
  LIBRARY_CATEGORIES,
  isDoublesCategory,
  doublesHint,
  categoryLabel
} from '../../utils/dict';

export default {
  data() {
    return {
      mode: 'single',
      playerId: '',
      teams: [],
      submitting: false,
      /**
       * 当前项目分支（MS/WS/MD/WD/XD）。
       * 批量导入时留空 = 交给后端按名单内容自动判定，
       * 避免用户「先选分支再导入」选错（例如男双的名单选了男单）。
       */
      category: '',
      categoryOptions: LIBRARY_CATEGORIES,
      genders: [
        { value: 'M', label: '男子' },
        { value: 'F', label: '女子' }
      ],
      form: {
        student_id: '',
        name: '',
        gender: 'M',
        team: '',
        phone: '',
        level: '',
        is_seed: false,
        seed_rank: '',
        partner_name: '',
        partner_student_id: '',
        partner_team: ''
      },
      errors: {},
      original: null,
      // ---------- 批量导入 ----------
      source: 'excel',
      raw: '',
      fileName: '',
      filePath: '',
      useAi: true,
      parsing: false,
      /** 本次识别出的各项目分布：[{ category, label, count, valid, paired }] */
      groups: [],
      /** 名单缺少「参赛类别」列等硬性错误提示 */
      categoryError: '',
      /** 部分行的「参赛类别」识别不了时的提醒 */
      categoryWarning: '',
      /** 识别已等待秒数：让用户看到「在动」，而不是只有一个转圈 */
      parseSeconds: 0,
      parseTimer: null,
      /** 识别轮次：跳过重试后旧请求的结果作废 */
      parseSeq: 0,
      parsed: [],
      ai: {
        enabled: true,
        available: false,
        model: '',
        message: '正在检测本地智能识别服务…'
      },
      autoSeed: true,
      seedN: 4
    };
  },
  computed: {
    isEdit() {
      return !!this.playerId;
    },
    /** 识别中的提示文案：本地模型慢时告知原因并给出选择 */
    parseHint() {
      const waited = '已等待 ' + this.parseSeconds + ' 秒';
      if (!this.useAi) return waited + '，正在解析名单…';
      if (this.parseSeconds < 12) return waited + '，正在让本地模型判断列语义…';
      return waited + '，本地模型首次调用需加载权重，可继续等待，也可跳过';
    },
    /** 当前是否双打分支 */
    doublesMode() {
      return isDoublesCategory(this.category);
    },
    doublesTip() {
      return doublesHint();
    },
    rawLines() {
      return (this.raw || '')
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l.length).length;
    },
    /** 按 autoSeed 规则给可导入选手标记种子 */
    seeded() {
      const list = this.parsed.map((p) => Object.assign({}, p));
      list.forEach((p) => {
        if (!p.is_seed) {
          p.is_seed = false;
          p.seed_rank = 0;
        }
      });
      if (this.autoSeed) {
        const idx = list
          .map((p, i) => ({ i, level: p.level || 0 }))
          .filter((x) => list[x.i].ok)
          .sort((a, b) => b.level - a.level || a.i - b.i)
          .slice(0, Math.max(0, Math.min(this.seedN, list.filter((p) => p.ok).length)))
          .map((x) => x.i);
        idx.forEach((i, rank) => {
          list[i].is_seed = true;
          list[i].seed_rank = rank + 1;
        });
      }
      return list;
    },
    validParsed() {
      return this.seeded.filter((p) => p.ok);
    },
    skipped() {
      return this.seeded.filter((p) => !p.ok);
    },
    parsedPreview() {
      return this.seeded.slice(0, 12).map((p) => ({
        name: p.name,
        // 一份表可能混放多个项目，预览里必须带上每行自己的项目，否则看不出来
        cat: p.category ? categoryLabel(p.category) : '类别未识别',
        catOk: !!p.category,
        seed: p.is_seed,
        ok: p.ok,
        desc:
          (p.student_id || '无学号') +
          ' · ' +
          (p.gender === 'F' ? '女' : '男') +
          ' · ' +
          (p.team || '未填单位') +
          ' · 水平分 ' +
          (p.level || 0) +
          (p.partner_name ? ' · 搭档 ' + p.partner_name : '') +
          (p.is_seed ? ' · 种子 ' + p.seed_rank : '') +
          (p.ok ? '' : ' · ' + p.reason)
      }));
    }
  },
  onLoad(options) {
    if (!requireLogin()) return;
    const o = options || {};
    this.mode = o.mode === 'batch' ? 'batch' : 'single';
    this.playerId = o.id || '';
    // 从选手库列表带过来的分支
    if (o.category) this.category = String(o.category).toUpperCase();
    // 单个新增/编辑必须落在某个分支上，未指定时按男单起步；
    // 批量导入则留空，交由后端按名单内容自动判定
    if (this.mode === 'single' && !this.category) this.category = 'MS';
    if (this.mode === 'batch') {
      uni.setNavigationBarTitle({ title: '导入选手' });
      this.checkAi();
    } else if (this.isEdit) {
      uni.setNavigationBarTitle({ title: '编辑选手' });
    } else {
      uni.setNavigationBarTitle({ title: '新增选手' });
    }
    this.loadTeams();
    if (this.isEdit) this.loadPlayer();
  },
  onUnload() {
    // 离开页面时清理计时器，避免后台空转
    if (this.parseTimer) {
      clearInterval(this.parseTimer);
      this.parseTimer = null;
    }
  },
  methods: {
    /* ---------------- 工具 ---------------- */
    back() {
      const pages = getCurrentPages();
      if (pages.length > 1) uni.navigateBack();
      else uni.reLaunch({ url: '/pages/player/list' });
    },
    /* ---------------- 单个表单 ---------------- */
    async loadTeams() {
      try {
        const ts = await getTeams();
        this.teams = Array.isArray(ts) ? ts : [];
      } catch (e) {
        this.teams = [];
      }
    },
    async loadPlayer() {
      try {
        const p = await getPlayerById(this.playerId);
        if (!p) {
          uni.showToast({ title: '选手不存在', icon: 'none' });
          return;
        }
        this.original = JSON.parse(JSON.stringify(p));
        if (p.category) this.category = String(p.category).toUpperCase();
        this.form = {
          student_id: p.student_id || '',
          // 编辑双打组合时只回填主选手名，搭档单独在 partner_name 里编辑
          name: String(p.name || '').split('/')[0],
          gender: p.gender || 'M',
          team: p.team || '',
          phone: p.phone || '',
          level: p.level === null || p.level === undefined ? '' : String(p.level),
          is_seed: !!p.is_seed,
          seed_rank: p.is_seed ? String(p.seed_rank || 1) : '',
          partner_name: p.partner_name || '',
          partner_student_id: p.partner_student_id || '',
          partner_team: p.partner_team || ''
        };
      } catch (e) {
        /* 已提示 */
      }
    },
    toggleSeed() {
      this.form.is_seed = !this.form.is_seed;
      if (this.form.is_seed && !this.form.seed_rank) this.form.seed_rank = '99';
      if (!this.form.is_seed) this.form.seed_rank = '';
    },
    bumpLevel(delta) {
      const cur = parseInt(this.form.level, 10) || 0;
      this.form.level = String(Math.max(0, Math.min(100, cur + delta)));
    },
    bumpSeedRank(delta) {
      const cur = parseInt(this.form.seed_rank, 10) || 1;
      this.form.seed_rank = String(Math.max(1, Math.min(99, cur + delta)));
    },
    resetForm() {
      if (!this.original) return;
      const p = this.original;
      this.form = {
        student_id: p.student_id || '',
        name: String(p.name || '').split('/')[0],
        gender: p.gender || 'M',
        team: p.team || '',
        phone: p.phone || '',
        level: p.level === null || p.level === undefined ? '' : String(p.level),
        is_seed: !!p.is_seed,
        seed_rank: p.is_seed ? String(p.seed_rank || 1) : '',
        partner_name: p.partner_name || '',
        partner_student_id: p.partner_student_id || '',
        partner_team: p.partner_team || ''
      };
      this.errors = {};
      uni.showToast({ title: '已撤销未保存的修改', icon: 'none' });
    },
    validate() {
      const e = {};
      if (!(this.form.student_id || '').trim()) e.student_id = '学号不能为空';
      if (!(this.form.name || '').trim()) e.name = '姓名不能为空';
      if (this.doublesMode && !(this.form.partner_name || '').trim()) {
        e.partner_name = '双打组合必须填写搭档姓名';
      }
      this.errors = e;
      const keys = Object.keys(e);
      if (keys.length) {
        uni.showToast({ title: e[keys[0]], icon: 'none' });
        return false;
      }
      return true;
    },
    buildPayload() {
      const name = (this.form.name || '').trim();
      const partner = this.doublesMode ? (this.form.partner_name || '').trim() : '';
      return {
        player_id: this.playerId || undefined,
        category: this.category,
        student_id: (this.form.student_id || '').trim(),
        // 双打统一成「主选手/搭档」的组合名，列表与对阵表都能直接展示
        name: partner && name.indexOf('/') < 0 ? name + '/' + partner : name,
        gender: this.form.gender || 'M',
        team: (this.form.team || '').trim(),
        phone: (this.form.phone || '').trim(),
        level: parseInt(this.form.level, 10) || 0,
        is_seed: !!this.form.is_seed,
        seed_rank: this.form.is_seed ? parseInt(this.form.seed_rank, 10) || 99 : 0,
        partner_name: partner,
        partner_student_id: this.doublesMode ? (this.form.partner_student_id || '').trim() : '',
        // 搭档单位独立保存：混双/跨院组队时与本人单位不同
        partner_team: this.doublesMode ? (this.form.partner_team || '').trim() : ''
      };
    },
    submit() {
      if (this.submitting) return;
      if (!this.validate()) return;
      this.submitting = true;
      const payload = this.buildPayload();
      const task = this.isEdit ? updatePlayer(payload) : addPlayer(payload);
      task
        .then(() => {
          uni.showToast({ title: this.isEdit ? '已保存' : '已新增', icon: 'success' });
          setTimeout(() => this.back(), 800);
        })
        .catch(() => {
          /* 已提示 */
        })
        .finally(() => {
          this.submitting = false;
        });
    },
    confirmDelete() {
      const name = this.form.name || '该选手';
      uni.showModal({
        title: '删除选手',
        content: '确认删除「' + name + '」？若该选手仍在未结束赛事的参赛名单中，将无法删除。',
        confirmText: '删除',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await deletePlayer(this.playerId);
            uni.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => this.back(), 800);
          } catch (e) {
            /* 已提示 */
          }
        }
      });
    },

    /* ---------------- 批量导入 ---------------- */
    async checkAi() {
      try {
        const s = await getImportAiStatus();
        if (s) {
          this.ai = s;
          if (s.available === false) this.useAi = false;
        }
      } catch (e) {
        this.ai = {
          enabled: false,
          available: false,
          model: '',
          message: '未检测到本地 Ollama 服务，导入时将使用规则解析'
        };
        this.useAi = false;
      }
    },
    chooseExcel() {
      const that = this;
      const onPicked = (path, name) => {
        that.filePath = path;
        that.fileName = name || '已选择文件';
        that.parse();
      };
      // 微信小程序：从聊天记录中选择文件
      if (typeof uni.chooseMessageFile === 'function') {
        uni.chooseMessageFile({
          count: 1,
          type: 'file',
          extension: ['xlsx', 'xls'],
          success: (res) => {
            const f = (res.tempFiles || [])[0];
            if (!f) return;
            onPicked(f.path, f.name);
          },
          fail: () => {
            /* 用户取消 */
          }
        });
        return;
      }
      // H5 / App：使用文件选择
      uni.chooseFile({
        count: 1,
        extension: ['.xlsx', '.xls'],
        success: (res) => {
          const f = (res.tempFiles || [])[0];
          if (!f) return;
          onPicked(f.path, f.name);
        },
        fail: () => {}
      });
    },
    parse() {
      if (this.parsing) return;
      // 没指定分支时传 AUTO，让后端按名单内容判定
      const cat = this.category || 'AUTO';
      if (this.source === 'excel') {
        if (!this.filePath) {
          uni.showToast({ title: '请先选择 Excel 文件', icon: 'none' });
          return;
        }
        this.runParse(() => parsePlayersFromExcel(this.filePath, this.useAi, cat));
        return;
      }
      if (!(this.raw || '').trim()) {
        uni.showToast({ title: '请先粘贴名单文本', icon: 'none' });
        return;
      }
      this.runParse(() => parsePlayersFromText(this.raw, this.useAi, cat));
    },


    /**
     * 执行识别。
     *
     * 刻意不用 uni.showLoading：原生 loading 上放不了按钮，本地模型一旦偏慢，
     * 用户就只能盯着转圈干等（这正是之前「一直转圈」的体感来源）。
     * 改用自定义遮罩：实时显示已等待秒数，并提供「跳过」入口，
     * 随时可以退回规则解析立刻拿到结果。
     */
    runParse(task) {
      const seq = ++this.parseSeq;
      this.parsing = true;
      this.parseSeconds = 0;
      this.parseTimer = setInterval(() => {
        this.parseSeconds += 1;
      }, 1000);

      task()
        .then((res) => {
          if (seq !== this.parseSeq) return; // 已被「跳过」开启的新一轮识别取代
          this.applyParsed(res);
        })
        .catch(() => {
          /* 错误提示已在请求层统一给出 */
        })
        .then(() => {
          if (seq !== this.parseSeq) return;
          this.finishParse();
        });
    },
    finishParse() {
      if (this.parseTimer) {
        clearInterval(this.parseTimer);
        this.parseTimer = null;
      }
      this.parsing = false;
    },
    /** 不想再等本地模型：立刻改用规则解析（纯本地计算，瞬间出结果） */
    skipAi() {
      if (!this.parsing) return;
      this.finishParse();
      this.useAi = false;
      this.parseSeq += 1; // 让正在等待的那次请求作废
      uni.showToast({ title: '已改用规则解析', icon: 'none' });
      this.parse();
    },
    applyParsed(res) {
      const players = (res && res.players) || [];
      // 参赛类别完全以名单里的「参赛类别」列为准，系统不做推断：
      // 这里只把各项目的分布取回来展示，每一行还带着自己的 category 供落库使用
      this.groups = Array.isArray(res && res.groups) ? res.groups : [];
      this.categoryError = (res && res.category_error) || '';
      this.categoryWarning = (res && res.category_warning) || '';
      this.parsed = players.map((p) => ({
        category: p.category || '',
        student_id: p.student_id || '',
        name: p.name || '',
        gender: p.gender === 'F' ? 'F' : 'M',
        team: p.team || '',
        phone: p.phone || '',
        level: p.level || 0,
        is_seed: !!p.is_seed,
        seed_rank: p.seed_rank || 0,
        partner_name: p.partner_name || '',
        partner_student_id: p.partner_student_id || '',
        partner_team: p.partner_team || '',
        ok: !!p.ok,
        reason: p.reason || ''
      }));
      if (res && res.pair_hint) {
        uni.showToast({ title: res.pair_hint, icon: 'none', duration: 3200 });
      }
      if (res && res.ai_message) {
        this.ai = Object.assign({}, this.ai, {
          available: !!res.ai_used,
          model: res.ai_model || this.ai.model,
          message: res.ai_message
        });
      }
      if (this.categoryError) {
        uni.showToast({ title: '名单缺少「参赛类别」列', icon: 'none', duration: 3000 });
      } else if (!this.parsed.length) {
        uni.showToast({ title: '没有识别到有效数据行', icon: 'none' });
      } else {
        const parts = this.groups.map((g) => g.label + ' ' + g.count);
        uni.showToast({
          title: parts.length ? parts.join(' · ') : '识别到 ' + this.parsed.length + ' 行',
          icon: 'none',
          duration: 2600
        });
      }
    },
    fillDemo() {
      // 示例里刻意混放多个项目，演示「一份表多个分支」与「同一人参加多项」
      this.raw = [
        '参赛类别,学号,姓名,性别,单位,水平分',
        '男单,20230001,张三,男,计算机学院,88',
        '男单,20230002,李四,男,外国语学院,76',
        '女单,20230003,王芳,女,体育学院,80',
        '男双,20230001,张三/王五,男,计算机学院,86',
        '混双,20230001,张三/王芳,男,计算机学院,84',
        '混双,20230004,高科/问陈瑶,男,电子信息学院,82'
      ].join('\n');
    },
    bumpSeed(delta) {
      this.seedN = Math.max(0, Math.min(64, this.seedN + delta));
    },
    doImport() {
      if (this.submitting) return;
      const list = this.validParsed;
      if (!list.length) {
        uni.showToast({ title: '没有可导入的有效数据', icon: 'none' });
        return;
      }
      const skipped = this.skipped.length;
      // 一份表可能含多个项目，导入前把各项目条数说清楚
      const breakdown = {};
      list.forEach((p) => {
        breakdown[p.category] = (breakdown[p.category] || 0) + 1;
      });
      const detail = Object.keys(breakdown)
        .map((c) => categoryLabel(c) + ' ' + breakdown[c])
        .join('、');
      uni.showModal({
        title: '确认导入',
        content:
          '将导入 ' + list.length + ' 条：' + detail +
          (skipped ? '；跳过 ' + skipped + ' 行不符合要求的数据' : '') +
          (this.autoSeed ? '。其中前 ' + Math.min(this.seedN, list.length) + ' 名标记为种子' : '') +
          '。',
        confirmText: '导入',
        success: (r) => {
          if (!r.confirm) return;
          this.submitting = true;
          const payload = list.map((p) => ({
            // 每条用自己的项目分支：同一份表里可能同时有男单、男双、混双
            category: p.category,
            student_id: p.student_id,
            name: p.name,
            gender: p.gender,
            team: p.team,
            phone: p.phone,
            level: p.level,
            is_seed: p.is_seed,
            seed_rank: p.seed_rank,
            partner_name: p.partner_name || '',
            partner_student_id: p.partner_student_id || '',
            partner_team: p.partner_team || ''
          }));
          batchImportPlayers(payload)
            .then((n) => {
              uni.showToast({ title: '成功导入 ' + (n || payload.length) + ' 位', icon: 'success' });
              setTimeout(() => this.back(), 1000);
            })
            .catch(() => {
              /* 已提示 */
            })
            .finally(() => {
              this.submitting = false;
            });
        }
      });
    }
  }
};
</script>

<style scoped>
.ph {
  color: #b3bfd2;
  font-size: 15px;
}

.block {
  margin-bottom: var(--sp-3);
}

/* ---------- 项目分支 ---------- */
.cats {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.cat {
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
}

.cat--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
}

.cat__t {
  font-size: 13px;
  color: var(--c-text-sub);
}

.cat--active .cat__t {
  color: var(--c-primary);
  font-weight: 600;
}

/* ---------- 导入方式切换 ---------- */
.seg {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: var(--r-sm);
  background-color: var(--c-muted);
  margin-bottom: 12px;
}

.seg__item {
  flex: 1;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 13px;
  color: var(--c-text-sub);
}

.seg__item--active {
  background-color: var(--c-surface);
  color: var(--c-primary);
  font-weight: 600;
  box-shadow: var(--sh-1);
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 132px;
  border: 1.5px dashed var(--c-border);
  border-radius: var(--r-sm);
  background-color: #fbfcfe;
}

.dropzone__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  max-width: 80%;
}

.dropzone__desc {
  font-size: 11px;
  color: var(--c-text-weak);
}

/* ---------- 批量导入 ---------- */
.paste {
  min-height: 168px;
  font-size: 13px;
  line-height: 1.7;
}

.paste__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.paste__count {
  font-size: 11px;
  color: var(--c-text-weak);
}

.paste__demo {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 32px;
  padding: 0 11px;
  border-radius: 999px;
  background-color: var(--c-primary-soft);
}

.paste__demo-text {
  font-size: 12px;
  color: var(--c-primary);
  font-weight: 600;
}

/* ---------- 智能识别状态 ---------- */
.ai-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
}

.ai-status--ok {
  background-color: var(--c-success-soft);
}

.ai-status--off {
  background-color: var(--c-muted);
}

.ai-status__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-text-sub);
  line-height: 1.5;
}

.parse-btn {
  margin-top: 12px;
}

/* ---------- 种子开关 ---------- */
.sw {
  width: 48px;
  height: 28px;
  border-radius: 999px;
  background-color: #cbd5e1;
  padding: 3px;
  box-sizing: border-box;
  flex-shrink: 0;
  transition: background-color 0.2s ease-out;
}

.sw--on {
  background-color: var(--c-primary);
}

.sw__knob {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background-color: #fff;
  box-shadow: var(--sh-1);
  transition: transform 0.2s ease-out;
}

.sw--on .sw__knob {
  transform: translateX(20px);
}

.seed-n {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--c-muted);
}

.seed-n__label {
  font-size: 14px;
  color: var(--c-text);
  font-weight: 500;
}

.seed-n__ctrl {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.seed-n__btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  background-color: var(--c-muted);
}

.seed-n__value {
  min-width: 34px;
  text-align: center;
  font-size: 17px;
  color: var(--c-text);
}

/* ---------- 预览 ---------- */
.preview {
  display: flex;
  flex-direction: column;
}

.prow {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 48px;
  border-bottom: 1px solid var(--c-muted);
}

.prow:last-child {
  border-bottom: none;
}

.prow--bad {
  background-color: #fffafa;
}

.prow__no {
  width: 20px;
  font-size: 12px;
  color: var(--c-text-weak);
  text-align: center;
  flex-shrink: 0;
}

.prow__name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.prow__meta {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

.preview__more {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-weak);
  padding-top: 10px;
}

.preview__more--warn {
  color: var(--c-warning);
}

.hintcard {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 12px;
}

.hintcard__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-text-sub);
  line-height: 1.65;
}

/* ---------- 单个表单 ---------- */
.teams {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 9px;
}

.teams__chip {
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: #fbfcfe;
  font-size: 12px;
  color: var(--c-text-sub);
}

.teams__chip--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
  color: var(--c-primary);
  font-weight: 600;
}

.field--gap {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-muted);
}

.level {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.level__btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  background-color: var(--c-muted);
  flex-shrink: 0;
}

.level__input {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  text-align: center;
  font-size: 18px;
  color: var(--c-text);
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  box-sizing: border-box;
}

/* ---------- 操作区 ---------- */
.ops {
  display: flex;
  gap: var(--sp-2);
}

.op {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  border-radius: var(--r-sm);
  background-color: var(--c-primary-soft);
}

.op--danger {
  background-color: var(--c-danger-soft);
}

.op__text {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-primary);
}

.op__text--danger {
  color: var(--c-danger);
}

/* ---------- 底部 ---------- */
.spacer {
  height: 8px;
}

.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.5;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.btn__text-ghost {
  color: var(--c-primary);
  font-size: 14px;
  font-weight: 600;
}

/* ---------- 自动判定的分支结果 ---------- */
.detect {
  margin-top: 12px;
  padding: 11px 12px;
  border-radius: var(--r-sm);
  background-color: var(--c-success-soft, #e6f4ea);
}

.detect__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.detect__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text);
}

.detect__reason {
  font-size: 11px;
  color: var(--c-text-sub);
}

.detect__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.dchip {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: #ffffff;
}

.dchip--active {
  background-color: var(--c-primary);
  border-color: var(--c-primary);
}

.dchip__text {
  font-size: 12px;
  color: var(--c-text-sub);
}

.dchip__text--active {
  color: #ffffff;
  font-weight: 600;
}

.detect__hint {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--c-text-weak);
  line-height: 1.6;
}

/* 缺少类别列等硬性问题用警示色 */
.detect--err {
  background-color: var(--c-warning-soft, #fdf1dd);
}

.detect__title--warn {
  color: var(--c-warning, #b45309);
}

/* 预览行里的项目标签 */
.prow__cat {
  display: inline-block;
  padding: 1px 7px;
  margin-bottom: 3px;
  border-radius: 999px;
  font-size: 10px;
  color: var(--c-primary);
  background-color: var(--c-primary-soft, #e8effc);
}

.prow__cat--bad {
  color: var(--c-danger, #dc2626);
  background-color: var(--c-danger-soft, #fdeaea);
}

/* ---------- 识别中遮罩 ---------- */
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(15, 23, 42, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.mask__card {
  width: 264px;
  padding: 24px 20px 18px;
  background-color: #ffffff;
  border-radius: var(--r-md, 14px);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.mask__spinner {
  width: 34px;
  height: 34px;
  border: 3px solid var(--c-muted, #e5e9f2);
  border-top-color: var(--c-primary, #1e40af);
  border-radius: 50%;
  animation: mask-spin 0.8s linear infinite;
}

@keyframes mask-spin {
  to {
    transform: rotate(360deg);
  }
}

.mask__title {
  margin-top: 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text, #1f2a44);
}

.mask__hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--c-text-weak, #8b98b0);
  text-align: center;
  line-height: 1.6;
}

.mask__skip {
  margin-top: 14px;
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid var(--c-primary, #1e40af);
  background-color: #ffffff;
}

.mask__skip-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-primary, #1e40af);
}
</style>
