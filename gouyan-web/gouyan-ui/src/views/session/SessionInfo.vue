<template>
  <div>
    <!--面包屑导航区域-->
    <div class="board">
      <el-breadcrumb separator-class="el-icon-arrow-right">
        <el-breadcrumb-item :to="{ path: '/welcome' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>场次管理</el-breadcrumb-item>
        <el-breadcrumb-item>场次信息管理</el-breadcrumb-item>
      </el-breadcrumb>
    </div>


    <!--卡片视图-->
    <el-card class="box-card">
      <el-row :gutter="20">
        <el-col :span="4">
          <el-select v-model="selectedCinemaId" placeholder="请选择影院名称" clearable v-has>
            <el-option
                v-for="item in cinemaList"
                :key="item.cinemaId"
                :label="item.cinemaName"
                :value="item.cinemaId">
            </el-option>
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="selectedMovieId" placeholder="请选择电影名称" clearable>
            <el-option
                v-for="item in movieList"
                :key="item.movieId"
                :label="item.movieNameCn"
                :value="item.movieId">
            </el-option>
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="selectedMovieRuntimeId" placeholder="请选择播放时段" clearable>
            <el-option
                v-for="item in movieRuntimeList"
                :key="item.movieRuntimeId"
                :label="item.movieRuntimeName"
                :value="item.movieRuntimeId">
            </el-option>
          </el-select>
        </el-col>

        <el-col :span="5">
          <el-date-picker
              v-model="selectedDate"
              value-format="yyyy-MM-dd"
              type="date"
              placeholder="选择日期">
          </el-date-picker>
        </el-col>

        <el-col :span="2">
          <el-button icon="el-icon-search" @click="getSessionList">搜索</el-button>
        </el-col>
        <el-col :span="2">
          <el-button type="primary" @click="showAddDialog">添加场次</el-button>
        </el-col>
        <el-col :span="2">
          <el-button type="danger" @click="multipleDelete">批量删除</el-button>
        </el-col>
      </el-row>

      <!--影厅分类列表-->
      <el-table :data="sessionList" style="width: 100%" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="sysCinema.cinemaName" label="影院名称" width="140px"></el-table-column>
        <el-table-column prop="sysHall.hallName" label="影厅名称"></el-table-column>
        <el-table-column prop="languageVersion" label="语言"></el-table-column>
        <el-table-column prop="sysMovie.movieNameCn" label="电影名称"></el-table-column>
        <el-table-column prop="sysMovieRuntime.movieRuntimeName" label="播放时段"></el-table-column>
        <el-table-column prop="sessionDate" label="场次时间"></el-table-column>
        <el-table-column prop="sessionPrice" label="场次价格"></el-table-column>
        <el-table-column prop="sessionTips" label="提示信息"></el-table-column>
        <el-table-column prop="sessionState" label="场次状态">
          <template slot-scope="scope">
            <el-switch v-model="scope.row.sessionState" @change="sessionStateChanged(scope.row)"></el-switch>
          </template>
        </el-table-column>
        <!--是否启用-->

        <el-table-column label="操作" width="180" align="center">
          <template slot-scope="scope">
            <el-tooltip effect="dark" content="修改场次信息" placement="top" :enterable="false" :open-delay="500">
              <el-button v-if="isCinemaOwner(scope.row.cinemaId)" type="primary" icon="el-icon-edit" size="mini" @click="showEditDialog(scope.row.sessionId)"></el-button>
            </el-tooltip>
            <el-tooltip effect="dark" content="删除影厅" placement="top" :enterable="false" :open-delay="500">
              <el-button type="danger" icon="el-icon-delete" size="mini" @click="deleteSessionById(scope.row.sessionId)"></el-button>
            </el-tooltip>
            <el-tooltip effect="dark" content="查看座位" placement="top" :enterable="false" :open-delay="500">
              <el-button type="warning" icon="el-icon-setting" size="mini" @click="checkSeat(scope.row.sessionId)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!--分页区域-->
      <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="queryInfo.pageNum"
          :page-sizes="[5, 7, 9]"
          :page-size="queryInfo.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total">
      </el-pagination>

    </el-card>

    <!--添加场次对话框-->
    <el-dialog title="添加场次" :visible.sync="addDialogVisible" width="60%" @close="addDialogClosed">
      <!--内容主题区-->
      <el-form :model="addForm" :rules="addFormRules" ref="addFormRef" label-width="150px">
        <!--prop：在addFormRules中定义校验规则， v-model：双向绑定数据-->
        <el-form-item label="影院名称" prop="cinemaId">
          <el-select v-if="isSystemAdmin" v-model="addForm.cinemaId" placeholder="请选择影院" @change="addForm.hallId = ''; getHallList(addForm.cinemaId)"><el-option v-for="cinema in cinemaList" :key="cinema.cinemaId" :label="cinema.cinemaName" :value="cinema.cinemaId" /></el-select><el-input v-else v-model="curCinemaName" disabled></el-input>
        </el-form-item>
        <el-form-item label="影厅名称" prop="hallId">
          <el-select v-model="addForm.hallId" placeholder="请选择影厅名称" clearable >
            <el-option
                v-for="item in hallList"
                :key="item.hallId"
                :label="item.hallName"
                :value="item.hallId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="语言版本" prop="languageVersion">
          <el-input v-model="addForm.languageVersion"></el-input>
        </el-form-item>
        <el-form-item label="电影名称" prop="movieId">
          <el-select v-model="addForm.movieId" placeholder="请选择电影名称" clearable >
            <el-option
                v-for="item in movieList"
                :key="item.movieId"
                :label="item.movieNameCn"
                :value="item.movieId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="播放时段" prop="movieRuntimeId">
          <el-select v-model="addForm.movieRuntimeId" placeholder="请选择播放时段" clearable >
            <el-option
                v-for="item in movieRuntimeList"
                :key="item.movieRuntimeId"
                :label="item.movieRuntimeName"
                :value="item.movieRuntimeId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="场次日期" prop="sessionDate">
          <el-date-picker
              v-model="addForm.sessionDate"
              value-format="yyyy-MM-dd"
              type="date"
              placeholder="选择日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="场次价格" prop="sessionPrice">
          <el-input v-model="addForm.sessionPrice"></el-input>
        </el-form-item>
        <el-form-item label="提示信息" prop="sessionTips">
          <el-input v-model="addForm.sessionTips"></el-input>
        </el-form-item>
      </el-form>
      <!--底部区域-->
      <span slot="footer" class="dialog-footer">
      <el-button @click="addDialogVisible = false">取 消</el-button>
      <el-button type="primary" @click="addSession">确 定</el-button>
      </span>
    </el-dialog>

    <!--修改场次对话框-->
    <el-dialog title="修改场次" :visible.sync="editDialogVisible" width="60%" @close="editDialogClosed">
      <el-form :model="editForm" :rules="editFormRules" ref="editFormRef" label-width="150px">
        <el-form-item label="影院名称" prop="cinemaId">
          <el-input v-model="curCinemaName" disabled></el-input>
        </el-form-item>
        <el-form-item label="影厅名称" prop="hallId">
          <el-select v-model="editForm.hallId" placeholder="请选择影厅名称" clearable >
            <el-option
                v-for="item in hallList"
                :key="item.hallId"
                :label="item.hallName"
                :value="item.hallId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="语言版本" prop="languageVersion">
          <el-input v-model="editForm.languageVersion"></el-input>
        </el-form-item>
        <el-form-item label="电影名称" prop="movieId">
          <el-select v-model="editForm.movieId" placeholder="请选择电影名称" clearable >
            <el-option
                v-for="item in movieList"
                :key="item.movieId"
                :label="item.movieNameCn"
                :value="item.movieId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="播放时段" prop="movieRuntimeId">
          <el-select v-model="editForm.movieRuntimeId" placeholder="请选择播放时段" clearable >
            <el-option
                v-for="item in movieRuntimeList"
                :key="item.movieRuntimeId"
                :label="item.movieRuntimeName"
                :value="item.movieRuntimeId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="场次日期" prop="sessionDate">
          <el-date-picker
              v-model="editForm.sessionDate"
              value-format="yyyy-MM-dd"
              type="date"
              placeholder="选择日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="场次价格" prop="sessionPrice">
          <el-input v-model="editForm.sessionPrice"></el-input>
        </el-form-item>
        <el-form-item label="提示信息" prop="sessionTips">
          <el-input v-model="editForm.sessionTips"></el-input>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="editDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="editSessionInfo">确 定</el-button>
      </span>
    </el-dialog>

    <!--安排座位对话框-->
    <el-dialog title="查看座位" :visible.sync="checkDialogVisible" :width="checkDialogWidth">
      <div style="display:flex; align-content: center">
        <span style="margin-right: 10px">启用</span>
        <span class="seat" :class="isSelected[0]"></span>
        <span style="margin: 0 10px">禁用</span>
        <span class="seat" :class="isSelected[1]"></span>
        <span style="margin: 0 10px">售出</span>
        <span class="seat" :class="isSelected[2]"></span>
      </div>
      <br/>
      <div class="row" v-for="(value, key) in seats">
        <span style="margin-right: 100px">{{key}}</span>
        <div style="display: flex; justify-content: flex-end">
          <span class="seat" :class="isSelected[seats[key][index]]" v-for="(item, index) in value"></span>
        </div>
      </div>
    </el-dialog>


  </div>
