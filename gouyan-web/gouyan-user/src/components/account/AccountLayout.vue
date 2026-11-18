<template>
  <main class="account-page">
    <header class="account-header">
      <router-link to="/login" class="account-brand"><img src="../../assets/logo.jpg" alt="狗眼电影"><span>狗眼电影<small>让每一次相遇，都有好电影</small></span></router-link>
      <router-link v-if="$route.path !== '/login'" to="/login" class="back-link">返回登录 <i class="el-icon-right"></i></router-link>
    </header>
    <section class="account-main">
      <div class="account-story"><span class="story-label">GOUYAN CINEMA</span><h1>好电影，<br>等你入场。</h1><p>发现精彩故事，收藏期待与热爱。<br>从这里，开启你的光影时刻。</p><span class="story-line"></span></div>
      <section class="account-card" :aria-label="title"><div class="card-heading"><span class="card-kicker">狗眼电影 · 账号中心</span><h2>{{ title }}</h2><p>{{ subtitle }}</p></div><slot></slot></section>
    </section>
    <footer class="account-footer">狗眼电影 · 与好电影不期而遇</footer>
  </main>
</template>
<script>
export default { name: 'AccountLayout', props: { title: { type: String, required: true }, subtitle: { type: String, default: '' } } }
</script>
<style scoped>
.account-page { min-height: 100vh; display: flex; flex-direction: column; background: #f5f6f8; color: #242a35; }
.account-header { padding: 20px max(24px, calc((100vw - 1120px) / 2)); background: #fff; display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.account-brand { display: flex; gap: 12px; align-items: center; color: #242a35; text-decoration: none; font-size: 22px; font-weight: 700; white-space: nowrap; }
.account-brand img { width: 46px; height: 46px; object-fit: cover; border-radius: 12px; }
.account-brand small { display: block; margin-top: 4px; color: #89909c; font-size: 11px; font-weight: 400; letter-spacing: 1px; }
.back-link { color: #727986; text-decoration: none; font-size: 14px; }.back-link:hover { color: #e44748; }
.account-main { flex: 1; display: flex; gap: 80px; align-items: center; justify-content: center; padding: 44px 32px; background: linear-gradient(90deg, rgba(17,24,38,.92), rgba(27,34,48,.65)), url('../../assets/login-background.png') center / cover; }
.account-story { max-width: 400px; flex: 1; color: #fff; }.story-label { font-size: 12px; color: #f6b6ac; letter-spacing: 4px; }
.account-story h1 { margin: 22px 0; font-size: 54px; line-height: 1.4; letter-spacing: 4px; }.account-story p { color: #d1d5de; font-size: 16px; line-height: 2; }
.story-line { display: block; width: 48px; height: 4px; margin-top: 32px; background: #ef6460; border-radius: 4px; }
.account-card { width: 420px; box-sizing: border-box; padding: 32px; border-radius: 18px; background: #fff; box-shadow: 0 20px 60px rgba(0,0,0,.16); }
.card-kicker { font-size: 11px; letter-spacing: 2px; color: #a0a6b0; }.card-heading h2 { margin: 12px 0 8px; font-size: 26px; font-weight: 600; }.card-heading p { margin: 0 0 24px; color: #858c98; font-size: 13px; line-height: 1.7; }
.account-footer { padding: 18px; text-align: center; color: #9a9fab; font-size: 12px; }
@media (max-width: 850px) { .account-main { gap: 32px; }.account-story h1 { font-size: 38px; } }
@media (max-width: 640px) { .account-header { padding: 16px 20px; }.account-brand { font-size: 19px; }.account-brand small { font-size: 10px; letter-spacing: 0; }.account-story { display: none; }.account-main { padding: 28px 16px; align-items: flex-start; }.account-card { width: 100%; max-width: 440px; padding: 26px 22px; } }
</style>
