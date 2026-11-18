<template>
  <div>
    <!--面包屑导航区域-->
    <div class="board">
      <el-breadcrumb separator-class="el-icon-arrow-right">
        <el-breadcrumb-item :to="{ path: '/welcome' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>影院管理</el-breadcrumb-item>
        <el-breadcrumb-item>影院信息管理</el-breadcrumb-item>
      </el-breadcrumb>
    </div>


    <!--卡片视图-->
    <el-card class="box-card">
      <el-row :gutter="20">

        <el-col :span="4">
          <el-select v-model="selectedCinemaBrandId" placeholder="请选择影院品牌" clearable>
            <el-option
                v-for="item in cinemaBrandList"
                :key="item.cinemaBrandId"
                :label="item.cinemaBrandName"
                :value="item.cinemaBrandId">
            </el-option>
          </el-select>
        </el-col>

        <el-col :span="4">
          <el-select v-model="selectedCinemaAreaId" placeholder="请选择影院区域" clearable>
            <el-option
                v-for="item in cinemaAreaList"
                :key="item.cinemaAreaId"
                :label="item.cinemaAreaName"
                :value="item.cinemaAreaId">
            </el-option>
          </el-select>
        </el-col>

        <el-col :span="4">
          <el-select v-model="selectedTicketState" placeholder="请选择是否支持改签" clearable>
            <el-option v-for="item in state1" :key="item.id" :label="item.name" :value="item.id"></el-option>
          </el-select>
        </el-col>

        <el-col :span="2">
          <el-button icon="el-icon-search" @click="getCinemaList">搜索</el-button>
        </el-col>
        <el-col :span="2">
          <el-button type="primary" @click="showAddDialog">添加影院</el-button>
        </el-col>
        <el-col :span="2">
          <el-button type="danger" @click="multipleDelete">批量删除</el-button>
        </el-col>
      </el-row>
      <el-row :gutter="20" class="row2">
        <el-col :span="4">
          <el-select v-model="selectedRefundState" placeholder="请选择是否支持退票" clearable>
            <el-option v-for="item in state2" :key="item.id" :label="item.name" :value="item.id"></el-option>
          </el-select>
        </el-col>

        <el-col :span="4">
          <el-input placeholder="请输入影院名称" v-model="inputCinemaName" />
        </el-col>
        <el-col :span="4">
          <el-input placeholder="请输入影院地址" v-model="inputCinemaAddress" />
        </el-col>
      </el-row>

      <el-table :data="cinemaList" style="width: 100%" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="cinemaName" label="影院名称" width="140px"></el-table-column>
        <el-table-column prop="sysCinemaBrand.cinemaBrandName" label="影院品牌"></el-table-column>
        <el-table-column prop="isTicketChanged" label="是否改签">
          <template slot-scope="scope">
            <el-switch v-model="scope.row.isTicketChanged" @change="stateChanged(scope.row)"></el-switch>
          </template>
        </el-table-column>
        <el-table-column prop="isRefunded" label="是否退款">
          <template slot-scope="scope">
            <el-switch v-model="scope.row.isRefunded" @change="stateChanged(scope.row)"></el-switch>
          </template>
        </el-table-column>
        <el-table-column prop="sysCinemaArea.city" label="城市"></el-table-column>
        <el-table-column prop="sysCinemaArea.cinemaAreaName" label="区域"></el-table-column>
        <el-table-column prop="cinemaAddress" label="影院地址" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="user.userName" label="管理员"></el-table-column>
        <el-table-column prop="entryDate" label="入驻日期"></el-table-column>

        <el-table-column label="操作" width="180">
          <template slot-scope="scope">
            <el-tooltip effect="dark" content="修改影院信息" placement="top" :enterable="false" :open-delay="500">
              <el-button type="primary" icon="el-icon-edit" size="mini" @click="showEditDialog(scope.row.cinemaId)"></el-button>
            </el-tooltip>
            <el-tooltip effect="dark" content="删除影院" placement="top" :enterable="false" :open-delay="500">
              <el-button type="danger" icon="el-icon-delete" size="mini" @click="deleteCinemaById(scope.row.cinemaId)"></el-button>
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
        <el-form-item label="影院名称" prop="cinemaName">
          <el-input v-model="addForm.cinemaName"></el-input>
        </el-form-item>
        <el-form-item label="影院品牌" prop="cinemaBrandId">
          <el-select v-model="addForm.cinemaBrandId" placeholder="请选择影院品牌" clearable >
            <el-option
                v-for="item in cinemaBrandList"
                :key="item.cinemaBrandId"
                :label="item.cinemaBrandName"
                :value="item.cinemaBrandId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="影院区域" prop="cinemaAreaId">
          <el-select v-model="addForm.cinemaAreaId" placeholder="请选择影院区域" clearable >
            <el-option
                v-for="item in cinemaAreaList"
                :key="item.cinemaAreaId"
                :label="item.cinemaAreaName"
                :value="item.cinemaAreaId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="影院地址" prop="cinemaAddress">
          <el-input v-model="addForm.cinemaAddress"></el-input>
        </el-form-item>
        <el-form-item label="用户" prop="userId">
          <el-select v-model="addForm.userId" placeholder="请选择用户" clearable >
            <el-option
                v-for="item in userList"
                :key="item.userId"
                :label="item.userName"
                :value="item.userId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="入驻日期" prop="entryDate">
          <el-date-picker
              v-model="addForm.entryDate"
              value-format="yyyy-MM-dd"
              type="date"
              placeholder="选择日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="影院图片">
          <el-upload action=""
                     list-type="picture-card"
                     :auto-upload="false"
                     :file-list="pics"
                     :on-change="handleChange"
                     :on-success="handleSuccess"
                     :on-error="handleError"
                     ref="pictureRef"
                     :http-request="submitFile">
            <i slot="default" class="el-icon-plus"></i>
            <div slot="file" slot-scope="{file}">
              <img class="el-upload-list__item-thumbnail" :src="file.url" alt="">
              <span class="el-upload-list__item-actions">
                <span class="el-upload-list__item-preview" @click="handlePictureCardPreview(file)">
                  <i class="el-icon-zoom-in"></i>
                </span>
                <span class="el-upload-list__item-delete" @click="handleRemove(file)">
                  <i class="el-icon-delete"></i>
                </span>
              </span>
            </div>
          </el-upload>
          <!--放大预览-->
          <el-dialog :visible.sync="dialogVisible">
            <img width="100%" :src="dialogImageUrl" alt="">
          </el-dialog>
        </el-form-item>
      </el-form>
      <!--底部区域-->
      <span slot="footer" class="dialog-footer">
      <el-button @click="addDialogVisible = false">取 消</el-button>
      <el-button type="primary" @click="addCinema">确 定</el-button>
      </span>
    </el-dialog>

    <!--修改影院对话框-->
    <el-dialog title="修改影院" :visible.sync="editDialogVisible" width="60%" @close="editDialogClosed">
      <el-form :model="editForm" :rules="editFormRules" ref="editFormRef" label-width="150px">
        <el-form-item label="影院名称" prop="cinemaName">
          <el-input v-model="editForm.cinemaName"></el-input>
        </el-form-item>
        <el-form-item label="影院品牌" prop="cinemaBrandId">
          <el-select v-model="editForm.cinemaBrandId" placeholder="请选择影院品牌" clearable >
            <el-option
                v-for="item in cinemaBrandList"
                :key="item.cinemaBrandId"
                :label="item.cinemaBrandName"
                :value="item.cinemaBrandId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="影院区域" prop="cinemaAreaId">
          <el-select v-model="editForm.cinemaAreaId" placeholder="请选择影院区域" clearable >
            <el-option
                v-for="item in cinemaAreaList"
                :key="item.cinemaAreaId"
                :label="item.cinemaAreaName"
                :value="item.cinemaAreaId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="影院地址" prop="cinemaAddress">
          <el-input v-model="editForm.cinemaAddress"></el-input>
        </el-form-item>
        <el-form-item label="用户" prop="userId">
          <el-select v-model="editForm.userId" placeholder="请选择用户" clearable >
            <el-option
                v-for="item in userList"
                :key="item.userId"
                :label="item.userName"
                :value="item.userId">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="入驻日期" prop="entryDate">
          <el-date-picker
              v-model="editForm.entryDate"
              value-format="yyyy-MM-dd"
              type="date"
              placeholder="选择日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="影院图片">
          <el-upload action="" list-type="picture-card" :auto-upload="false"
                     :file-list="pics" :on-change="handleChange" :on-success="handleSuccess" :on-error="handleError" ref="pictureEditRef" :http-request="submitFile">
            <i slot="default" class="el-icon-plus"></i>
            <div slot="file" slot-scope="{file}">
              <img class="el-upload-list__item-thumbnail" :src="file.url" alt="">
              <span class="el-upload-list__item-actions">
                <span class="el-upload-list__item-preview" @click="handlePictureCardPreview(file)">
                  <i class="el-icon-zoom-in"></i>
                </span>
                <span class="el-upload-list__item-delete" @click="handleRemove(file)">
                  <i class="el-icon-delete"></i>
                </span>
              </span>
            </div>
          </el-upload>
          <!--放大预览-->
          <el-dialog :visible.sync="dialogVisible">
            <img width="100%" :src="dialogImageUrl" alt="">
          </el-dialog>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="cancelEdit">取 消</el-button>
        <el-button type="primary" @click="editCinemaInfo">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { checkedRequest, validateForm, runMutation, pictureArray, uploadPictures, runRead } from "@/utils/adminCrud";
