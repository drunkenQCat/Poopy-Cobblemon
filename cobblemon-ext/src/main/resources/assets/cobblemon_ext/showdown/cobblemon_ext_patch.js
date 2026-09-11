(function () {
  if (globalThis.__cobblemonExtPatched) {
    return;
  }

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
    throw new Error('cobblemon-ext patch: cannot require sim/index — ' + requireErrors.join(' | '));
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
    if (type === 'cobblemonext_boost') {
      try {
        var payload = JSON.parse(message);
        var target = findPokemon(this.battle, payload.target);
        if (target && !target.fainted) {
          var changes = {};
          changes[payload.stat] = payload.stages;
          var delta = target.boostBy(changes);
          if (delta) {
            this.battle.add(payload.stages > 0 ? '-boost' : '-unboost',
              target, payload.stat, String(Math.abs(delta)));
          }
        }
      } catch (e) {
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
        if (target && !target.fainted) {
          var amount = payload.amount | 0;
          if (amount > 0) {
            // 保底留 1 HP：真实伤害但不直接打倒，避免开场倒下引发回合结构混乱
            var dmg = Math.min(amount, target.hp - 1);
            if (dmg > 0) {
              target.damage(dmg, target);
            }
          }
        }
      } catch (e) {
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
