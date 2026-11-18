const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const compiler = require('vue-template-compiler')
const babel = require('@babel/core')
const sourceRoot = path.resolve(__dirname, '../src')

const fixture = {
  id: 42, userId: 42, roleId: 42, actorId: 42, actorRoleId: 42, cinemaId: 42, cinemaAreaId: 42,
  cinemaBrandId: 42, hallId: 1, hallCategoryId: 42, movieId: 42, movieAgeId: 42,
  movieAreaId: 42, movieCategoryId: 42, movieRuntimeId: 42, sessionId: 42, billId: 42,
  userHobbyId: 42, userName: 'fixture-user', actorName: 'fixture-actor', movieNameCn: 'fixture-movie',
  movieNameEn: 'fixture-movie', name: 'fixture-menu', roleName: 'fixture-role', roleDesc: 'fixture-role',
  rowNums: 3, seatNumsRow: 3, rowStart: '1', seatNums: 9, hallState: true, sessionState: true,
  sessionPrice: 35, sessionSeats: '{"1":[3,0,0],"2":[0,0,0],"3":[0,0,0]}',
  seatState: '{"1":[0,1,0],"2":[0,0,0],"3":[0,0,0]}',
  commentTime: '2026-11-18 12:34:56', score: 4.5, parentId: 0, level: 1,
  languageVersion: 'fixture-language', sessionDate: '2026-11-18', userPicture: '[]',
  actorPhoto: '[]', cinemaPicture: '[]', moviePictures: '[]', moviePoster: '[]'
}

function create(file, options = {}) {
  const calls = { writes: [], reads: [], confirm: 0, success: [], error: [], warning: [] }
  const session = new Map([['btnPermission', 'admin'], ['loginUser', JSON.stringify({ sysUser: { userId: 1 }, cinemaId: null })]])
  const api = { defaults: { headers: { post: {}, put: {} } } }
  for (const verb of ['post', 'put', 'delete']) api[verb] = async (...args) => {
    calls.writes.push({ verb, args })
    return { data: options.fail ? { code: 500, msg: 'mutation-rejected' } : { code: 200, data: '/images/test.png' } }
  }
  api.get = async (url, config) => {
    calls.reads.push(url)
    const single = /^\/?(?:sysHall|sysSession|sysUser)\//.test(url)
    return { data: { code: 200, data: single ? { ...fixture } : [], total: 0, pageNum: 1, pageSize: 7 } }
  }
  const cache = new Map()
  const globals = { axios: api, process: { env: {} }, window: { sessionStorage: { getItem: key => session.get(key), setItem: (key, value) => session.set(key, value) } },
    console: { log() {}, error() {}, warn() {} }, FormData: class { append() {} }, setTimeout, clearTimeout }
  function load(filename) {
    if (cache.has(filename)) return cache.get(filename)
    const content = fs.readFileSync(filename, 'utf8')
    const script = filename.endsWith('.vue') ? compiler.parseComponent(content).script.content : content
    const code = babel.transformSync(script, { babelrc: false, configFile: false,
      plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    const requireModule = name => {
      if (name.endsWith('.css')) return {}
      if (name.startsWith('@/')) return load(path.join(sourceRoot, name.substring(2)) + '.js')
      return require(name)
    }
    vm.runInNewContext(code, { ...globals, module, exports: module.exports, require: requireModule }, { filename })
    cache.set(filename, module.exports)
    return module.exports
  }
  const component = load(file).default
  const view = {
    $root: { $emit() {} },
    global: { base: 'http://localhost:8181' },
    $message: { success: m => calls.success.push(m), error: m => calls.error.push(m), info() {}, warning: m => calls.warning.push(m) },
    $confirm: async () => { calls.confirm++; if (options.confirm !== undefined) throw options.confirm; return 'confirm' },
    $set: (object, key, value) => { object[key] = value },
    $refs: new Proxy({}, { get: () => ({ validate: done => done(options.valid !== false), resetFields() {}, clearFiles() {}, getCheckedKeys: () => [], getHalfCheckedKeys: () => [] }) })
  }
  Object.assign(view, component.data.call(view))
  for (const [name, method] of Object.entries(component.methods)) view[name] = method.bind(view)
  view.multipleSelection = [{ ...fixture }]
  view.editForm = { ...fixture }
  view.addForm = { ...view.addForm, ...fixture }
  view.addDialogVisible = true
  view.editDialogVisible = true
  return { view, calls, component, api, session }
}
function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(path.join(dir, entry.name)) : entry.name.endsWith('.vue') ? [path.join(dir, entry.name)] : [])
}
const pages = files(path.join(sourceRoot, 'views')).filter(file => /async multipleDelete\(/.test(fs.readFileSync(file, 'utf8')))

for (const file of pages) {
  const name = path.relative(sourceRoot, file)
  test(name + ': cancel and close cannot delete; empty selection cannot send a request', async () => {
    for (const confirm of ['cancel', 'close']) {
      const { view, calls } = create(file, { confirm })
      await view.multipleDelete()
      assert.equal(calls.writes.length, 0); assert.equal(calls.success.length, 0)
    }
    const { view, calls } = create(file)
    view.multipleSelection = []
    await view.multipleDelete()
    assert.equal(calls.confirm, 0); assert.equal(calls.writes.length, 0)
  })
  test(name + ': rejected deletion never reports success', async () => {
    const { view, calls } = create(file, { fail: true })
    await view.multipleDelete()
    assert.equal(calls.writes.length, 1)
    assert.equal(calls.success.length, 0)
    assert.ok(calls.error.some(message => message.includes('mutation-rejected')))
  })
  const parsed = create(file).component
  for (const [method, fn] of Object.entries(parsed.methods)) {
    if (!/^(?:add|edit)/.test(method) || !/axios\.(?:post|put)/.test(fn.toString()) || method === 'addCategory' || method === 'addActor' && file.endsWith('MovieInfo.vue')) continue
    test(name + ': ' + method + ' failure preserves the form and reports the API reason', async () => {
      const { view, calls } = create(file, { fail: true })
      await view[method]()
      assert.equal(calls.success.length, 0)
      assert.ok(calls.error.some(message => message.includes('mutation-rejected')), calls.error.join(';'))
    })
  }
}

test('system administrator can manage halls and sessions from other cinemas', () => {
  for (const file of ['hall/HallInfo.vue', 'session/SessionInfo.vue']) {
    assert.equal(create(path.join(sourceRoot, 'views', file)).view.isCinemaOwner(99), true)
  }
})
test('session metadata edits preserve persisted seat state', async () => {
  const { view, calls } = create(path.join(sourceRoot, 'views/session/SessionInfo.vue'))
  view.editForm.sessionSeats = '{}'
  await view.editSessionInfo()
  const payload = calls.writes.find(call => call.verb === 'put').args[1]
  assert.equal(payload.sessionSeats, fixture.sessionSeats)
  assert.equal(payload.cinemaId, fixture.cinemaId)
})
test('bill toggle sends the payment API envelope', async () => {
  const { view, calls } = create(path.join(sourceRoot, 'views/user/BillInfo.vue'))
  await view.billStateChanged({ billId: 42, billState: true })
  const data = JSON.parse(calls.writes[0].args[1])
  assert.equal(data.sysBill.billId, 42)
})
