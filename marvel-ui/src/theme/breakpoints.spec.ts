import { describe, expect, it } from 'vitest'
import { forUnoCSS, forVuetify } from './breakpoints'

describe('theme/breakpoints', () => {
  it('对外暴露 Vuetify 断点阈值', () => {
    expect(forVuetify).toMatchObject({
      xs: 0,
      sm: 600,
      md: 960,
      lg: 1280,
      xl: 1920,
      xxl: 2560,
    })
  })

  it('为 UnoCSS 输出同值的 px 字符串', () => {
    expect(forUnoCSS).toEqual({
      xs: '0px',
      sm: '600px',
      md: '960px',
      lg: '1280px',
      xl: '1920px',
      xxl: '2560px',
    })
  })

  it('两侧断点键集合保持一致', () => {
    expect(Object.keys(forUnoCSS).sort()).toEqual(Object.keys(forVuetify).sort())
  })
})
