/* Run from dev: node showdowntest/bridge_regression_test.js
 * Uses the instance's real index.js startBattle/sendBattleMessage callback path.
 * This checks the wire contract consumed by Cobblemon, not a Minecraft UI mock.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createRequire} = require('node:module');
const {randomUUID} = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const showdown = path.join(root, 'minecraft/showdown');
const patch = process.argv[2] ? path.resolve(process.argv[2]) : path.join(root,
  'dev/cobblemon-ext/src/main/resources/assets/cobblemon_ext/showdown/cobblemon_ext_patch.js');
const context = vm.createContext({require: createRequire(path.join(showdown, 'index.js')), console});
vm.runInContext(fs.readFileSync(path.join(showdown, 'index.js'), 'utf8'), context);
vm.runInContext(fs.readFileSync(patch, 'utf8'), context);
const flush = () => new Promise(resolve => setImmediate(resolve));
const output = [];
const id = randomUUID();
const callback = {
  sendFromShowdown(battleId, message) { assert.equal(battleId, id); output.push(message); },
  log(message) { throw new Error(message); },
};
const team = (species, uuid) => [species, species, uuid, '100', '', '0', '',
  'runaway', 'splash', '40/40', 'Hardy', '', '', '', '', '50', ''].join('|') + '|';
let checks = 0;
function check(name, run) { run(); checks++; console.log('PASS ' + name); }
async function send(lines) {
  output.length = 0;
  context.sendBattleMessage(id, Array.isArray(lines) ? lines : [lines]);
  await flush();
  return output.join('\n');
}
function damageCommand(pokemon, amount) {
  return '>cobblemonext_damage ' + JSON.stringify({target: pokemon.uuid, amount});
}
function boostCommand(pokemon, stages) {
  return '>cobblemonext_boost ' + JSON.stringify({target: pokemon.uuid, stat: 'spe', stages});
}
function assertDamagePacket(log, pokemon) {
  const lines = log.split('\n');
  const i = lines.indexOf('|split|' + pokemon.side.id);
  assert.ok(i >= 0, 'Missing split header: ' + log);
  const health = pokemon.getHealth();
  assert.equal(lines[i + 1], '|-damage|' + pokemon + '|' + health.secret);
  assert.equal(lines[i + 2], '|-damage|' + pokemon + '|' + health.shared);
  assert.match(health.secret, /^\d+\/\d+(?: [a-z]+)?$/);
  assert.ok(!log.includes('[object Object]'));
}
async function main() {
  context.startBattle(callback, id, [
    '>start {"formatid":"gen9customgame","seed":[1,2,3,4]}',
    '>player p1 ' + JSON.stringify({name:'A', team:team('pikachu', randomUUID()) + ']' + team('eevee', randomUUID())}),
    '>player p2 ' + JSON.stringify({name:'B', team:team('jolteon', randomUUID())}),
  ]);
  await flush();
  await send(['>p1 team 12', '>p2 team 1']);
  const stream = vm.runInContext('battleMap.get(' + JSON.stringify(id) + ')', context);
  const battle = stream.battle;
  const p1 = battle.p1.active[0], p2 = battle.p2.active[0], bench = battle.p1.pokemon[1];
  check('fixture HP within max HP', () => {
    assert.ok(p1.hp <= p1.maxhp && p2.hp <= p2.maxhp);
  });
  let log = await send(damageCommand(p1, 50));
  check('entry bridge delivers exact 100 -> 50 HP split packet before next move', () => {
    assert.equal(p1.hp, 50); assertDamagePacket(log, p1);
  });
  battle.reportExactHP = false;
  p2.status = 'par';
  log = await send(damageCommand(p2, 13));
  check('p2 split preserves exact/public HP and status suffix', () => {
    assert.equal(p2.hp, 87); assertDamagePacket(log, p2);
    assert.notEqual(p2.getHealth().secret, p2.getHealth().shared);
    assert.match(p2.getHealth().secret, / par$/);
  });
  p2.status = '';
  check('opponent is initially faster', () => assert.ok(p2.getActionSpeed() > p1.getActionSpeed()));
  log = await send(boostCommand(p2, -1));
  check('speed drop changes engine speed and emits exact delta', () => {
    assert.equal(p2.boosts.spe, -1);
    assert.ok(p2.getActionSpeed() < p1.getActionSpeed());
    assert.ok(log.includes('|-unboost|' + p2 + '|spe|1'));
  });
  log = await send(['>p1 move 1', '>p2 move 1']);
  check('next turn actually uses changed move order; damage does not rebound', () => {
    const moves = log.split('\n').filter(line => line.startsWith('|move|'));
    assert.ok(moves[0].startsWith('|move|' + p1 + '|'), log);
    assert.ok(moves[1].startsWith('|move|' + p2 + '|'), log);
    assert.equal(p1.hp, 50); assert.equal(p2.hp, 87);
  });
  await send(boostCommand(p2, -20));
  log = await send(boostCommand(p2, -1));
  check('speed clamps at -6 and emits no phantom seventh drop', () => {
    assert.equal(p2.boosts.spe, -6); assert.ok(!log.includes('|-unboost|'));
  });
  log = await send(damageCommand(p2, 99999));
  check('large damage leaves 1 HP without faint packet', () => {
    assert.equal(p2.hp, 1); assert.equal(p2.fainted, false);
    assertDamagePacket(log, p2); assert.ok(!log.includes('|faint|'));
  });
  log = await send([damageCommand(p2, 1), damageCommand(p1, 0), damageCommand(p1, -5),
    damageCommand({uuid:randomUUID()}, 10), damageCommand(bench, 10)]);
  check('1 HP, nonpositive, missing UUID and bench targets do not emit fake damage', () => {
    assert.equal(p2.hp, 1); assert.equal(p1.hp, 50); assert.equal(bench.hp, 100);
    assert.ok(!log.includes('|-damage|'));
  });
  await send(['>p1 switch 2', '>p2 move 1']);
  log = await send(damageCommand(p1, 10));
  check('switched-out UUID cannot be damaged', () => {
    assert.equal(p1.hp, 50); assert.ok(!log.includes('|-damage|'));
  });
  await send(['>p1 switch 2', '>p2 move 1']);
  log = await send(damageCommand(p1, 10));
  check('returning active UUID receives a new valid damage packet', () => {
    assert.equal(battle.p1.active[0], p1); assert.equal(p1.hp, 40); assertDamagePacket(log, p1);
  });
  context.endBattle(id);
  await flush();
  console.log('\n' + checks + ' bridge regression checks passed. Minecraft UI still requires live verification.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
