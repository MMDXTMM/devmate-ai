import { inject, type InjectionKey } from 'vue'

export type ModelAccess = (purpose: string) => Promise<boolean>
export const modelAccessKey: InjectionKey<ModelAccess> = Symbol('modelAccess')
// The application supplies the account check and shared configuration dialog.
export function useModelAccess(): ModelAccess {
  return inject(modelAccessKey, async () => true)
}