</template>

<script>
import { checkedRequest, validateForm, runMutation, pictureArray, uploadPictures, runRead } from "@/utils/adminCrud";
import global from "@/assets/css/global.css";
export default {
  computed: {
    isSystemAdmin() {
      return window.sessionStorage.getItem("btnPermission") === "admin";
    }

  },
  name: "HallInfo",

  data() {
    return {
      queryInfo: {
        sessionId: '',
        cinemaId: '',
        movieId: '',
        movieRuntimeId: '',
        sessionDate: '',
        pageNum: 1,
        pageSize: 7
      },
      sessionList: [],
      total: 0,
      //控制对话框的显示与隐藏
      addDialogVisible: false,
      //添加影厅的表单数据
      addForm: {
        cinemaId: '',
        hallId: '',
        languageVersion: '',
        movieId: '',
        movieRuntimeId: '',
        sessionDate: '',
        sessionState: true,
        sessionPrice: '',
        sessionTips: '',
        sessionSeats: '',
        seatNums: 0
      },
      //验证表单规则对象
      addFormRules: {
        cinemaId: [{
          required: true,
          message: "请选择影院",
          trigger: "change"
        }],
        hallId: [{
          required: true,
          message: '请选择影厅名称',
          trigger: 'change'
        }],
        languageVersion: [{
          required: true,
          message: '请输入电影语言',
          trigger: 'blur'
        }],
        movieId: [{
          required: true,
          message: '请选择电影名称',
          trigger: 'change'
        }],
        movieRuntimeId: [{
          required: true,
          message: '请选择播放时段',
          trigger: 'change'
        }],
        sessionDate: [{
          required: true,
          message: '请选择场次日期',
          trigger: 'blur'
        }],
        sessionPrice: [{
          required: true,
          message: '请输入场次价格',
          trigger: 'blur'
        }]
      },
      editDialogVisible: false,
      editForm: {},
      editFormRules: {
        cinemaId: [{
          required: true,
          message: "请选择影院",
          trigger: "change"
        }],
        hallId: [{
          required: true,
          message: '请选择影厅名称',
          trigger: 'change'
        }],
        languageVersion: [{
          required: true,
          message: '请输入电影语言',
          trigger: 'blur'
        }],
        movieId: [{
          required: true,
          message: '请选择电影名称',
          trigger: 'change'
        }],
        movieRuntimeId: [{
          required: true,
          message: '请选择播放时段',
          trigger: 'change'
        }],
        sessionDate: [{
          required: true,
          message: '请选择场次日期',
          trigger: 'blur'
        }],
        sessionPrice: [{
          required: true,
          message: '请输入场次价格',
          trigger: 'blur'
        }]
      },
      selectedCinemaId: '',
      selectedMovieId: '',
      selectedMovieRuntimeId: '',
      selectedDate: '',
      cinemaList: [],
      hallList: [],
      movieList: [],
      movieRuntimeList: [],
      states: [{
        id: 0,
        name: '已下架'
      }, {
        id: 1,
        name: '上映中'
      }],
      multipleSelection: [],
      checkDialogVisible: false,
      seats: {},
      col: '',
      isSelected: {
        0: 'seat-default',
        1: 'seat-choose',
        3: 'seat-sold'
      },
      editSeat: {},
      checkDialogWidth: '',
      pickerOptions: {
        disabledDate(time) {
          return time.getTime() > Date.now();
        }

      },
      rowNums: '',
      colNums: '',
      rowStart: '',
      curCinemaName: '',
      loginUser: JSON.parse(window.sessionStorage.getItem('loginUser'))
    };
  },

  created() {
    this.getSessionList();
    this.getHallList();
    this.getCinemaList();
    this.getMovieList();
    this.getMovieRuntimeList();
    this.curCinemaName = this.loginUser.cinemaName;
  },

  methods: {
    async getSessionList() {
      return runRead(this, async () => {
        this.queryInfo.cinemaId = this.selectedCinemaId; //非系统管理员只能查看自己影院的场次信息

        if (window.sessionStorage.getItem("btnPermission") === 'normal') {
          this.queryInfo.cinemaId = JSON.parse(window.sessionStorage.getItem('loginUser')).cinemaId;
        }

        this.queryInfo.movieId = this.selectedMovieId;
        this.queryInfo.movieRuntimeId = this.selectedMovieRuntimeId;
        this.queryInfo.sessionDate = this.selectedDate;
        console.log(String(this.selectedDate));

        const _this = this;

        await checkedRequest(axios.get('sysSession', {
          params: _this.queryInfo
        })).then(resp => {
          console.log(resp);
          _this.sessionList = resp.data.data;
          _this.total = resp.data.total;
          _this.queryInfo.pageSize = resp.data.pageSize;
          _this.queryInfo.pageNum = resp.data.pageNum;
        });
      });
    },

    handleSizeChange(newSize) {
      this.queryInfo.pageSize = newSize;
      this.getSessionList();
    },

    handleCurrentChange(newPage) {
      this.queryInfo.pageNum = newPage;
      this.getSessionList();
    },

    showAddDialog() {
      if (window.sessionStorage.getItem("btnPermission") !== "admin") this.addForm.cinemaId = this.loginUser.cinemaId;
      this.getHallList(this.addForm.cinemaId);
      this.addDialogVisible = true;
    },

    // 监听添加对话框的关闭事件
    addDialogClosed() {
      this.$refs.addFormRef.resetFields();
    },

    // 监听添加按钮
    async addSession() {
      return runMutation(this, "addSession", async () => {
        if (window.sessionStorage.getItem("btnPermission") !== "admin") this.addForm.cinemaId = this.loginUser.cinemaId;
        if (!(await validateForm(this.$refs.addFormRef))) return;
        const {
          data
        } = await checkedRequest(axios.get("sysHall/" + this.addForm.cinemaId + "/" + this.addForm.hallId));
        if (!data.data) throw Error("所选影厅不存在");
        const payload = { ...this.addForm,
          sessionPrice: Number(this.addForm.sessionPrice),
          sessionSeats: data.data.seatState,
          seatNums: 0
        };
        await checkedRequest(axios.post("sysSession", payload, {
          headers: {
            "Content-Type": "application/json"
          }
        }));
        this.addDialogVisible = false;
        this.getSessionList();
        this.$message.success("添加场次成功");
      });
    },

    // 显示修改对话框，回显数据
    async showEditDialog(id) {
      return runMutation(this, "showEditDialog", async () => {
        const {
          data
        } = await checkedRequest(axios.get("sysSession/" + id));
        this.editForm = data.data;
        await this.getHallList(this.editForm.cinemaId);
        this.editDialogVisible = true;
      });
    },

    // 监听修改对话框的关闭事件
    editDialogClosed() {
      this.$refs.editFormRef.resetFields();
    },

    // 修改场次信息并提交
    async editSessionInfo() {
      return runMutation(this, "editSessionInfo", async () => {
        if (!(await validateForm(this.$refs.editFormRef))) return;
        const {
          data
        } = await checkedRequest(axios.get("sysSession/" + this.editForm.sessionId));
        let seats = data.data.sessionSeats;
        let count = data.data.seatNums;

        if (data.data.hallId !== this.editForm.hallId) {
          const hall = await checkedRequest(axios.get("sysHall/" + this.editForm.cinemaId + "/" + this.editForm.hallId));
          seats = hall.data.data.seatState;
          count = 0;
        }

        await checkedRequest(axios.put("sysSession", { ...this.editForm,
          sessionPrice: Number(this.editForm.sessionPrice),
          sessionSeats: seats,
          seatNums: count
        }, {
          headers: {
            "Content-Type": "application/json"
          }
        }));
        this.editDialogVisible = false;
        this.getSessionList();
        this.$message.success("修改场次成功");
      });
    },

    // 监听多选框变化
    handleSelectionChange(val) {
      this.multipleSelection = val;
    },

    async multipleDelete() {
      return runMutation(this, "multipleDelete", async () => {
        if (!this.multipleSelection || !this.multipleSelection.length) {
          this.$message.warning('请先选择要删除的条目');
          return;
        }

        const _this = this; //询问用户是否确认删除


        const resp = await this.$confirm('此操作将永久删除这些条目, 是否继续?', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).catch(err => err); // 用户确认删除, resp为字符串"confirm"
        // 用户取消删除，resp为字符串"cancel"

        if (resp !== "confirm") {
          return _this.$message.info('已取消删除');
        }

        let ids = [];
        this.multipleSelection.forEach(data => {
          ids.push(data.sessionId);
        });
        await checkedRequest(axios.delete('sysSession/' + ids)).then(resp => {
          if (resp.data.code !== 200) {
            this.$message.success('批量删除场次失败！');
          }
        });
        this.getSessionList();
        this.$message.success('批量删除场次成功！');
      });
    },

    //根据id删除对应的影厅分类
    async deleteSessionById(id) {
      return runMutation(this, "deleteSessionById", async () => {
        const _this = this; //询问用户是否确认删除


        const resp = await this.$confirm('此操作将永久删除该条目, 是否继续?', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).catch(err => err); // 用户确认删除, resp为字符串"confirm"
        // 用户取消删除，resp为字符串"cancel"

        if (resp !== "confirm") {
          return _this.$message.info('已取消删除');
        }

        await checkedRequest(axios.delete('sysSession/' + id)).then(resp => {
          if (resp.data.code !== 200) {
            _this.$message.success('删除场次信息失败！');
          }
        });
        this.getSessionList();
        this.$message.success('删除场次信息成功！');
      });
    },

    async getCinemaList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysCinema')).then(resp => {
          _this.cinemaList = resp.data.data;
        });
      });
    },

    async getHallList(cinemaId) {
      return runRead(this, async () => {
        const id = cinemaId || (window.sessionStorage.getItem("btnPermission") === "admin" ? this.addForm.cinemaId : this.loginUser.cinemaId);

        if (!id) {
          this.hallList = [];
          return;
        }

        const {
          data
        } = await checkedRequest(axios.get("sysHall", {
          params: {
            cinemaId: id
          }
        }));
        if (data.code === 200) this.hallList = data.data;
      });
    },

    async getMovieList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysMovie')).then(resp => {
          _this.movieList = resp.data.data;
        });
      });
    },

    async getMovieRuntimeList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysMovieRuntime')).then(resp => {
          console.log(resp);
          _this.movieRuntimeList = resp.data.data;
        });
      });
    },

    async sessionStateChanged(sessionInfo) {
      return runMutation(this, "sessionStateChanged", async () => {
        const _this = this;

        axios.defaults.headers.put['Content-Type'] = 'application/json';
        await checkedRequest(axios.put('sysSession', JSON.stringify(sessionInfo))).then(resp => {
          if (resp.data.code !== 200) {
            _this.$message.error('更新场次状态失败！');
          }
        });
        this.getSessionList();
        this.$message.success('更新场次状态成功！');
      }, () => this.getSessionList());
    },

    async checkSeat(id) {
      return runMutation(this, "checkSeat", async () => {
        const _this = this;

        await checkedRequest(axios.get('sysSession/' + id)).then(resp => {
          _this.seats = JSON.parse(resp.data.data.sessionSeats);
          _this.col = resp.data.data.sysHall.seatNumsRow;
        });
        this.checkDialogWidth = 200 + this.col * 40 + 'px';
        this.checkDialogVisible = true;
      });
    },

    isCinemaOwner(cinemaId) {
      return window.sessionStorage.getItem("btnPermission") === "admin" || this.loginUser.cinemaId === cinemaId;
    }

  }
};
</script>

