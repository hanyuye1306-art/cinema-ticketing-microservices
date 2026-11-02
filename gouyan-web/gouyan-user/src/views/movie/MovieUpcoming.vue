<template>
  <div v-loading="loading" element-loading-text="正在加载电影">
    <content-skeleton v-if="loading && movieList.length === 0" type="cards" :count="10" />
    <div class="movie-list" v-else>
      <movie-item :movieItem="item" v-for="item in movieList" :key="item.movieId"></movie-item>
    </div>

    <div class="no-cinema" v-if="sorry">
      <p>抱歉，没有找到相关结果，请尝试用其他条件筛选。</p>
    </div>

    <div class="pageHelper">
      <el-pagination
          @current-change="handleCurrentChange"
          :current-page="pageNum"
          :page-size="pageSize"
          background
          layout="prev, pager, next"
          :total="total">
      </el-pagination>
    </div>
  </div>
</template>

<script>
import movieItem from '../../components/movie/movie-item'
import moment from 'moment'
import ContentSkeleton from '../../components/common/content-skeleton'
export default {
  name: "MovieUpcoming",
  components:{
    movieItem,
    ContentSkeleton
  },
  props:{
    movieCategoryId: Number,
    movieAreaId: Number,
    movieAgeId: Number,
    orderByColumn: String
  },
  data(){
    return{
      total: 0,
      pageSize: 30,
      pageNum: 1,
      startDate: moment().format("YYYY-MM-DD"),
      movieList: [],
      sorry: false,
      loading: false
    }
  },
  computed: {
    listenChange () {
      const {movieCategoryId, movieAreaId, movieAgeId, orderByColumn} = this
      return {movieCategoryId, movieAreaId, movieAgeId, orderByColumn}
    }
  },
  created() {
    this.getMovieList()
  },
  watch:{
    listenChange(){
      this.getMovieList()
    }
  },
  methods: {
    async getMovieList(){
      this.loading = true
      let queryInfo = {
        movieCategoryId: this.movieCategoryId,
        movieAgeId: this.movieAgeId,
        movieAreaId: this.movieAreaId,
        orderByColumn: this.orderByColumn,
        pageSize: this.pageSize,
        pageNum: this.pageNum,
        startDate: this.startDate,
        isAsc: 'desc'
      }
      try {
        const { data : res } = await axios.get('sysMovie', {params: queryInfo})
        this.movieList = res.data
        this.total = res.total
        this.sorry = this.movieList.length === 0
      } finally {
        this.loading = false
      }
    },
    handleCurrentChange(newPage) {
      this.pageNum = newPage
      this.getMovieList()
    }
  }
}
</script>

<style scoped>
.movie-list{
  display: flex;
  flex-wrap: wrap;
}

.pageHelper{
  display: flex;
  justify-content: center;
  margin: 40px 0px;
}

.no-cinema{
  display: flex;
  justify-content: center;
  margin-top: 40px;
  margin-bottom: 40px;
  font-size: 16px;
  color: #333;
}
</style>
