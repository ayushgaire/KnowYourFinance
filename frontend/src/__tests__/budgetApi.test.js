import { afterEach, expect, it, vi } from 'vitest';
import { getBudgets, saveBudget, removeBudget } from '../api';
afterEach(() => vi.unstubAllGlobals());
it('uses the current user and chosen month for budget requests', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok:true, status:200, json:async () => [] });
  vi.stubGlobal('fetch', fetch);
  await getBudgets('2026-02');
  expect(fetch).toHaveBeenLastCalledWith('/api/budgets?userId=1&year=2026&month=2');
  await saveBudget('2026-02', 'DINING', '100.25');
  expect(fetch).toHaveBeenLastCalledWith('/api/budgets/DINING?userId=1&year=2026&month=2', expect.objectContaining({method:'PUT', body:JSON.stringify({limitAmount:'100.25'})}));
});
it('accepts a successful empty delete response', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:true,status:204}));
  expect(await removeBudget('2026-02','DINING')).toBeNull();
});
it('shows server-side limit validation errors', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:false,status:400,json:async () => ({ fieldErrors:{limitAmount:'Too many decimals.'} })}));
  await expect(saveBudget('2026-02','DINING','1.001')).rejects.toThrow('Too many decimals.');
});
