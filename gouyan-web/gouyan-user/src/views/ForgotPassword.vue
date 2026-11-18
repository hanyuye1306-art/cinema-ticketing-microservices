<template>
  <AccountLayout title="找回密码" subtitle="通过注册时绑定的邮箱，安全重置登录密码">
    <div v-if="complete" class="account-result" role="status"><i class="el-icon-circle-check"></i><h3>密码重置成功</h3><p>请使用新密码重新登录，继续你的观影之旅。</p><el-button class="account-submit" @click="$router.push({ path: '/login', query: { userName: form.userName } })">返回登录</el-button></div>
    <template v-else>
      <div class="recovery-steps"><span :class="{ active: !codeSent }">1. 验证账号</span><i class="el-icon-arrow-right"></i><span :class="{ active: codeSent }">2. 设置新密码</span></div>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="account-error" role="alert" />
      <el-alert v-if="notice" :title="notice" type="info" show-icon :closable="false" class="account-error" role="status" />
      <el-form ref="form" :model="form" :rules="rules" class="account-form" label-position="top" :disabled="submitting || verifying" @submit.native.prevent="codeSent ? resetPassword() : startVerification()">
        <el-form-item label="用户名" prop="userName"><el-input v-model.trim="form.userName" placeholder="请输入需要找回的用户名" maxlength="20" autocomplete="username" :disabled="codeSent" /></el-form-item>
        <el-form-item label="绑定邮箱" prop="email"><el-input v-model.trim="form.email" type="email" placeholder="请输入注册时填写的邮箱" maxlength="254" autocomplete="email" :disabled="codeSent" /></el-form-item>
        <template v-if="codeSent">
          <el-form-item label="邮箱验证码" prop="code"><div class="code-row"><el-input v-model.trim="form.code" placeholder="6位数字" maxlength="6" inputmode="numeric" autocomplete="one-time-code" /><el-button :disabled="countdown > 0 || submitting || verifying" @click="startVerification">{{ countdown > 0 ? countdown + '秒后重发' : '重新获取' }}</el-button></div></el-form-item>
          <el-form-item label="新密码" prop="password"><el-input v-model="form.password" placeholder="6–16个字符" show-password maxlength="16" autocomplete="new-password" @input="revalidateConfirmation" /></el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" placeholder="请再次输入新密码" show-password maxlength="16" autocomplete="new-password" /></el-form-item>
        </template>
        <el-button native-type="submit" class="account-submit" :loading="submitting" :disabled="verifying || (!codeSent && countdown > 0)">{{ verifying ? '请完成图形验证' : codeSent ? '确认重置密码' : countdown > 0 ? countdown + '秒后可获取' : '验证并获取邮箱验证码' }}</el-button>
      </el-form>
      <p class="account-help recovery-help">验证码10分钟内有效。未绑定邮箱或无法访问邮箱？请联系管理员核实身份后处理。</p>
      <div class="account-links"><el-button v-if="codeSent" type="text" class="account-inline-link" :disabled="submitting || verifying" @click="changeIdentity">修改账号或邮箱</el-button><span v-else>想起密码了？</span><router-link to="/login">返回登录</router-link></div>
      <Verify ref="verify" mode="pop" captcha-type="blockPuzzle" :img-size="captchaSize" @success="sendCode" @close="verifying = false" />
    </template>
  </AccountLayout>
</template>
<script>
import AccountLayout from '@/components/account/AccountLayout'
import Verify from '@/components/verifition/Verify'
import '@/assets/css/account.css'
export default {
  name: 'ForgotPassword', components: { AccountLayout, Verify },
  data() {
    const confirm = (rule, value, callback) => callback(value === this.form.password ? undefined : new Error('两次输入的密码不一致'))
    return {
      form: { userName: '', email: '', code: '', password: '', confirmPassword: '' },
      rules: {
        userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 20, message: '用户名为2–20个字符', trigger: 'blur' }],
        email: [{ required: true, message: '请输入绑定邮箱', trigger: 'blur' }, { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
        code: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }, { pattern: /^\d{6}$/, message: '请输入6位数字验证码', trigger: 'blur' }],
        password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, max: 16, message: '密码为6–16个字符', trigger: 'blur' }],
        confirmPassword: [{ required: true, message: '请确认新密码', trigger: 'blur' }, { validator: confirm, trigger: 'blur' }]
      },
      submitting: false, verifying: false, codeSent: false, complete: false, notice: '', errorMessage: '', pending: null, countdown: 0, timer: null,
      captchaSize: { width: '280px', height: '132px' }
    }
  },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    revalidateConfirmation() { if (this.form.confirmPassword) this.$refs.form.validateField('confirmPassword') },
    async startVerification() {
      if (this.submitting || this.verifying || this.countdown > 0) return
      this.verifying = true
      const results = await Promise.all(['userName', 'email'].map(field => new Promise(resolve => {
        this.$refs.form.validateField(field, error => resolve(!error))
      })))
      if (results.some(valid => !valid)) { this.verifying = false; return }
      this.errorMessage = ''; this.pending = { userName: this.form.userName, email: this.form.email }; this.$refs.verify.show()
    },
    async sendCode({ captchaVerification }) {
      if (this.submitting || !this.pending) return
      this.submitting = true; this.verifying = false
      try {
        const { data } = await axios.post('sysUser/password/code', { ...this.pending, captchaVerification }, { headers: { 'Content-Type': 'application/json' } })
        if (data.code !== 200) { this.errorMessage = data.msg || '获取失败，请重试'; return }
        this.notice = data.msg; this.codeSent = true; this.form.code = ''; this.countdown = 60
        clearInterval(this.timer)
        this.timer = setInterval(() => { if (this.countdown > 0) this.countdown--; else clearInterval(this.timer) }, 1000)
      } catch (error) { this.errorMessage = '未能获取验证码，请检查网络后重试' }
      finally { this.submitting = false; this.pending = null }
    },
    resetPassword() {
      if (this.submitting || this.verifying) return
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.submitting = true; this.errorMessage = ''
        try {
          const { data } = await axios.post('sysUser/password/reset', this.form, { headers: { 'Content-Type': 'application/json' } })
          if (data.code !== 200) { this.errorMessage = data.msg || '重置失败，请重试'; return }
          window.sessionStorage.removeItem('token'); window.sessionStorage.removeItem('loginUser')
          this.form.password = ''; this.form.confirmPassword = ''; this.form.code = ''; this.complete = true; clearInterval(this.timer)
        } catch (error) { this.errorMessage = '重置未完成，请检查网络后重试' }
        finally { this.submitting = false }
      })
    },
    changeIdentity() {
      this.codeSent = false; this.notice = ''; this.errorMessage = ''; this.form.code = ''; this.form.password = ''; this.form.confirmPassword = ''
      this.$nextTick(() => this.$refs.form.clearValidate())
    }
  }
}
</script>
<style scoped>
.recovery-steps { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; padding-bottom: 18px; border-bottom: 1px solid #f0f1f3; font-size: 13px; color: #a3a9b3; }.recovery-steps .active { color: #df4c4c; font-weight: 600; }
.code-row { display: flex; gap: 10px; }.code-row .el-input { flex: 1; min-width: 0; }.code-row .el-button { padding: 0 12px; }.recovery-help { margin-top: 16px; }
</style>
