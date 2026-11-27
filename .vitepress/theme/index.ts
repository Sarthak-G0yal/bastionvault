import { h } from 'vue'
import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'

import HeroLogo from './components/HeroLogo.vue'
import HeroCommand from './components/HeroCommand.vue'
import HeroPreview from './components/HeroPreview.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      'home-hero-info-before': () => h(HeroLogo),
      'home-hero-actions-after': () => h(HeroCommand),
      'home-hero-after': () => h(HeroPreview),
    }),
} satisfies Theme
