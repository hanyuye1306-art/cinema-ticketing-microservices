<template>
  <div class="user-info-container" v-loading="loading">
    <section class="profile-avatar">
      <h2>用户头像</h2>
      <el-upload action="" list-type="picture-card" accept="image/*" :auto-upload="false" :limit="1" :disabled="saving" :file-list="pics" :on-change="handleChange" :on-exceed="handleExceed">
        <i slot="default" class="el-icon-plus"></i>
        <div slot="file" slot-scope="{file}">
          <img class="el-upload-list__item-thumbnail" :src="pictureSource(file.url)" alt="用户头像">
          <span class="el-upload-list__item-actions"><span class="el-upload-list__item-delete" role="button" tabindex="0" aria-label="移除头像" @click="handleRemove(file)" @keydown.enter="handleRemove(file)"><i class="el-icon-delete"></i></span></span>
        </div>
      </el-upload>
      <p class="profile-help">最多上传一张头像，不选择新图片会保留原头像。</p>
    </section>
    <section class="profile-details">
      <h2>个人资料</h2>
      <el-form :model="loginUser" :rules="rules" ref="editFormRef" label-width="90px" :disabled="saving" @submit.native.prevent="editUser">
        <el-form-item label="用户名" prop="userName"><el-input v-model.trim="loginUser.userName" maxlength="20" autocomplete="username" /><p class="profile-help">用户名用于登录；修改成功后会同步更新登录状态。</p></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model.trim="loginUser.email" type="email" maxlength="254" placeholder="用于找回密码的邮箱" autocomplete="email" /></el-form-item>
        <el-form-item label="手机号" prop="phoneNumber"><el-input v-model.trim="loginUser.phoneNumber" maxlength="11" placeholder="选填" autocomplete="tel" /></el-form-item>
        <el-form-item label="性别" prop="sex"><el-radio v-model="loginUser.sex" :label="true">男</el-radio><el-radio v-model="loginUser.sex" :label="false">女</el-radio></el-form-item>
        <el-form-item label="生日"><el-date-picker v-model="information.birthday" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" /></el-form-item>
        <el-form-item label="生活状态"><el-radio v-model="information.condition" label="1">单身</el-radio><el-radio v-model="information.condition" label="2">热恋中</el-radio><el-radio v-model="information.condition" label="3">已婚</el-radio><el-radio v-model="information.condition" label="4">为人父母</el-radio></el-form-item>
        <el-form-item label="从事行业"><el-cascader v-model="information.job" :options="options" clearable /></el-form-item>
        <el-form-item label="兴趣爱好"><el-checkbox-group v-model="information.hobbies"><el-checkbox v-for="hobby in hobbyList" :label="hobby.userHobbyId" :key="hobby.userHobbyId">{{ hobby.userHobbyName }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="个性签名"><el-input v-model="information.autograph" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving" :disabled="loading">保存资料</el-button></el-form-item>
      </el-form>
    </section>
  </div>
</template>
<script>
const emptyInformation = () => ({ birthday: '', condition: '', job: [], hobbies: [], autograph: '' })
function parseJson(value, fallback) {
  try { return value ? JSON.parse(value) : fallback } catch (error) { return fallback }
}
export default {
  name: 'UserInfo',
  data() {
    return {
      loading: false, saving: false,
      loginUser: { userName: '', email: '', phoneNumber: '', sex: false },
      pics: [], hobbyList: [], information: emptyInformation(),
      rules: {
        userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 20, message: '用户名为2到20个字符', trigger: 'blur' }],
        email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
        phoneNumber: [{ pattern: /^(?:1[3-9]\d{9})?$/, message: '请输入正确的11位手机号', trigger: 'blur' }]
      },
      options: [
        { value: 1, label: '信息技术', children: [{ value: 11, label: '互联网' }, { value: 12, label: 'IT' }] },
        { value: 2, label: '金融保险', children: [{ value: 21, label: '投资股票' }, { value: 22, label: '保险' }] }
      ]
    }
  },
  methods: {
    pictureSource(url) {
      if (!url) return ''
      return /^(?:https?:|blob:|data:)/.test(url) ? url : this.global.base + url
    },
    async getUser() {
      const session = parseJson(window.sessionStorage.getItem('loginUser'), {})
      if (!session || !session.userId) { this.$router.push('/login'); return }
      this.loading = true
      try {
        const { data } = await axios.get('sysUser/' + session.userId)
        if (data.code !== 200 || !data.data) throw new Error(data.msg || '加载资料失败')
        this.loginUser = { ...data.data, email: data.data.email || '', phoneNumber: data.data.phoneNumber || '' }
        const stored = parseJson(this.loginUser.information, {})
        this.information = { ...emptyInformation(), ...(stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {}) }
        if (!Array.isArray(this.information.job)) this.information.job = []
        if (!Array.isArray(this.information.hobbies)) this.information.hobbies = []
        const pictures = parseJson(this.loginUser.userPicture, [])
        this.pics = (Array.isArray(pictures) ? pictures : []).filter(url => typeof url === 'string' && url).slice(0, 1).map(url => ({ name: '头像', url, status: 'success' }))
      } catch (error) { this.$message.error(error.message || '加载资料失败，请重试') }
      finally { this.loading = false }
    },
    async getHobbyList() {
      try {
        const { data } = await axios.get('sysUserHobby')
        if (data.code === 200 && Array.isArray(data.data)) this.hobbyList = data.data
      } catch (error) { this.$message.error('兴趣列表加载失败，请稍后刷新') }
    },
    handleChange(file, fileList) { this.pics = fileList.slice(0) },
    handleRemove(file) {
      if (!this.saving) this.pics = this.pics.filter(item => item.uid !== file.uid || item.url !== file.url)
    },
    handleExceed() { this.$message.error('用户头像最多上传一张') },
    async submitFile() {
      const pictures = []
      for (const picture of this.pics) {
        if (picture.uploadedUrl) { pictures.push(picture.uploadedUrl); continue }
        if (!picture.raw) {
          const url = picture.url || ''
          const index = url.indexOf('/images/')
          if (index < 0) throw new Error('头像地址无效，请重新选择图片')
          pictures.push(url.substring(index))
          continue
        }
        const formData = new FormData()
        formData.append('file', picture.raw)
        const { data } = await axios.post('upload/user', formData)
        if (data.code !== 200 || typeof data.data !== 'string' || !data.data.startsWith('/images/')) throw new Error(data.msg || '头像上传失败，请重试')
        this.$set(picture, 'uploadedUrl', data.data)
        pictures.push(data.data)
      }
      return pictures
    },
    async editUser() {
      if (this.saving || this.loading) return
      const valid = await new Promise(resolve => this.$refs.editFormRef.validate(resolve))
      if (!valid || this.saving) return
      this.saving = true
      try {
        const pictures = await this.submitFile()
        const payload = {
          userId: this.loginUser.userId, userName: this.loginUser.userName,
          email: this.loginUser.email, phoneNumber: this.loginUser.phoneNumber, sex: this.loginUser.sex,
          information: JSON.stringify(this.information), userPicture: JSON.stringify(pictures)
        }
        const { data } = await axios.put('sysUser', payload, { headers: { 'Content-Type': 'application/json' } })
        if (data.code !== 200) throw new Error(data.msg || '保存资料失败')
        if (data.data && data.data.token) window.sessionStorage.setItem('token', data.data.token)
        this.loginUser = { ...this.loginUser, ...payload, sysRole: null }
        delete this.loginUser.password
        delete this.loginUser.salt
        window.sessionStorage.setItem('loginUser', JSON.stringify(this.loginUser))
        this.pics = pictures.map(url => ({ name: '头像', url, status: 'success' }))
        this.$root.$emit('user-profile-updated', this.loginUser)
        this.$message.success('资料保存成功')
      } catch (error) { this.$message.error(error.message || '保存资料失败，请重试') }
      finally { this.saving = false }
    }
  },
  created() { this.getUser(); this.getHobbyList() }
}
</script>
<style scoped>
.user-info-container { display: flex; gap: 48px; max-width: 960px; margin: 0 auto; padding: 12px 0; }
.profile-avatar { width: 230px; flex-shrink: 0; }.profile-details { flex: 1; min-width: 0; }
h2 { margin: 0 0 28px; font-size: 20px; font-weight: 500; }.profile-help { margin: 6px 0 0; color: #9198a2; font-size: 12px; line-height: 1.7; }
@media (max-width: 760px) { .user-info-container { flex-direction: column; gap: 32px; }.profile-avatar { width: 100%; } }
</style>
