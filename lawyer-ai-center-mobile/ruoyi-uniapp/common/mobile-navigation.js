const rootPath = '/pages/index/assistant'
const sections = ['consult','templates','drafting','mine']
let pending = null
export function openMobileSection(api, section = 'consult') {
  pending = { token: api.getStorageSync('token'), section: sections.includes(section) ? section : 'consult' }
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const index = pages.findIndex(page => page.route === rootPath.slice(1))
  if (index >= 0 && index < pages.length - 1) api.navigateBack({ delta: pages.length - index - 1 })
  else api.reLaunch({ url: rootPath })
}
export function openWorkspace(api, section = 'templates') { return openMobileSection(api, section === 'drafting' ? 'drafting' : 'templates') }
export function takeMobileSection(api) {
  const value = pending
  pending = null
  return value && value.token === api.getStorageSync('token') ? value.section : ''
}
