const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const compiler = require('vue-template-compiler')
const babel = require('@babel/core')

function instance(file, api = {}) {
  const storage = new Map([['loginUser', JSON.stringify({ userId: 42, userName: 'old-user' })]])
  const calls = { success: [], error: [], events: [] }
  const module = { exports: {} }
  const source = compiler.parseComponent(fs.readFileSync(file, 'utf8')).script.content
  const { code } = babel.transformSync(source, { babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] })
  const globals = {
    axios: api, console,
    window: { sessionStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) } },
    FormData: class { append() {} }
  }
  const load = name => {
    if (name.endsWith('.css')) return {}
    if (name === '@/utils/adminCrud') {
      const helper = { exports: {} }
      const source = fs.readFileSync(path.resolve(__dirname, '../../gouyan-ui/src/utils/adminCrud.js'), 'utf8')
      const output = babel.transformSync(source, { babelrc: false, configFile: false,
        plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
      vm.runInNewContext(output, { ...globals, module: helper, exports: helper.exports })
      return helper.exports
    }
    return require(name)
  }
  vm.runInNewContext(code, { ...globals, module, exports: module.exports, require: load })
  const component = module.exports.default
  const user = {
    global: { base: 'http://localhost:8181' },
    $set: (target, key, value) => { target[key] = value },
    $message: { success: value => calls.success.push(value), error: value => calls.error.push(value), warning() {} },
    $root: { $emit: (...event) => calls.events.push(event) }, $router: { push() {} },
    $refs: { editFormRef: { validate: done => done(true) } }
  }
  Object.assign(user, component.data.call(user))
  for (const [name, method] of Object.entries(component.methods)) user[name] = method.bind(user)
  return { user, storage, calls }
}
const profile = path.resolve(__dirname, '../src/views/user/UserInfo.vue')
const admin = path.resolve(__dirname, '../../gouyan-ui/src/views/user/UserInfo.vue')

test('empty or repeated profile loads keep safe defaults and do not accumulate avatars', async () => {
  let picture = null
  const { user } = instance(profile, { get: async () => ({ data: { code: 200,
    data: { userId: 42, userName: 'old-user', userPicture: picture, information: '{}' } } }) })
  await user.getUser()
  assert.equal(user.pics.length, 0)
  assert.equal(user.information.hobbies.length, 0)
  assert.equal(user.information.job.length, 0)
  picture = '["/images/user/avatar.png"]'
  await user.getUser(); await user.getUser()
  assert.equal(user.pics.length, 1)
  assert.equal(user.pics[0].url, '/images/user/avatar.png')
})

test('saving an existing avatar twice keeps it and updates the renamed login token without sending credentials', async () => {
  const payloads = []
  const { user, storage, calls } = instance(profile, {
    post: async () => { throw new Error('Existing avatar must not upload') },
    put: async (url, payload) => { payloads.push(payload); return { data: { code: 200, data: { token: 'renewed-token' } } } }
  })
  user.loginUser = { userId: 42, userName: 'new-user', email: '', sex: true, password: 'must-not-send', salt: 'must-not-send' }
  user.pics = [{ url: '/images/user/avatar.png', status: 'success' }]
  await user.editUser(); await user.editUser()
  assert.equal(payloads.length, 2)
  for (const payload of payloads) {
    assert.equal(payload.userPicture, '["/images/user/avatar.png"]')
    assert.equal('password' in payload, false); assert.equal('salt' in payload, false)
  }
  assert.equal(storage.get('token'), 'renewed-token')
  assert.equal(JSON.parse(storage.get('loginUser')).userName, 'new-user')
  assert.equal('password' in JSON.parse(storage.get('loginUser')), false)
  assert.equal(calls.events.length, 2)
})

test('failed avatar upload prevents profile mutation; retry after API rejection reuses the uploaded file', async () => {
  let uploads = 0, writes = 0, uploadFails = true, saveFails = true
  const { user, calls } = instance(profile, {
    post: async () => { uploads++; return { data: uploadFails ? { code: 500, msg: 'Upload failed' } : { code: 200, data: '/images/user/new.png' } } },
    put: async () => { writes++; return { data: saveFails ? { code: 400, msg: 'Duplicate username' } : { code: 200 } } }
  })
  user.loginUser = { userId: 42, userName: 'test-user', email: '', phoneNumber: '', sex: false }
  user.pics = [{ url: 'blob:preview', raw: {} }]
  await user.editUser()
  assert.equal(writes, 0); assert.equal(user.saving, false); assert.equal(calls.error.at(-1), 'Upload failed')
  uploadFails = false
  await user.editUser()
  assert.equal(writes, 1); assert.equal(calls.error.at(-1), 'Duplicate username')
  const count = uploads
  saveFails = false
  await user.editUser()
  assert.equal(uploads, count); assert.equal(writes, 2)
  assert.equal(user.pics[0].url, '/images/user/new.png')
})

test('admin edit allows keeping password, retains the existing avatar and handles refreshed token', async () => {
  let saved
  const { user, storage } = instance(admin, {
    post: async () => { throw new Error('Existing avatar must not upload') },
    put: async (url, payload) => { saved = payload; return { data: { code: 200, data: { token: 'admin-renamed-token' } } } }
  })
  user.editForm = { userId: 42, userName: 'new-user', password: '', email: '', phoneNumber: '', roleId: 1 }
  storage.set('loginUser', JSON.stringify({ sysUser: { userId: 42, userName: 'old-user' }, cinemaId: 23 }))
  user.pics = [{ url: 'http://localhost:8181/images/user/admin.png' }]
  user.getUserList = () => {}
  await user.editUserInfo()
  assert.equal('password' in saved, false)
  assert.equal(saved.userPicture, '["/images/user/admin.png"]')
  assert.equal(storage.get('token'), 'admin-renamed-token')
  assert.equal(JSON.parse(storage.get('loginUser')).sysUser.userName, 'new-user')
  assert.equal(JSON.parse(storage.get('loginUser')).cinemaId, 23)
  assert.equal(user.savingEdit, false)
})
