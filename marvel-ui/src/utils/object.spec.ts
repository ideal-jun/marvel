import { describe, expect, it } from 'vitest'
import { clearObject } from './object'

describe('utils/object.clearObject', () => {
  it('清空对象的全部自有可枚举字段', () => {
    const form: Record<string, unknown> = { a: 1, b: 'x', c: true }
    clearObject(form)
    expect(Object.keys(form)).toHaveLength(0)
    expect(form).toEqual({})
  })

  it('保留对象引用不变', () => {
    const form: Record<string, unknown> = { a: 1 }
    const ref = form
    clearObject(form)
    expect(ref).toBe(form)
    expect(Object.keys(ref)).toHaveLength(0)
  })
})