<style scoped>
.row{
  white-space: nowrap;
  margin-top: 10px;
  padding: 0 10px;
  text-align: center;
  display: flex;
  justify-content: space-between;
}
.seat{
  display: inline-block;
  width: 30px;
  height: 26px;
  margin: 0 5px;
  background-position: 0 1px;
}
.seat-default {
  background: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAeCAYAAAA7MK6iAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAAEnQAABJ0Ad5mH3gAAAHKSURBVEhLxZfbysIwDMezKeJUVAR9A/H9X0FQZMOLeem14sWc4AnP9muybM4557eT/mCYdLT/tknaqQgJ/ACVf7/Oz4TfbvVisYDlcgm1Wg2SRON0OoGqqtDr9bglAAoHGY1GQgqzl5zz+SyGwyF7z7wITyYTtrJD13W2HrzEuFqtspUdnU4Hbrcbew5fSa5SqfQbYbmzoCgKew5fK6disciWgyd8vV5hPB5DoVAA27bpSct6vQbLsmjFhmFwK0MpJjFNky2H3W4nZEf24rPZbMTlcmFPiPv9LgaDAXu+rNY0jS0HzG5Zh+zFRwo9bS/GuNvtgpwM+SQsVwetVosa/GDnpMhFsfUAJ3I4HMgmYRQIZl1awsbzTyYyqzHRkhK2Yj+RwklwBYMHRpDMhd0tTrXiT52jwCsxCrqPZc3RARLM7O1266V/XMrlMlQqFfYcVqsVZXa9Xo8Wzhq/cOYx/i+e8KeYZIFfgyyMxWw2o4Y8mc/nXty9jz08NqfTKTSbTapBLAt8kh6buDocGh+3xNrtNjQaDbLxRSj7/f7pNolLv98Xx+ORvVfeBla+S3VJ4K7hGO/I7S8MDht2UbjklspRokj+NRQKwB/pWISi3oSUQQAAAABJRU5ErkJggg==") no-repeat;
}
.seat-choose{
  background: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAeCAYAAAA7MK6iAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAAEnQAABJ0Ad5mH3gAAAKVSURBVEhLxZXPThpRFMY/hhFBG4okbdiYxgXpxmJi0phi4sKlL+HWlSsfwMSFr+DKjSufwMSFiyakaaagQaOxW1txUUjDHwVh5vacmdtxgGEsA1N/yYHLvdzzzT33nDMhQeAFUOT3f+fFhAeGutRs4tfpKV5dXECoqpz9R8hlKxqFsrqK96mUnOzGVfhrsYh3+/tIXV8Dk5Nydkh0HW3a+21zE59WVuTkE33C5/k8PmxvW4ITE/SPkFzxQacDUOS0nR18XFiQkxZ9dzx9dARwaCOR0UQZ9kP29vAQupz6S39y8UmVMeZcOIwInfp54QAQdJDe2LkLt9tAq2Vmp294L/vhe6Zxb13YwrSMvKZRZMIoLy2hvLYG3N/7E+c9FN7f2SzK8/MQiQS0szO5aGFndfHkBJndXSAeNzc2ZmehUw3GCwUru4eBTlpbXETs6grqwwNgGBBkX7a2kF1eNv9inziWy1minFxU/NM3N2jHYr5PbJAPtdGwHpp8hiiS6YMDUPBNTOE6WfL21sxAGx7Xav5KivaIx0fy7kghGqv0QHR+6yd/GGQUcx52M0Idu+0UDn+ORxovLp24i8CEO9SrvQhM+LlL8hQWnJHcBHxYiKvDA7OOqzTobGwgWalYjZ2hjlObm0N7Zmb4kqIkipZKmLq7e6oU8ldJJqHu7YGK1kOYke3OF1xKzvLsEfa+Y34IDrcfc4q6YAsrfDcBozgy3RSeIvuRyVidinsrNfixW7WKn+m0qcXYLwlum9+Pj5G4vITO709KEDaDmrsfFPLBrtnMrkhvqDfr63gt13nBlYZhiFyhIH8Nz2dNE005dmNgcgl6Fxv8dvGJXq9DcIgHYId63LBbvqpBeJfTCHiJMoEJewP8Afy6sw903o8jAAAAAElFTkSuQmCC") no-repeat;
}
.seat-sold{
  background: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAeCAYAAAA7MK6iAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAAEnQAABJ0Ad5mH3gAAANwSURBVEhLxZZLTBNRFIb/mWlpjbWAYgISNBofGyMGwQdEg0sTdaciaNy40UTxmeiKuDPGxwZXsjAuDNHohmhcYARjjGJBoYSFhAUYA+EpSksfQ8dz7gwwbacj5SEfXMq9c+757z3n3DuVNALLgGx8/neWTThlqIMjgL+3Gd3B11AklzE6NzTEkKFloTT/LNZtzDJG47EUbm/twouhk+hTOpAhGYNpMkXNSX+qPO+wv6xcHzSRJNzW/hm1A3vhcgD0C2mewswUeQ6T+IUcPwqLthujOkk5bh68AycpOklwIaKMQvMdCvBqsNoYmSVJOENeiQXqxcECEYzrHRP/paplKVnGUlil3ERiVJ2W9Z4atg9Gab4xN0qfXGRWB2dWmJ598bVAURzY4T6CA9mXEUpDnO1GQ8D5nfXYlFmMICkWeSux2XEQ3ow8fPX5DUudmapuamlA3dhReKiweCTXsRVrXdvQFWyAYw5JH54ErhY/pfqQ8aCtAnuyD6Mn0IgQ/bBAjBZyxvMBZaVlwn5mx22jT4Qon1sXjQ6o3+GWssWkKE0KUAitds9jLHqtuF707/oq4HGyDy8mtZA4HexToepuGD8tbBhdOAIMSa1xCeejMBEZFvkqyTuGm7ubMEKhNIvz/xxe3qlG2bzvq8Qat34MI1NBKirDkBC+ZRW0FoGuxbmkiSY7gUTV6CSL9z+e4+dEB2r2NYrdTYvzQq4Y4b3nq8JqQ5SRLC8Baea5LmxlY8JLV/Uj/0X0Bzpxq6xRCCaGN2fFrChjVclmzNG1hXdT57+E/okuXC95hnOFtXHhTURVqShssBVOXDWL885/h/vhdmSRaHx44/hHFG2FnbJbXCbmxmF/3FWNh+2nkEmifEEk2nBzKR5xIlKhn+MwcKNpA/44+kQ1M/xmyXfuglcpIAdUfWkgQ8Gg2olhtTvO3yp1PW6X99KqbIQZNrZbtR3sJtGXWdg21DyRb635NLOoFUKYa0ilq22piSE6cwcIYYmKZAuOI0CpDHOjh4vdJshvQeyQ0BKaIseERnl+2/oSPeE3kDWnuHm4xWLpFdY0siyL4yga7dXrysWJohpIdNEIWNiKqZCmtX7yG730afn4TdOiRseClMU1qQbopf7L6KVPIDyGkJq6bpK+ZS4W7Nb6RaFje5wWgp0os2TC9gB/AXsuAMA34noyAAAAAElFTkSuQmCC") no-repeat;
}
</style>