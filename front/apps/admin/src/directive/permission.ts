import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/** v-permission="'sys:user:add'" 或 v-permission="['a','b']"（满足其一） */
export const permission: Directive = {
    mounted(el: HTMLElement, binding) {
        const perms = useUserStore().permissions
        const need: string | string[] = binding.value
        const ok = Array.isArray(need) ? need.some((p) => perms.includes(p)) : perms.includes(need)
        if (!ok) {
            el.parentNode?.removeChild(el)
        }
    }
}