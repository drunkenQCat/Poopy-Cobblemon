(function () {
  if (globalThis.__cobblemonExtPatched) {
    return;
  }

  // 自诊断状态：Java 侧可通过 bindings 回读并写入日志
  globalThis.__cobblemonExtStatus = {
    patched: true,
    requireError: '',
    linesSeen: 0,
    linesByType: {},
    applied: [],
    lastError: ''
  };
  var status = globalThis.__cobblemonExtStatus;

  var sim = null;
  var requireErrors = [];
  var candidates = ['./sim/index', 'sim/index', './sim/index.js', './showdown/sim/index'];
  for (var ci = 0; ci < candidates.length; ci++) {
    try {
      sim = require(candidates[ci]);
      if (sim && sim.Battle) break;
    } catch (e) {
      requireErrors.push(candidates[ci] + ': ' + e);
    }
  }
  if (!sim || !sim.Battle) {
    status.requireError = requireErrors.join(' | ');
    throw new Error('cobblemon-ext patch: cannot require sim/index — ' + status.requireError);
  }

  var BattleStream = sim.BattleStream;

  function findPokemon(battle, uuid) {
    var sides = battle && battle.sides ? battle.sides : [];
    for (var i = 0; i < sides.length; i++) {
      var side = sides[i];
      if (!side) continue;
      var act = side.active || [];
      for (var j = 0; j < act.length; j++) {
        var p = act[j];
        if (p && p.uuid === uuid) return p;
      }
    }
    return null;
  }

  var _writeLine = BattleStream.prototype._writeLine;
  BattleStream.prototype._writeLine = function (type, message) {
    try {
      status.linesSeen++;
      status.linesByType[type] = (status.linesByType[type] || 0) + 1;
    } catch (e) { }

    if (type === 'cobblemonext_boost') {
      try {
        var payload = JSON.parse(message);
        var target = findPokemon(this.battle, payload.target);
        var record = {
          kind: 'boost', found: !!target, stat: payload.stat, stages: payload.stages,
          before: target ? (target.boosts ? target.boosts[payload.stat] : null) : null,
          after: null
        };
        if (target && !target.fainted) {
          var changes = {};
          changes[payload.stat] = payload.stages;
          var delta = target.boostBy(changes);
          if (delta) {
            this.battle.add(payload.stages > 0 ? '-boost' : '-unboost',
              target, payload.stat, String(Math.abs(delta)));
          }
          record.after = target.boosts ? target.boosts[payload.stat] : null;
        } else {
          record.lastError = 'target not found / fainted';
        }
        record.applied = true;
        status.applied.push(record);
        if (status.applied.length > 8) status.applied.shift();
      } catch (e) {
        status.lastError = 'boost: ' + e;
        if (this.battle) {
          try { this.battle.add('debug', 'cobblemonext_boost failed: ' + e); } catch (e2) { }
        }
      }
      return;
    }
    if (type === 'cobblemonext_damage') {
      try {
        var payload = JSON.parse(message);
        var target = findPokemon(this.battle, payload.target);
        var record = {
          kind: 'damage', found: !!target, amount: payload.amount | 0,
          hpBefore: target ? target.hp : null, hpAfter: null
        };
        if (target && !target.fainted) {
          var amount = payload.amount | 0;
          if (amount > 0) {
            // 保底留 1 HP：真实伤害但不直接打倒，避免开场倒下引发回合结构混乱
            var dmg = Math.min(amount, target.hp - 1);
            if (dmg > 0) {
              target.damage(dmg, target);
              // 引擎的 damage() 不发协议消息，必须手动补 -damage，
              // Cobblemon 的 DamageInstruction 才会同步血条与持久化数据
              this.battle.add('-damage', target, target.getHealth());
            }
          }
          record.hpAfter = target.hp;
        } else {
          record.lastError = 'target not found / fainted';
        }
        status.applied.push(record);
        if (status.applied.length > 8) status.applied.shift();
      } catch (e) {
        status.lastError = 'damage: ' + e;
        if (this.battle) {
          try { this.battle.add('debug', 'cobblemonext_damage failed: ' + e); } catch (e2) { }
        }
      }
      return;
    }
    return _writeLine.call(this, type, message);
  };

  globalThis.__cobblemonExtPatched = true;
})();
