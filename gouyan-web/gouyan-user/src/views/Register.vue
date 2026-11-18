<template>
  <AccountLayout title="创建账号" subtitle="加入狗眼电影，开启你的光影之旅">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="account-error" role="alert" />
    <el-form ref="form" :model="form" :rules="rules" class="account-form" label-position="top" :disabled="submitting || verifying" @submit.native.prevent="startVerification">
      <el-form-item label="用户名" prop="userName"><el-input v-model.trim="form.userName" placeholder="2–20个字符" maxlength="20" autocomplete="username" /></el-form-item>
      <el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" type="email" placeholder="请输入本人可用邮箱" maxlength="254" autocomplete="email" /><p class="account-help">用于找回密码，请确认邮箱填写正确。</p></el-form-item>
      <el-form-item label="密码" prop="password"><el-input v-model="form.password" placeholder="6–16个字符" show-password maxlength="16" autocomplete="new-password" @input="revalidateConfirmation" /></el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" placeholder="请再次输入密码" show-password maxlength="16" autocomplete="new-password" /></el-form-item>
      <el-form-item label="性别"><el-radio-group v-model="form.sex"><el-radio :label="false">女</el-radio><el-radio :label="true">男</el-radio></el-radio-group></el-form-item>
      <el-button native-type="submit" class="account-submit" :loading="submitting" :disabled="verifying">{{ verifying ? '请完成图形验证' : '验证并注册' }}</el-button>
    </el-form>
    <div class="account-links"><span>已有账号？</span><router-link to="/login">立即登录 <i class="el-icon-right"></i></router-link></div>
    <Verify ref="verify" mode="pop" captcha-type="blockPuzzle" :img-size="captchaSize" @success="register" @close="verifying = false" />
  </AccountLayout>
</template>
<script>
import AccountLayout from '@/components/account/AccountLayout'
import Verify from '@/components/verifition/Verify'
import '@/assets/css/account.css'
export default {
  name: 'Register', components: { AccountLayout, Verify },
  data() {
    const confirm = (rule, value, callback) => callback(value === this.form.password ? undefined : new Error('两次输入的密码不一致'))
    return {
      form: { userName: '', email: '', password: '', confirmPassword: '', sex: false },
      rules: {
        userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 20, message: '用户名为2–20个字符', trigger: 'blur' }],
        email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, max: 16, message: '密码为6–16个字符', trigger: 'blur' }],
        confirmPassword: [{ required: true, message: '请再次输入密码', trigger: 'blur' }, { validator: confirm, trigger: 'blur' }]
      },
      submitting: false, verifying: false, errorMessage: '', pending: null,
      captchaSize: { width: '280px', height: '132px' }
    }
  },
  methods: {
    revalidateConfirmation() { if (this.form.confirmPassword) this.$refs.form.validateField('confirmPassword') },
    startVerification() {
      if (this.submitting || this.verifying) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.errorMessage = ''; this.pending = { ...this.form }; this.verifying = true; this.$refs.verify.show()
      })
    },
    async register({ captchaVerification }) {
      if (this.submitting || !this.pending) return
      this.submitting = true; this.verifying = false
      try {
        const { data } = await axios.post('sysUser/register', { ...this.pending, captchaVerification }, { headers: { 'Content-Type': 'application/json' } })
        if (data.code !== 200) { this.errorMessage = data.msg || '注册失败，请重试'; return }
        this.$message.success('注册成功，请使用新账号登录')
        this.$router.push({ path: '/login', query: { userName: this.pending.userName } })
      } catch (error) { this.errorMessage = '注册未完成，请检查网络后重新验证' }
      finally { this.submitting = false; this.pending = null }
    }
  }
}
</script>
