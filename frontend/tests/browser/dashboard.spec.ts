import { test, expect } from '@playwright/test';
import { simulatedManifest, simulatedSnapshot } from '../fixtures';

test('espera sem votos fictícios; mapa, busca e ordenação funcionam', async ({page}) => {
  const errors:string[]=[]; page.on('pageerror', e=>errors.push(e.message));
  const requests:string[]=[]; page.on('request', request=>requests.push(request.url()));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'O Brasil, voto a voto.'})).toBeVisible();
  await expect(page.getByText('Fonte ainda não conectada')).toBeVisible();
  await expect(page.getByRole('button',{name:/^Selecionar /})).toHaveCount(27);
  await expect(page.getByText('27 de 27 unidades da federação')).toBeVisible();
  await page.getByRole('button',{name:'Selecionar Bahia',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Bahia',exact:true})).toBeVisible();
  await page.getByLabel('Selecionar estado').selectOption('AC');
  await expect(page.getByRole('heading',{name:'Acre',exact:true})).toBeVisible();
  await page.getByLabel('Buscar estado ou UF').fill('sao paulo');
  await expect(page.getByText('1 de 27 unidades da federação')).toBeVisible();
  await page.getByLabel('Buscar estado ou UF').fill('naoexiste');
  await expect(page.getByText('Nenhum estado encontrado.',{exact:false})).toBeVisible();
  await page.getByLabel('Buscar estado ou UF').clear();
  await page.getByRole('columnheader',{name:'Estado'}).getByRole('button').click();
  await expect(page.locator('tbody tr').first()).toContainText('Tocantins');
  expect(requests.some(url=>url.includes(':8080') || url.includes('resultados.tse.jus.br'))).toBe(false);
  expect(errors).toEqual([]);
  await page.screenshot({path:'test-results/dashboard-desktop.png',fullPage:true});
});

test('responsivo em 390px e acessível por teclado', async ({page}) => {
  await page.setViewportSize({width:390,height:844});
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Presidência da República'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const bahia=page.getByRole('button',{name:'Selecionar Bahia',exact:true});
  await bahia.focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('heading',{name:'Bahia',exact:true})).toBeVisible();
  await page.screenshot({path:'test-results/dashboard-mobile.png',fullPage:true});
});

test('carrega snapshot, mantém dados válidos após erro e avisa atraso', async ({page}) => {
  // SIMULAÇÃO isolada via interceptação do navegador, nunca servida ou incluída no build.
  let broken = false;
  await page.route('**/results/manifest.json',route => route.fulfill({json:broken ? {...simulatedManifest(),versao:'bad-version',arquivo:'snapshots/bad-version.json',status:{...simulatedManifest().status,versao:'bad-version'}} : simulatedManifest()}));
  await page.route('**/results/snapshots/test-version.json',route => route.fulfill({json:simulatedSnapshot()}));
  await page.route('**/results/snapshots/bad-version.json',route => route.fulfill({json:{nao:'um snapshot válido'}}));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  await expect(page.getByText('Apuração em andamento',{exact:true})).toBeVisible();
  await expect(page.getByText('60,00%',{exact:true}).first()).toBeVisible();
  broken=true; await page.getByRole('button',{name:'Atualizar resultados'}).click();
  await expect(page.getByText('Exibindo o último resultado válido')).toBeVisible();
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  await expect(page.getByText('Exibindo o último resultado válido')).toBeVisible();
});

test('layout sem overflow em celular compacto, tablet e desktop', async ({page}) => {
  for (const width of [320,768,1440]) {
    await page.setViewportSize({width,height:900}); await page.goto('/');
    await expect(page.getByRole('heading',{name:'Presidência da República'})).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
});

test('histórico apresenta apenas atualizações recebidas', async ({page}) => {
  let second = false;
  const first = simulatedSnapshot(); const next = simulatedSnapshot('test-second');
  next.coletadoEm = new Date(Date.now()+1000).toISOString();
  for (const state of Object.values(next.estados)) state.resultado.atualizadoEm=next.coletadoEm;
  await page.route('**/results/manifest.json',route=>route.fulfill({json:simulatedManifest(second?'test-second':'test-version')}));
  await page.route('**/results/snapshots/test-version.json',route=>route.fulfill({json:first}));
  await page.route('**/results/snapshots/test-second.json',route=>route.fulfill({json:next}));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  await expect(page.getByText('O histórico aparecerá após duas atualizações oficiais nesta sessão.')).toBeVisible();
  second=true; await page.getByRole('button',{name:'Atualizar resultados'}).click();
  await expect(page.locator('.history-chart svg')).toBeVisible();
  await expect(page.getByText('Atualizações recebidas nesta sessão; não inclui períodos anteriores.')).toBeVisible();
});
