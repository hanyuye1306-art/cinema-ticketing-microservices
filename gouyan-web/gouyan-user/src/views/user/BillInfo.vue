<template>
  <div class="bill-list-container" v-loading="loading" element-loading-text="正在加载订单">
    <div class="profile-title">我的订单</div>
    <content-skeleton v-if="loading && billList.length === 0" type="rows" :count="4" />
    <div class="order-box" v-for="item in billList" :key="item.billId">
      <div class="order-header">
        <span class="order-date">{{item.billDate}}</span>
        <span class="order-id">狗眼订单号: {{item.billId}}</span>
        <span class="del-order"  @click="deleteBill(item.billId)">
          <i class="el-icon-delete"></i>
        </span>
      </div>
      <div class="order-body">
        <div class="poster">
          <img :src="item.sysSession.sysMovie.moviePoster">
        </div>
        <div class="order-content">
          <div class="movie-name">《{{item.sysSession.sysMovie.movieNameCn}}》</div>
          <div class="cinema-name">{{item.sysSession.sysCinema.cinemaName}}</div>
          <div class="hall-ticket">
            <span>{{item.sysSession.sysHall.sysHallCategory.hallCategoryName+item.sysSession.sysHall.hallName}}</span>
            <span class="ticket-count">{{item.ticketCount}}张</span>
          </div>
          <div class="seat-list">座位：{{item.seatList.join('、')}}</div>
          <div class="show-time">{{item.sysSession.sessionDate + ' ' + item.sysSession.sysMovieRuntime.beginTime}}</div>
        </div>
        <div class="order-price">
          <strong>￥{{item.totalPrice}}</strong>
          <span>￥{{item.sysSession.sessionPrice}} × {{item.ticketCount}}张</span>
        </div>
        <div class="order-status">
          {{item.billState?'已完成':'未完成'}}
        </div>
        <div class="actions">
          <div>
            <a class="order-detail" @click="toBillDetail(item.billId)">查看详情</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import moment from 'moment'
import ContentSkeleton from '../../components/common/content-skeleton'
export default {
  name: "BillInfo",
  components: { ContentSkeleton },
  data(){
    return{
      queryInfo:{
        userId:''
      },
      billList: [],
      loading: false
    }
  },
  methods:{
    getUser(){
      this.queryInfo.userId = JSON.parse(window.sessionStorage.getItem('loginUser')).userId
    },
    async getBillList(){
      this.loading = true
      const _this = this
      try {
        await axios.get('sysBill',{params:_this.queryInfo}).then(resp=>{
          _this.billList = resp.data.data
        })
        for(let idx in this.billList){
          const bill = this.billList[idx]
          bill.sysSession.sessionDate = moment(bill.sysSession.sessionDate).format('YYYY年MM月DD日')
          bill.sysSession.sysMovie.moviePoster = this.global.base + JSON.parse(bill.sysSession.sysMovie.moviePoster)[0]
          const seatList = JSON.parse(bill.seats || '[]')
          this.$set(bill, 'seatList', seatList)
          this.$set(bill, 'ticketCount', seatList.length)
          this.$set(bill, 'totalPrice', (bill.sysSession.sessionPrice * seatList.length).toFixed(2))
        }
      } finally {
        this.loading = false
      }
    },
    async deleteBill(id){
      const _this = this
      const resp = await this.$confirm('此操作将永久删除订单信息,是否继续?','提示',{
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).catch(err=>err)
      if(resp=="cancel"){
        return _this.$message.info('已取消删除')
      }
      await axios.delete('sysBill/'+id).then(resp=>{
        if (resp.data.code !== 200){
          this.$message.success('删除失败！')
        }
      })
      this.getBillList()
      this.$message.success('删除成功！')
    },
    toBillDetail(id) {
      this.$router.push('/billDetail/' + id)
    }
  },
  created() {
    this.getUser()
    this.getBillList()
  }
}
</script>

<style scoped>
.bill-list-container{
  min-height: 900px;
}

.profile-title {
  padding: 26px 0;
  color: #ec443f;
  font-size: 18px;
  border-bottom: 1px solid #e1e1e1;
  margin-bottom: 30px;
}

.order-box {
  border: 1px solid #e5e5e5;
  margin: 0 40px 30px 0;
}

.order-box .order-header {
  background-color: #f7f7f7;
  font-size: 14px;
  padding: 16px 20px;
}

.order-box .order-header .order-date {
  color: #333;
  display: inline-block;
  margin-right: 30px;
}

.order-box .order-header .order-id {
  color: #999;
}

.del-order{
  float: right;
}

.order-box .order-body {
  padding: 20px;
  padding-right: 0;
  display: flex;
}

.order-box .order-body .poster {
  width: 66px;
  height: 91px;
  margin-right: 11px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, .12), 0 0 6px rgba(0, 0, 0, .04)
}

.poster img{
  width: 100%;
  height: 100%;
}

.order-content{
  width: 49%;
}

.movie-name{
  font-size: 16px;
  font-weight: 700;
  color: #333;
  margin: 4px 0 7px -6px;
}

.cinema-name, .hall-ticket {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.ticket-count {
  margin-left: 12px;
  color: #f03d37;
  font-weight: 600;
}

.seat-list {
  margin-bottom: 4px;
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.show-time {
  font-size: 12px;
  color: #f03d37;
}

.order-price, .order-status, .actions{
  font-size: 14px;
  color: #333;
  width: 12%;
  line-height: 95px;
}

.order-price {
  display: flex;
  flex-direction: column;
  justify-content: center;
  line-height: 1.5;
}

.order-price strong {
  font-size: 16px;
}

.order-price span {
  color: #999;
  font-size: 12px;
}

a {
  text-decoration: none;
  cursor: pointer;
  color: #333;
}
</style>
