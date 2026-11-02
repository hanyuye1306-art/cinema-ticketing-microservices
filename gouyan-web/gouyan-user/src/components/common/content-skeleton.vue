<template>
  <div class="skeleton" :class="'skeleton-' + type" aria-label="内容加载中">
    <template v-if="type === 'cards'">
      <div class="skeleton-card" v-for="item in count" :key="item">
        <div class="skeleton-block skeleton-poster"></div>
        <div class="skeleton-block skeleton-line"></div>
        <div class="skeleton-block skeleton-line short"></div>
      </div>
    </template>
    <template v-else-if="type === 'rows'">
      <div class="skeleton-row" v-for="item in count" :key="item">
        <div class="skeleton-row-content">
          <div class="skeleton-block skeleton-line wide"></div>
          <div class="skeleton-block skeleton-line medium"></div>
          <div class="skeleton-block skeleton-line short"></div>
        </div>
        <div class="skeleton-block skeleton-button"></div>
      </div>
    </template>
    <template v-else>
      <div class="skeleton-detail-left">
        <div class="skeleton-block skeleton-avatar"></div>
      </div>
      <div class="skeleton-detail-right">
        <div class="skeleton-block skeleton-title"></div>
        <div class="skeleton-block skeleton-line wide"></div>
        <div class="skeleton-block skeleton-line medium"></div>
        <div class="skeleton-block skeleton-line wide"></div>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'ContentSkeleton',
  props: {
    type: { type: String, default: 'cards' },
    count: { type: Number, default: 8 }
  }
}
</script>

<style scoped>
.skeleton { width: 100%; box-sizing: border-box; }
.skeleton-cards { display: flex; flex-wrap: wrap; min-height: 520px; }
.skeleton-card { width: 160px; margin: 0 30px 32px 0; }
.skeleton-block { position: relative; overflow: hidden; background: #f0f2f5; border-radius: 4px; }
.skeleton-block::after { content: ''; position: absolute; inset: 0; transform: translateX(-100%); background: linear-gradient(90deg, transparent, rgba(255,255,255,.72), transparent); animation: skeleton-wave 1.35s infinite; }
.skeleton-poster { width: 160px; height: 220px; }
.skeleton-line { height: 14px; margin-top: 13px; width: 100%; }
.skeleton-line.short { width: 45%; }
.skeleton-line.medium { width: 65%; }
.skeleton-line.wide { width: 85%; }
.skeleton-rows { min-height: 500px; }
.skeleton-row { width: 100%; min-height: 105px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eee; }
.skeleton-row-content { width: 70%; }
.skeleton-button { width: 92px; height: 34px; border-radius: 18px; }
.skeleton-detail { min-height: 620px; display: flex; gap: 38px; padding: 45px 8%; }
.skeleton-avatar { width: 250px; height: 350px; }
.skeleton-detail-right { flex: 1; padding-top: 20px; }
.skeleton-title { width: 38%; height: 34px; margin-bottom: 38px; }
@keyframes skeleton-wave { to { transform: translateX(100%); } }
</style>
