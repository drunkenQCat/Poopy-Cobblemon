(function () {
  if (globalThis.__ptPatched) {
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
    throw new Error('poketoilet patch: cannot require sim/index — ' + requireErrors.join(' | '));
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
    if (type === 'poketoilet_senna') {
      try {
        var payload = JSON.parse(message);
        var target = findPokemon(this.battle, payload.target);
        if (target && !target.fainted) {
          var delta = target.boostBy({ spe: -1 });
          if (delta) {
            this.battle.add('-unboost', target, 'spe', String(Math.abs(delta)));
          }
        }
      } catch (e) {
        if (this.battle) {
          try { this.battle.add('debug', 'poketoilet_senna failed: ' + e); } catch (e2) { }
        }
      }
      return;
    }
    if (type === 'poketoilet_dragonfruit') {
      try {
        var payload = JSON.parse(message);
        var target = findPokemon(this.battle, payload.target);
        if (target && !target.fainted) {
          var amount = payload.amount | 0;
          if (amount > 0) {
            // 保底留 1 HP：引擎侧真实伤害，但避免开场打倒引发回合结构混乱
            var dmg = Math.min(amount, target.hp - 1);
            if (dmg > 0) {
              target.damage(dmg, target);
            }
          }
        }
      } catch (e) {
        if (this.battle) {
          try { this.battle.add('debug', 'poketoilet_dragonfruit failed: ' + e); } catch (e2) { }
        }
      }
      return;
    }
    return _writeLine.call(this, type, message);
  };

  globalThis.__ptPatched = true;
})();
