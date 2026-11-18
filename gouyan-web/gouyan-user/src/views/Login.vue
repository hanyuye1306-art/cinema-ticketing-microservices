<template>
  <AccountLayout title="欢迎回来" subtitle="登录账号，继续你的观影计划">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="account-error" role="alert" />
    <el-form ref="form" :model="form" :rules="rules" class="account-form" label-position="top" :disabled="submitting || verifying" @submit.native.prevent="startVerification">
      <el-form-item label="用户名" prop="userName"><el-input v-model.trim="form.userName" prefix-icon="el-icon-user" placeholder="请输入用户名" maxlength="20" autocomplete="username" /></el-form-item>
      <el-form-item label="密码" prop="password"><el-input v-model="form.password" prefix-icon="el-icon-lock" placeholder="请输入密码" show-password maxlength="16" autocomplete="current-password" /></el-form-item>
      <div class="forgot-link"><router-link to="/forgot-password" class="account-inline-link">忘记密码？</router-link></div>
      <el-button native-type="submit" class="account-submit" :loading="submitting" :disabled="verifying">{{ verifying ? '请完成图形验证' : '验证并登录' }}</el-button>
    </el-form>
    <div class="account-links"><span>还没有账号？</span><router-link to="/register">立即注册 <i class="el-icon-right"></i></router-link></div>
    <Verify ref="verify" mode="pop" captcha-type="blockPuzzle" :img-size="captchaSize" @success="login" @close="verifying = false" />
  </AccountLayout>
</template>
<script>
import AccountLayout from '@/components/account/AccountLayout'
import Verify from '@/components/verifition/Verify'
import '@/assets/css/account.css'
export default {
  name: 'Login', components: { AccountLayout, Verify },
  data() {
    return {
      form: { userName: this.$route.query.userName || '', password: '' },
      rules: {
        userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 20, message: '用户名为2–20个字符', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, max: 16, message: '密码为6–16个字符', trigger: 'blur' }]
      },
      submitting: false, verifying: false, errorMessage: '', pending: null,
      captchaSize: { width: '280px', height: '132px' }
    }
  },
  methods: {
    startVerification() {
      if (this.submitting || this.verifying) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.errorMessage = ''; this.pending = { ...this.form }; this.verifying = true; this.$refs.verify.show()
      })
    },
    async login({ captchaVerification }) {
      if (this.submitting || !this.pending) return
      this.submitting = true; this.verifying = false
      try {
        const { data } = await axios.post('sysUser/login', { ...this.pending, captchaVerification }, { headers: { 'Content-Type': 'application/json' } })
        if (data.code !== 200) { this.errorMessage = data.msg || '登录失败，请重试'; return }
        if (!data.data || !data.data.token || !data.data.sysUser) throw new Error('Invalid login response')
        window.sessionStorage.setItem('token', data.data.token)
        data.data.sysUser.sysRole = null
        window.sessionStorage.setItem('loginUser', JSON.stringify(data.data.sysUser))
        this.$message.success({ message: '登录成功', duration: 1000 })
        this.$router.push('/welcome')
      } catch (error) { this.errorMessage = '登录未完成，请检查网络后重新验证' }
      finally { this.submitting = false; this.pending = null }
    }
  }
}
</script>
<style scoped>
.forgot-link { text-align: right; margin: -6px 0 22px; font-size: 13px; }
</style>
