<template>
  <el-container class="admin-shell">
    <el-aside :width="isCollapsed ? '72px' : '224px'" class="admin-sidebar">
      <router-link to="/welcome" class="brand" aria-label="管理首页"><img src="../assets/logo.png" alt=""><div v-if="!isCollapsed"><strong>狗眼电影</strong><small>CINEMA CONSOLE</small></div></router-link>
      <div class="nav-caption" v-if="!isCollapsed">工作空间</div>
      <el-menu background-color="#172239" text-color="#aebbd2" active-text-color="#fff" unique-opened :collapse="isCollapsed" :collapse-transition="false" router :default-active="$route.path">
        <el-menu-item index="/welcome"><i class="el-icon-data-board"></i><span slot="title">工作台</span></el-menu-item>
        <el-submenu :index="String(item.id)" v-for="item in menulist" :key="item.id"><template slot="title"><i :class="iconlist[item.id] || 'el-icon-folder-opened'"></i><span>{{ item.name }}</span></template><el-menu-item :index="'/' + sub.path.replace(/^\//, '')" v-for="sub in item.children" :key="sub.id"><span slot="title">{{ sub.name }}</span></el-menu-item></el-submenu>
      </el-menu>
      <div class="sidebar-footer" v-if="!isCollapsed"><span></span>影院运营管理平台</div>
    </el-aside>
    <el-container class="admin-workspace">
      <el-header height="72px" class="workspace-header"><div class="header-location"><el-button type="text" class="collapse-control" :aria-label="isCollapsed ? '展开导航' : '收起导航'" @click="isCollapsed = !isCollapsed"><i :class="isCollapsed ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i></el-button><span class="header-product">管理控制台</span><span class="header-divider">/</span><strong>{{ pageTitle }}</strong></div><div class="header-account"><span class="account-avatar"><img v-if="avatarUrl && !avatarFailed" :src="avatarUrl" :alt="displayName + '的头像'" @error="avatarFailed = true"><span v-else>{{ displayName.slice(0,1).toUpperCase() }}</span></span><div><strong>{{ displayName }}</strong><small>{{ roleName }}</small></div><el-button type="text" class="logout-control" @click="logout">退出登录 <i class="el-icon-switch-button"></i></el-button></div></el-header>
      <el-main class="workspace-main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
<script>
import { pictureArray, checkedRequest } from '@/utils/adminCrud'
export default {
  name: 'Home',
  data() {
    let login = {}
    try { login = JSON.parse(window.sessionStorage.getItem('loginUser')) || {} } catch (error) { /* Login guard handles invalid sessions. */ }
    const user = login.sysUser || login
    return { avatarFailed: false, isCollapsed: window.innerWidth < 1100, user, menulist: user.sysRole && user.sysRole.children || [], iconlist: { 1:'el-icon-office-building',2:'el-icon-video-camera',3:'el-icon-film',4:'el-icon-date',5:'el-icon-s-custom',6:'el-icon-user',7:'el-icon-lock' } }
  },
  computed: {
    avatarUrl() { const path = pictureArray(this.user.userPicture)[0]; return path && path.startsWith('/images/') ? this.global.base + path : '' },
    displayName() { return this.user.userName || '管理员' },
    roleName() { return this.user.sysRole && this.user.sysRole.roleName || '管理账号' },
    pageTitle() { if(this.$route.path === '/welcome') return '工作台'; for(const group of this.menulist) { const item = (group.children || []).find(child => '/' + String(child.path).replace(/^\//,'') === this.$route.path); if(item) return item.name } return '业务管理' }
  },
  created() { this.$root.$on('user-profile-updated', this.updateProfile); window.addEventListener('resize', this.handleResize); this.refreshProfile() },
  beforeDestroy() { this.$root.$off('user-profile-updated', this.updateProfile); window.removeEventListener('resize', this.handleResize) },
  methods: {
    handleResize() { if(window.innerWidth < 1100) this.isCollapsed = true },
    updateProfile(user) { this.user = { ...this.user, ...user }; this.avatarFailed = false },
    async refreshProfile() {
      if (!this.user.userId) return
      try { const { data } = await checkedRequest(axios.get('sysUser/' + this.user.userId)); if(data.data) this.updateProfile(data.data) } catch(error) { /* Keep the signed-in profile if this request fails. */ }
    },
    logout() { window.sessionStorage.clear(); this.$router.push('/login') }
  }
}
</script>
<style scoped>
.admin-shell{height:100%;background:#f3f5f9;color:#23304a}.admin-sidebar{background:#172239;display:flex;flex-direction:column;transition:width .2s;overflow-x:hidden}.brand{height:96px;display:flex;align-items:center;gap:12px;padding:0 22px;box-sizing:border-box;color:white;text-decoration:none;flex-shrink:0}.brand img{width:36px;height:36px;border-radius:10px}.brand strong{font-size:20px;letter-spacing:2px}.brand small{display:block;font-size:9px;letter-spacing:1.6px;color:#8090ac;margin-top:5px}.nav-caption{font-size:11px;color:#7485a3;letter-spacing:2px;margin:6px 26px 16px}.el-menu{border:0;flex:1}.el-menu:not(.el-menu--collapse){width:224px}.el-menu-item,.admin-sidebar ::v-deep .el-submenu__title{height:50px;line-height:50px;font-size:13px}.admin-sidebar ::v-deep .el-menu-item.is-active{background:#4264e7!important;border-radius:8px;margin:0 12px;padding-left:14px!important}.admin-sidebar ::v-deep .el-submenu .el-menu-item.is-active{padding-left:38px!important}.admin-sidebar ::v-deep .el-menu-item i,.admin-sidebar ::v-deep .el-submenu__title i{color:inherit}.sidebar-footer{padding:22px;font-size:11px;color:#8b9ab4;border-top:1px solid #26334b;white-space:nowrap}.sidebar-footer>span{display:inline-block;width:6px;height:6px;background:#6d86f8;border-radius:50%;margin-right:9px}.admin-workspace{min-width:0}.workspace-header{background:#fff;border-bottom:1px solid #e8edf4;display:flex;align-items:center;justify-content:space-between;padding:0 32px;flex-shrink:0}.header-location,.header-account{display:flex;align-items:center;gap:16px;font-size:13px}.collapse-control{font-size:21px;color:#67758c}.header-product,.header-divider{color:#9aa5b5}.header-account strong{font-size:12px;display:block}.header-account small{display:block;color:#9aa5b5;font-size:10px;margin-top:4px}.account-avatar{width:34px;height:34px;background:#eef1ff;color:#4866d9;border-radius:11px;display:grid;place-items:center;font-weight:700}.account-avatar{overflow:hidden}.account-avatar img{width:100%;height:100%;object-fit:cover}.logout-control{margin-left:10px;color:#8792a5;font-size:12px}.workspace-main{padding:28px 32px 40px;overflow:auto}@media(max-width:800px){.workspace-header{padding:0 16px}.workspace-main{padding:20px 16px}.header-product,.header-divider,.header-account small,.header-account>div{display:none}.header-location,.header-account{gap:8px}.logout-control{font-size:11px;margin:0}}
</style>
