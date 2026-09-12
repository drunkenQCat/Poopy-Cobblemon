/*
 * Poketoilet Showdown 补丁无头测试
 *
 * 用整合包的 Showdown 引擎（minecraft/showdown）跑一场真实对战，
 * 注入 cobblemon_ext_patch.js，验证两条自定义协议行的引擎级效果：
 *   1. cobblemonext_boost  → 敌方速度 -1（真实 boost + -unboost 战报）
 *   2. cobblemonext_damage → 真实扣血 + 完整 split 协议（保底 1 HP）
 *
 * 用法：node headless_test.js <showdown目录> <补丁JS路径>
 */
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const showdownDir = path.resolve(process.argv[2] || '.');
const patchFile = path.resolve(process.argv[3] || '../cobblemon-ext/src/main/resources/assets/cobblemon_ext/showdown/cobblemon_ext_patch.js');

if (!fs.existsSync(showdownDir)) { console.error('showdown 目录不存在: ' + showdownDir); process.exit(2); }
if (!fs.existsSync(patchFile)) { console.error('补丁文件不存在: ' + patchFile); process.exit(2); }

process.chdir(showdownDir);

const simulator = require(path.join(showdownDir, 'sim', 'index'));

// 加载补丁：用 shim require 让补丁内的 require('./sim/index') 命中真实引擎模块
const patchSource = fs.readFileSync(patchFile, 'utf8');
(function loadPatch(require) {
  return eval(patchSource);
})(function (spec) {
  if (spec.includes('sim/index')) return simulator;
  throw new Error('headless shim: unexpected require ' + spec);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  let failures = 0;
  const check = (label, ok) => {
    console.log((ok ? '  PASS ' : '  FAIL ') + label);
    if (!ok) failures++;
  };

  const stream = new simulator.BattleStream();
  let battleLog = '';
  (async () => {
    for await (const chunk of stream) battleLog += chunk + '\n';
  })();

  // Cobblemon fork 的队伍打包格式：
  // name|species|uuid|currentHealth|status|statusDuration|item|ability|moves|movesInfo|nature|evs|gender|ivs|shiny|level|misc|
  const cobbleTeam = (name, species, ability, move, level, uuid) =>
    [name, species, uuid, '100', '', '0', '', ability, move, '15/15', 'Hardy', '', '', '', '', String(level), ''].join('|') + '|';

  const team1 = cobbleTeam('Pikachu', 'pikachu', 'static', 'thunderbolt', 50, crypto.randomUUID());
  const team2 = cobbleTeam('Charmander', 'charmander', 'blaze', 'scratch', 50, crypto.randomUUID());

  stream.write('>start {"formatid":"customgame"}');
  stream.write('>player p1 {"name":"Alice","team":"' + team1 + '"}');
  stream.write('>player p2 {"name":"Bob","team":"' + team2 + '"}');
  await sleep(150);
  stream.write('>p1 team 1');
  stream.write('>p2 team 1');
  await sleep(400);

  const battle = stream.battle;
  check('战斗对象已创建', !!battle);
  const p1 = battle.sides[0].active[0];
  const p2 = battle.sides[1].active[0];
  check('双方宝可梦已出场', !!(p1 && p2));
  if (!p1 || !p2) { console.log('battleLog:\n' + battleLog); process.exit(1); }
  console.log('  p1=' + p1.name + ' uuid=' + p1.uuid + ' hp=' + p1.hp);
  console.log('  p2=' + p2.name + ' uuid=' + p2.uuid + ' hp=' + p2.hp);

  // ---- 番泻叶：敌方速度 -1（cobblemonext_boost 协议行）----
  const speBefore = p2.boosts.spe;
  stream.write('>cobblemonext_boost {"target":"' + p2.uuid + '","stat":"spe","stages":-1}');
  await sleep(300);
  check('番泻叶：目标速度阶级 -1（' + speBefore + ' → ' + p2.boosts.spe + '）',
    p2.boosts.spe === speBefore - 1);
  check('番泻叶：战报含 -unboost', battleLog.includes('-unboost'));

  // ---- 帝王火龙果：真实扣血（cobblemonext_damage 协议行）----
  const hpBefore = p1.hp;
  const damageLogStart = battleLog.length;
  stream.write('>cobblemonext_damage {"target":"' + p1.uuid + '","amount":50}');
  await sleep(300);
  check('火龙果：扣血 50（' + hpBefore + ' → ' + p1.hp + '）', p1.hp === hpBefore - 50);
  check('火龙果：战报含 -damage', battleLog.includes('-damage'));
  const damageLog = battleLog.slice(damageLogStart);
  console.log('  实际伤害协议:\n' + damageLog.trim());
  check('火龙果：血量字段不能是对象字符串', !damageLog.includes('[object Object]'));
  const damageLines = damageLog.trim().split('\n');
  const splitIndex = damageLines.indexOf('|split|p1');
  check('火龙果：Cobblemon split 伤害包完整（私有精确HP + 公开HP）',
    splitIndex >= 0 &&
    damageLines[splitIndex + 1] === '|-damage|' + p1.toString() + '|' + p1.getHealth().secret &&
    damageLines[splitIndex + 2] === '|-damage|' + p1.toString() + '|' + p1.getHealth().shared);

  // ---- 保底 1 HP ----
  stream.write('>cobblemonext_damage {"target":"' + p2.uuid + '","amount":99999}');
  await sleep(300);
  check('火龙果：巨额伤害保底 1 HP（p2.hp=' + p2.hp + '，未倒下=' + !p2.fainted + '）',
    p2.hp === 1 && !p2.fainted);

  // ---- 再来一发番泻叶验证可叠加 ----
  stream.write('>cobblemonext_boost {"target":"' + p2.uuid + '","stat":"spe","stages":-1}');
  await sleep(300);
  check('番泻叶：可叠加至 -2', p2.boosts.spe === -2);

  console.log(failures === 0 ? '\n全部通过 ✓' : '\n存在失败项 ✗');
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => { console.error('测试执行异常:', e); process.exit(1); });
