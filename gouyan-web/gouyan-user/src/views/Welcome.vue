<template>
  <el-container>
    <el-header>
      <div class="header-inner">
        <a href="/welcome" class="logo">
          <img src="../assets/homeLogo-fixed.png" alt="狗眼电影">
        </a>
        <el-menu
            :default-active="activeUrl"
            class="nav-menu"
            mode="horizontal"
            :router="true"
            active-text-color="#409EFF"
            text-color="#000000">
          <el-menu-item :index="item.path" v-for="item in menuList" :key="item.id">{{item.name}}</el-menu-item>
        </el-menu>
        <div class="searchContainer">
          <el-input v-model="kw" class="searchBar" placeholder="找电影、影人、影院"></el-input>
          <el-button id="searchBtn" icon="el-icon-search" type="primary" circle @click="search"></el-button>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="el-dropdown-link">
            <el-avatar :src="url" :size="50">{{url === '' || url === null ? 'user': ''}}</el-avatar>
            <i class="el-icon-arrow-down el-icon--right icon-arrow"></i>
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="userMenu">个人中心</el-dropdown-item>
            <el-dropdown-item command="logout">退出</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </el-header>
    <el-main>
      <router-view></router-view>
    </el-main>
    <el-footer>
      <div class="footer-mini"></div>
      <div class="footer">
<!--        <img style="width: 357px; height: 50px" src="../assets/register-footer.pngback">-->
      </div>
      <el-backtop></el-backtop>
    </el-footer>
  </el-container>
</template>

<script>
export default {
  name: "Welcome",
  data() {
    return {
      url: '',
      activeUrl: this.$route.path.substring(0, this.$route.path.indexOf('/',1) === -1 ? this.$route.path.length : this.$route.path.indexOf('/',1)),
      menuList: [
        {
          id: 1,
          name: '首页',
          path: '/home'
        },
        {
          id: 2,
          name: '电影',
          path: '/movie'
        },
        {
          id: 3,
          name: '影院',
          path: '/cinema'
        },
        {
          id: 4,
          name: '榜单',
          path: '/rankingList'
        }
      ],
      //搜索关键字
      kw:''
    }
  },
  created() {
    this.updateProfileAvatar(JSON.parse(window.sessionStorage.getItem('loginUser')))
    this.$root.$on('user-profile-updated', this.updateProfileAvatar)
  },
  beforeDestroy() {
    this.$root.$off('user-profile-updated', this.updateProfileAvatar)
  },
  watch: {
    '$route'() {
      this.activeUrl = this.$route.path.substring(0, this.$route.path.indexOf('/',1) === -1 ? this.$route.path.length : this.$route.path.indexOf('/',1))
    }
  },
  methods: {
    updateProfileAvatar(user) {
      let pictures = []
      try { pictures = JSON.parse(user && user.userPicture || '[]') } catch (error) { /* Empty avatar fallback. */ }
      this.url = Array.isArray(pictures) && pictures[0] ? this.global.base + pictures[0] : ''
    },
    handleSelect(key, keyPath){
      console.log(key, keyPath);
    },
    search(){
      let tmp = this.kw
      this.$router.push('/search/searchMovie?kw=' + tmp)
      this.kw = ''
    },
    handleCommand(command){
      if(command === 'logout'){
        window.sessionStorage.clear();
        return this.$router.push('/login')
      }
      this.$router.push('/' + command)
    }

  }
}
</script>

<style scoped>
.el-header{
  height: 80px !important;
  border-bottom: 1px solid #e6e6e6;
}

.header-inner{
  width: min(1200px, calc(100% - 48px));
  height: 80px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo{
  width: clamp(180px, 14vw, 220px);
  height: 72px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  overflow: hidden;
}

.logo img{
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
  object-position: center;
}

.nav-menu{
  border-bottom: 0px solid #eee !important;
  height: 60px;
  margin: 9px auto 9px 0;
  flex: 0 0 auto;
}

.el-menu-item{
  font-size: 20px;
}

.el-menu-item:hover{
  color: #409EFF !important;
}

.searchContainer{
  width: clamp(300px, 24vw, 390px);
  height: 48px;
  display: flex;
  flex: 0 1 390px;
  position: relative;
  align-items: center;
}

.searchBar{
  width: 100%;
}

.searchBar >>> input{
  height: 48px;
  padding: 0 64px 0 22px;
  border-radius: 50px;
  font-size: 16px;
}

#searchBtn{
  width: 48px;
  height: 48px;
  position: absolute;
  right: 0;
  top: 0;
  transform: none;
  z-index: 2;
}

.el-dropdown-link{
  width: 70px;
  margin: 15px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

@media (max-width: 1100px) {
  .header-inner{
    width: calc(100% - 24px);
    gap: 12px;
  }

  .logo{
    width: 170px;
  }

  .searchContainer{
    min-width: 250px;
  }

  .el-menu-item{
    padding: 0 14px;
  }
}

@media (max-width: 850px) {
  .logo{
    width: 140px;
  }

  .searchContainer{
    min-width: 190px;
  }

  .searchBar >>> input{
    padding-left: 16px;
    font-size: 14px;
  }
}

.icon-arrow {
  transition: all 0.2s ease-in-out;
  -webkit-transition: all 0.2s ease-in-out;
  -moz-transition: all 0.2s ease-in-out;
  -o-transition: all 0.2s ease-in-out;
}

.el-dropdown-link:hover .icon-arrow{
  /* 定义动画的状态 */
  -webkit-transform: rotate(180deg) scale(1);
  -moz-transform: rotate(180deg) scale(1);
  -o-transform: rotate(180deg) scale(1);
  -transform: rotate(180deg) scale(1);
}

.footer-mini{
  border-top: 1px solid #EEE;
  padding-top: 20px;
  text-align: center;
}

.footer{
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.el-main{
  padding: 0px;
}
</style>
