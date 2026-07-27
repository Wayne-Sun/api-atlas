/**
 * Tree-shaking optimized naive-ui plugin.
 *
 * Strategy: use `create()` with an explicit component list so Vite/Rollup
 * only bundles the components listed here — NOT the entire naive-ui library.
 * Keep this list in sync with actual usage; remove entries when components
 * are no longer referenced in any template.
 *
 * NOTE: Most .vue files also import naive-ui components locally for
 * direct usage. The global registration here serves as a fallback and
 * ensures these components are available without explicit imports.
 */
import { create, NButton, NCard, NConfigProvider, NDataTable, NEmpty, NForm, NFormItem, NIcon, NInput, NInputNumber, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, NPopconfirm, NSelect, NSpace, NSwitch, NTag, NText } from 'naive-ui'

const naiveUiPlugin = create({
  components: [NButton, NCard, NConfigProvider, NDataTable, NEmpty, NForm, NFormItem, NIcon, NInput, NInputNumber, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, NPopconfirm, NSelect, NSpace, NSwitch, NTag, NText],
})

export default naiveUiPlugin
