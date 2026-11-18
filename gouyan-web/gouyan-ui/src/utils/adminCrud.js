const pendingOperations = new WeakMap()

export function validateForm(form) {
  if (!form) return Promise.reject(new Error('表单尚未加载，请稍后再试'))
  return new Promise(resolve => form.validate(valid => resolve(Boolean(valid))))
}

export async function checkedRequest(request) {
  const response = await request
  if (!response || !response.data || Number(response.data.code) !== 200) {
    throw new Error(response && response.data && response.data.msg || '操作失败，请稍后重试')
  }
  return response
}

export async function runMutation(view, key, operation, refreshOnFailure) {
  let pending = pendingOperations.get(view)
  if (!pending) { pending = new Set(); pendingOperations.set(view, pending) }
  if (pending.has(key)) return
  pending.add(key)
  try {
    return await operation()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !(error && error.reported)) {
      view.$message.error(error && error.message || '操作未完成，请检查网络后重试')
    }
    if (refreshOnFailure) {
      try { await refreshOnFailure() } catch (refreshError) { /* Request interceptor already reports connection failures. */ }
    }
  } finally { pending.delete(key) }
}

export async function runRead(view, operation) {
  try { return await operation() }
  catch (error) {
    if (!(error && error.reported)) view.$message.error(error && error.message || '加载数据失败，请稍后重试')
  }
}

export function pictureArray(value) {
  try {
    const list = Array.isArray(value) ? value : JSON.parse(value || '[]')
    return Array.isArray(list) ? list.filter(item => typeof item === 'string' && item) : []
  } catch (error) { return [] }
}

export async function uploadPictures(view, files, type) {
  const pictures = []
  for (const file of files) {
    if (file.uploadedUrl) { pictures.push(file.uploadedUrl); continue }
    if (!file.raw) {
      const index = (file.url || '').indexOf('/images/')
      if (index < 0) throw new Error('图片地址无效，请重新选择图片')
      pictures.push(file.url.substring(index))
      continue
    }
    const form = new FormData()
    form.append('file', file.raw)
    const { data } = await checkedRequest(axios.post('upload/' + type, form))
    if (typeof data.data !== 'string' || !data.data.startsWith('/images/')) throw new Error('图片上传失败，请重试')
    view.$set(file, 'uploadedUrl', data.data)
    pictures.push(data.data)
  }
  return pictures
}
