import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import PinwheelBg from './PinwheelBg.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'layout-top': () => h(PinwheelBg)
    })
  }
}
