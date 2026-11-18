<template>
  <!--
    用 view + background-image 而非 image 标签。
    原因：image 在拿不到有效宽高时会以「容器宽度」为基准按原始比例撑高，
    一旦 style 未生效就会退化成巨幅图标顶爆整个布局（真机/工具实测踩过）。
    view 是块级元素，宽高由 class 兜底 + style 强约束，配合 background-size
    自适应，任何情况下都不会溢出。
  -->
  <view class="wb-icon" :style="boxStyle" />
</template>

<script>
/**
 * wb-icon — 内联 SVG 图标
 *
 * 设计规范要求：不用 emoji 充当结构性图标（跨平台字形不一致、无法用设计令牌控制）。
 * 这里用统一图标族（Lucide 风格：24 视图框 / 1.8 描边 / 圆角端点），
 * 通过 data-uri 渲染为背景图，颜色由 color 属性注入，随主题令牌变化。
 */

// 每个图标为若干段 path 的 d 值集合（描边绘制）
const PATHS = {
  home: ['M3 10.5 12 3l9 7.5', 'M5.5 9.5V21h13V9.5'],
  trophy: [
    'M8 21h8M12 17v4',
    'M7 4h10v4.5a5 5 0 0 1-10 0V4z',
    'M7 5.5H4V7a3 3 0 0 0 3 3',
    'M17 5.5h3V7a3 3 0 0 1-3 3'
  ],
  users: [
    'M16 20v-1.2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20',
    'M9.5 11.2a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2',
    'M21 20v-1.2a4 4 0 0 0-3-3.87',
    'M16.2 4.1a3.6 3.6 0 0 1 0 6.9'
  ],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8'],
  plus: ['M12 5v14M5 12h14'],
  minus: ['M5 12h14'],
  chevronRight: ['M9.5 6l6 6-6 6'],
  chevronLeft: ['M14.5 6l-6 6 6 6'],
  chevronDown: ['M6 9.5l6 6 6-6'],
  arrowLeft: ['M20 12H4M11 19l-7-7 7-7'],
  calendar: ['M8 3v4M16 3v4', 'M4 7.5h16V20H4z', 'M4 11.5h16'],
  clock: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18', 'M12 7.5V12l3 1.8'],
  mapPin: ['M12 21.5s7-5.6 7-11.2a7 7 0 1 0-14 0c0 5.6 7 11.2 7 11.2', 'M12 12.6a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8'],
  chart: ['M6 20v-6M12 20V5M18 20v-9', 'M3 20h18'],
  shield: ['M12 3l8 3v6c0 5.2-3.6 8.4-8 9.5C7.6 20.4 4 17.2 4 12V6l8-3z'],
  check: ['M20 6.5L9.5 17 4 11.5'],
  close: ['M18 6L6 18M6 6l12 12'],
  edit: ['M4 20h4L18.5 9.5l-4-4L4 16v4z', 'M14 6l4 4'],
  trash: ['M4 7h16M9.5 7V5h5v2', 'M6.5 7l1 13h9l1-13'],
  refresh: ['M20.5 12a8.5 8.5 0 1 1-2.5-6', 'M20.5 4v5h-5'],
  search: ['M11 18.5a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15', 'M16.5 16.5l4.5 4.5'],
  filter: ['M3.5 5h17l-6.8 8v6.5L10.3 17v-4z'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18', 'M12 11v5.5', 'M12 7.8v.02'],
  alert: ['M12 3.5l9 16.5H3z', 'M12 10v4.2', 'M12 17.2v.02'],
  upload: ['M12 17.5V5M6 11l6-6 6 6', 'M4 21h16'],
  download: ['M12 5v12.5M6 11.5l6 6 6-6', 'M4 21h16'],
  list: ['M8 6.5h13M8 12h13M8 17.5h13', 'M4 6.5v.02M4 12v.02M4 17.5v.02'],
  medal: ['M12 21a6 6 0 1 0 0-12 6 6 0 0 0 0 12', 'M8 3.5l2.2 6M16 3.5l-2.2 6'],
  star: ['M12 3.5l2.9 6 6.6.9-4.8 4.5 1.2 6.6L12 18.4l-5.9 3.1 1.2-6.6L2.5 10.4l6.6-.9z'],
  lock: ['M5.5 11h13v10h-13z', 'M8.5 11V8a3.5 3.5 0 0 1 7 0v3'],
  // 开锁：锁体相同，锁梁向右上方敞开（用于「密码已显示」状态）
  unlock: ['M5.5 11h13v10h-13z', 'M8.5 11V8a3.5 3.5 0 0 1 6.9-1.4'],
  logout: ['M15 12H3.5M9.5 8l-4 4 4 4', 'M14.5 4h5.5v16h-5.5'],
  sliders: ['M4 8h9M17.5 8H20M4 16h4M12.5 16H20',
    'M15.5 8a2 2 0 1 0-4 0 2 2 0 0 0 4 0', 'M10.5 16a2 2 0 1 0-4 0 2 2 0 0 0 4 0'],
  layers: ['M12 3.5l9 4.8-9 4.8-9-4.8 9-4.8z', 'M3.5 13.5l8.5 4.5 8.5-4.5'],
  target: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18', 'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9', 'M12 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2'],
  shuffle: ['M16 3.5h5V8.5M21 3.5l-7 7M8 20.5H3v-5M3 20.5l7-7',
    'M16 20.5h5v-5M21 20.5l-7-7M8 3.5H3v5M3 3.5l7 7'],
  score: ['M12 20.5h9', 'M16.6 3.6a2.05 2.05 0 0 1 2.9 2.9L7.5 18.5l-4 1 1-4L16.6 3.6z'],
  play: ['M7 4.5l13 7.5-13 7.5z'],
  fileText: ['M14.5 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7.5L14.5 3z',
    'M14.5 3v4.5H19', 'M9 13h6M9 17h4'],
  grid: ['M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z'],
  flag: ['M6 21V4M6 4.5h11l-2 3.5 2 3.5H6'],
  wifi: ['M2.5 8.5a15 15 0 0 1 19 0', 'M6 12a10 10 0 0 1 12 0',
    'M9.5 15.4a5 5 0 0 1 5 0', 'M12 19v.02'],
  inbox: ['M4 13.5V20h16v-6.5', 'M4 13.5l2.5-9h11l2.5 9', 'M4 13.5h5l1 2.5h4l1-2.5h5'],
  sparkle: ['M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z']
};

export default {
  name: 'WbIcon',
  props: {
    name: { type: String, required: true },
    size: { type: [Number, String], default: 20 },
    color: { type: String, default: '#5b6b88' },
    strokeWidth: { type: [Number, String], default: 1.8 }
  },
  computed: {
    paths() {
      const p = PATHS[this.name];
      if (!p) {
        // 静默回退会让「图标名写错」伪装成「图标长得不对」，联调期很难查。
        // 这里显式告警，控制台直接给出错误的名字。
        console.warn('[wb-icon] 未知图标名：' + this.name + '，已回退为 info');
        return PATHS.info;
      }
      return p;
    },
    /** 尺寸必须落成具体像素值：缺失或非法时回退默认值，避免失去约束 */
    px() {
      const n = typeof this.size === 'number' ? this.size : parseFloat(this.size);
      return isNaN(n) || n <= 0 ? 20 : n;
    },
    /**
     * 用字符串拼接而非对象：
     * 对象形式的内联 style 在部分渲染路径下可能整个丢失（实测会导致图标失控），
     * 字符串形式更稳定，且把 min/max 一并锁死，杜绝被父级 flex 拉伸。
     */
    boxStyle() {
      const s = this.px + 'px';
      return (
        'width:' + s + ';' +
        'height:' + s + ';' +
        'min-width:' + s + ';' +
        'max-width:' + s + ';' +
        'min-height:' + s + ';' +
        'max-height:' + s + ';' +
        'background-image:' + this.dataUri + ';'
      );
    },
    dataUri() {
      const body = this.paths
        .map(
          (d) =>
            '<path d="' + d + '" fill="none" stroke="' + this.color +
            '" stroke-width="' + this.strokeWidth +
            '" stroke-linecap="round" stroke-linejoin="round"/>'
        )
        .join('');
      const svg =
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">' + body + '</svg>';
      // 用 encodeURIComponent 保证 # 、< 、> 等字符在 data-uri 中安全
      return 'url("data:image/svg+xml,' + encodeURIComponent(svg) + '")';
    }
  }
};
</script>

<style scoped>
.wb-icon {
  display: inline-block;
  flex-shrink: 0;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 100% 100%;
  vertical-align: middle;
  /* 兜底尺寸：即使 style 注入失败也不会以容器宽度撑开 */
  width: 20px;
  height: 20px;
  overflow: hidden;
}
</style>
