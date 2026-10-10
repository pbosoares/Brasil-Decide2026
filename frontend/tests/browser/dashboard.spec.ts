import { test, expect } from '@playwright/test';
import { simulatedManifest, simulatedSnapshot } from '../fixtures';
import waitingManifest from '../../public/results/manifest.json' with { type: 'json' };

test.beforeEach(async ({page}) => {
  await page.route('**/results/manifest.json', route => route.fulfill({json:waitingManifest}));
});

test('espera sem votos fictícios; mapa, busca e ordenação funcionam', async ({page}) => {
  const errors:string[]=[]; page.on('pageerror', e=>errors.push(e.message));
  const requests:string[]=[]; page.on('request', request=>requests.push(request.url()));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'O Brasil, voto a voto.'})).toBeVisible();
  await expect(page.getByText('Fonte ainda não conectada')).toBeVisible();
  await expect(page.getByRole('heading',{name:'Lula',exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Flavio Bolsonaro',exact:true})).toBeVisible();
  for (const name of ['Lula','Flavio Bolsonaro']) {
    const photo = page.getByRole('img',{name:`Foto de ${name}`});
    await expect(photo).toBeVisible();
    await expect.poll(() => photo.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  }
  await expect(page.getByRole('button',{name:/^Selecionar /})).toHaveCount(27);
  await expect(page.getByText('27 de 27 unidades da federação')).toBeVisible();
  await page.getByRole('button',{name:'Selecionar Bahia',exact:true}).click();
  await expect(page.getByRole('button',{name:'Selecionar Bahia',exact:true})).toHaveCSS('outline-style','none');
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


test('cores seguem Lula e Flavio mesmo com ordem invertida; empate permanece neutro', async ({page}) => {
  const snapshot = simulatedSnapshot();
  for (const result of [snapshot.nacional.resultado, ...Object.values(snapshot.estados).map(state => state.resultado)]) {
    result.candidatos[0].id = '280002542548';
    result.candidatos[1].id = '280002551544';
    result.candidatos[0].numero = '13';
    result.candidatos[1].numero = '22';
    result.liderId = result.candidatos[0].id;
    // Reverse source order to ensure the color is determined by identity.
    result.candidatos.reverse();
  }
  const blue = snapshot.estados.SP.resultado;
  blue.candidatos.find(c => c.id === '280002551544')!.votos = 60;
  blue.candidatos.find(c => c.id === '280002542548')!.votos = 40;
  blue.candidatos.forEach(c => c.percentualOficial = c.votos);
  blue.liderId = '280002551544';
  const tie = snapshot.estados.AC.resultado;
  tie.candidatos.forEach(c => { c.votos = 50; c.percentualOficial = 50; });
  tie.liderId = null; tie.diferencaVotos = 0;
  await page.route('**/results/manifest.json', route => route.fulfill({json:simulatedManifest()}));
  await page.route('**/results/snapshots/test-version.json', route => route.fulfill({json:snapshot}));
  await page.goto('/');
  await expect(page.getByRole('button',{name:'Selecionar Bahia',exact:true})).toHaveAttribute('fill','#e45359');
  await expect(page.getByRole('button',{name:'Selecionar São Paulo',exact:true})).toHaveAttribute('fill','#4c8de8');
  await expect(page.getByRole('button',{name:'Selecionar Acre',exact:true})).toHaveAttribute('fill','#263341');
  await page.getByRole('button',{name:'Selecionar São Paulo',exact:true}).focus();
  await expect(page.getByRole('button',{name:'Selecionar São Paulo',exact:true})).toHaveCSS('outline-style','none');
});


test('atualiza automaticamente e recupera de timeout sem perder o placar', async ({page}) => {
  await page.clock.install();
  let mode: 'first' | 'hang' | 'next' = 'first';
  let calls = 0;
  const first = simulatedSnapshot();
  const next = simulatedSnapshot('test-recovered');
  next.coletadoEm = new Date(Date.now()+1000).toISOString();
  await page.route('**/results/manifest.json', route => {
    calls++;
    if (mode === 'hang') return new Promise<void>(() => {});
    return route.fulfill({json:simulatedManifest(mode === 'next' ? 'test-recovered' : 'test-version')});
  });
  await page.route('**/results/snapshots/test-version.json', route => route.fulfill({json:first}));
  await page.route('**/results/snapshots/test-recovered.json', route => route.fulfill({json:next}));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  const initialCalls = calls;
  mode = 'hang';
  await page.clock.runFor(6_100);
  expect(calls).toBe(initialCalls + 1);
  await page.clock.runFor(12_100);
  await expect(page.getByText('Exibindo o último resultado válido')).toBeVisible();
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  // The UI remains usable while the source is unavailable.
  await page.getByLabel('Selecionar estado').selectOption('BA');
  await expect(page.getByRole('heading',{name:'Bahia',exact:true})).toBeVisible();
  mode = 'next';
  await page.clock.runFor(3_100);
  await expect(page.getByText('Exibindo o último resultado válido')).toHaveCount(0);
  expect(calls).toBe(initialCalls + 2);
});

test('rejeita uma UF antiga mesmo que o horário geral de coleta seja mais novo', async ({page}) => {
  let regress = false;
  const first = simulatedSnapshot(); const bad = simulatedSnapshot('test-regression');
  bad.coletadoEm = new Date(Date.now()+1000).toISOString();
  bad.estados.SP.resultado.atualizadoEm = '2026-01-01T00:00:00Z';
  await page.route('**/results/manifest.json', route => route.fulfill({json:simulatedManifest(regress ? 'test-regression' : 'test-version')}));
  await page.route('**/results/snapshots/test-version.json', route => route.fulfill({json:first}));
  await page.route('**/results/snapshots/test-regression.json', route => route.fulfill({json:bad}));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'SIMULAÇÃO Candidatura A',exact:true})).toBeVisible();
  regress = true;
  await page.getByRole('button',{name:'Atualizar resultados'}).click();
  await expect(page.getByText('Exibindo o último resultado válido')).toBeVisible();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('brasil-decide:2026:presidente:t2:v1')!).versao)).toBe('test-version');
});