import global from "@/assets/css/global.css";
export default {
  data() {
    return {
      queryInfo: {
        cinemaName: '',
        cinemaBrandId: '',
        cinemaAddress: '',
        cinemaAreaId: '',
        isTicketChanged: '',
        isRefunded: '',
        pageNum: 1,
        pageSize: 7
      },
      cinemaList: [],
      total: 0,
      //控制对话框的显示与隐藏
      addDialogVisible: false,
      //添加影院的表单数据
      addForm: {
        cinemaId: '',
        cinemaName: '',
        cinemaBrandId: '',
        cinemaPicture: '',
        isTicketChanged: true,
        isRefunded: true,
        cinemaAreaId: '',
        cinemaAddress: '',
        userId: '',
        entryDate: ''
      },
      //验证表单规则对象
      addFormRules: {
        cinemaName: [{
          required: true,
          message: '请输入影院名称',
          trigger: 'change'
        }],
        cinemaBrandId: [{
          required: true,
          message: '请选择影院品牌',
          trigger: 'change'
        }],
        cinemaAreaId: [{
          required: true,
          message: '请选择影院区域',
          trigger: 'blur'
        }],
        cinemaAddress: [{
          required: true,
          message: '请输入影院地址',
          trigger: 'change'
        }],
        entryDate: [{
          required: true,
          message: '请选择场次日期',
          trigger: 'blur'
        }]
      },
      editDialogVisible: false,
      editForm: {},
      editFormRules: {
        cinemaName: [{
          required: true,
          message: '请输入影院名称',
          trigger: 'change'
        }],
        cinemaBrandId: [{
          required: true,
          message: '请选择影院品牌',
          trigger: 'change'
        }],
        cinemaAreaId: [{
          required: true,
          message: '请选择影院区域',
          trigger: 'blur'
        }],
        cinemaAddress: [{
          required: true,
          message: '请输入影院地址',
          trigger: 'change'
        }],
        entryDate: [{
          required: true,
          message: '请选择场次日期',
          trigger: 'blur'
        }]
      },
      inputCinemaName: '',
      inputCinemaAddress: '',
      selectedCinemaBrandId: '',
      selectedCinemaAreaId: '',
      selectedTicketState: '',
      selectedRefundState: '',
      cinemaBrandList: [],
      cinemaAreaList: [],
      userList: [],
      state1: [{
        id: 0,
        name: '不可改签'
      }, {
        id: 1,
        name: '可改签'
      }],
      state2: [{
        id: 0,
        name: '不可退款'
      }, {
        id: 1,
        name: '可退款'
      }],
      multipleSelection: [],
      pickerOptions: {
        disabledDate(time) {
          return time.getTime() > Date.now();
        }

      },
      dialogImageUrl: '',
      dialogVisible: false,
      hideUpload: false,
      //添加删除图片 动态绑定图片列表
      pics: [],
      // 发送给后端的JSON图片数组
      pictureList: [],
      picNums: 0,
      deletePicList: []
    };
  },

  created() {
    this.getCinemaList();
    this.getCinemaBrandList();
    this.getCinemaAreaList();
    this.getUserList();
  },

  methods: {
    async getCinemaList() {
      return runRead(this, async () => {
        this.queryInfo.cinemaName = this.inputCinemaName;
        this.queryInfo.cinemaAddress = this.inputCinemaAddress;
        this.queryInfo.cinemaBrandId = this.selectedCinemaBrandId;
        this.queryInfo.cinemaAreaId = this.selectedCinemaAreaId;
        this.queryInfo.isTicketChanged = this.selectedTicketState;
        this.queryInfo.isRefunded = this.selectedRefundState;

        const _this = this;

        await checkedRequest(axios.get('sysCinema', {
          params: _this.queryInfo
        })).then(resp => {
          console.log(resp);
          _this.cinemaList = resp.data.data;
          _this.total = resp.data.total;
          _this.queryInfo.pageSize = resp.data.pageSize;
          _this.queryInfo.pageNum = resp.data.pageNum;
        });
      });
    },

    handleSizeChange(newSize) {
      this.queryInfo.pageSize = newSize;
      this.getCinemaList();
    },

    handleCurrentChange(newPage) {
      this.queryInfo.pageNum = newPage;
      this.getCinemaList();
    },

    showAddDialog() {
      this.addDialogVisible = true;
    },

    // 监听添加对话框的关闭事件
    addDialogClosed() {
      this.$refs.addFormRef.resetFields();
      this.$refs.pictureRef.clearFiles();
      this.pics = [];
      this.pictureList = [];
    },

    // 监听添加按钮
    async addCinema() {
      return runMutation(this, "addCinema", async () => {
        await this.submitFile();
        this.addForm.cinemaPicture = JSON.stringify(this.pictureList);

        const _this = this;

        const _valid = await validateForm(this.$refs.addFormRef);

        if (!_valid) return; // 预校验成功，发网络请求

        axios.defaults.headers.post['Content-Type'] = 'application/json';
        await checkedRequest(axios.post('sysCinema', JSON.stringify(_this.addForm))).then(resp => {
          if (resp.data.code !== 200) {
            this.$message.error('添加影院信息失败！');
          }
        }); //隐藏添加对话框

        this.addDialogVisible = false; //重新加载列表

        this.getCinemaList();
        this.$message.success('添加影院信息成功！');
      });
    },

    // 显示修改对话框，回显数据
    async showEditDialog(id) {
      return runMutation(this, "showEditDialog", async () => {
        this.pictureList = [];
        this.deletePicList = [];
        this.pics = [];

        const _this = this;

        await checkedRequest(axios.get('sysCinema/' + id)).then(resp => {
          _this.editForm = resp.data.data;
        });

        for (const item of pictureArray(this.editForm.cinemaPicture)) {
          let pic = {};
          pic['name'] = '';
          pic['url'] = this.global.base + item;
          this.pics.push(pic);
        }

        this.editDialogVisible = true;
      });
    },

    // 监听修改对话框的关闭事件
    editDialogClosed() {
      this.$refs.editFormRef.resetFields();
      this.$refs.pictureEditRef.clearFiles();
      this.pics = [];
      this.pictureList = [];
    },

    // 取消修改
    cancelEdit() {
      this.editDialogVisible = false;
      this.deletePicList.splice(0, this.deletePicList.length);
    },

    async editCinemaInfo() {
      return runMutation(this, "editCinemaInfo", async () => {
        await this.submitFile();
        this.editForm.cinemaPicture = JSON.stringify(this.pictureList);

        const _valid2 = await validateForm(this.$refs.editFormRef);

        const _this = this;

        if (!_valid2) return;
        let success = true;
        axios.defaults.headers.put['Content-Type'] = 'application/json';
        await checkedRequest(axios.put('/sysCinema', JSON.stringify(_this.editForm))).then(resp => {
          if (resp.data.code !== 200) {
            this.$message.error('修改影院信息失败！');
            success = false;
          }
        });
        if (!success) return;
        this.editDialogVisible = false;
        this.getCinemaList();
        this.$message.success('修改影院信息成功！');

        for (let s of this.deletePicList) {
          try { await checkedRequest(axios.get('/upload/delete', {params:{filePath:s.substring(s.indexOf('/images'))}})); } catch(error) { this.$message.warning('资料已保存，旧图片清理失败'); }
        }
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
          ids.push(data.cinemaId);
        });
        await checkedRequest(axios.delete('sysCinema/' + ids)).then(resp => {
          if (resp.data.code !== 200) {
            this.$message.success('批量删除影院失败！');
          }
        });
        this.getCinemaList();
        this.$message.success('批量删除影院成功！');
      });
    },

    //根据id删除对应的影厅分类
    async deleteCinemaById(id) {
      return runMutation(this, "deleteCinemaById", async () => {
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

        await checkedRequest(axios.delete('sysCinema/' + id)).then(resp => {
          if (resp.data.code !== 200) {
            _this.$message.success('删除影院信息失败！');
          }
        });
        this.getCinemaList();
        this.$message.success('删除影院信息成功！');
      });
    },

    async getCinemaBrandList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysCinemaBrand')).then(resp => {
          console.log(resp);
          _this.cinemaBrandList = resp.data.data;
        });
      });
    },

    async getCinemaAreaList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysCinemaArea')).then(resp => {
          console.log(resp);
          _this.cinemaAreaList = resp.data.data;
        });
      });
    },

    async getUserList() {
      return runRead(this, async () => {
        const _this = this;

        await checkedRequest(axios.get('sysUser')).then(resp => {
          console.log(resp);
          _this.userList = resp.data.data;
        });
      });
    },

    async stateChanged(cinemaInfo) {
      return runMutation(this, "stateChanged", async () => {
        const _this = this;

        axios.defaults.headers.put['Content-Type'] = 'application/json';
        await checkedRequest(axios.put('sysCinema', JSON.stringify(cinemaInfo))).then(resp => {
          if (resp.data.code !== 200) {
            _this.$message.error('更新状态失败！');
          }
        });
        this.getCinemaList();
        this.$message.success('更新状态成功！');
      }, () => this.getCinemaList());
    },

    handleRemove(file, fileList) {
      const filePath = file.url;
      const idx = this.pics.findIndex(x => x.url === filePath);

      if (file.status === 'success') {
        this.deletePicList.push(file.url);
      }

      this.pics.splice(idx, 1);
    },

    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url;
      this.dialogVisible = true;
    },

    handleChange(file, filelist) {
      this.pics = filelist.slice(0);
    },

    handleSuccess(response) {
      this.pictureList.push(response.data);
    },

    handleError(err) {},

    async submitFile() {
      this.pictureList = await uploadPictures(this, this.pics, "cinema");
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

.row2{
  margin-top: 20px;
}

</style>