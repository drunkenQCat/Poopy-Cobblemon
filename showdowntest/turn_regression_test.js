/* Run from dev: node showdowntest/turn_regression_test.js [JDK home]
 * Compile the production scheduling methods and ExtEvents verbatim against small
 * game-object stubs. Effects are recorded, not simulated: this tests Java timing,
 * retention and cancellation, while bridge_regression_test.js tests real damage.
 * Live Minecraft is still needed to verify Mixin injection and the display queue.
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'build', 'turn-regression');
fs.mkdirSync(output, {recursive: true});
const source = fs.readFileSync(path.join(root, 'src/main/java/com/poopycobblemon/battle/HeldItemBattleEffects.java'), 'utf8');
function declaration(marker) {
  const start = source.indexOf(marker);
  if (start < 0) throw new Error('Missing production declaration: ' + marker);
  const open = source.indexOf('{', start);
  let depth = 1, end = open + 1;
  while (depth && end < source.length) {
    const char = source[end++];
    if (char === '{') depth++;
    if (char === '}') depth--;
  }
  if (depth) throw new Error('Unclosed declaration: ' + marker);
  return source.slice(start, end);
}
const production = [
  'private static final class PendingDragonFruit',
  'private static boolean isHolding(',
  'private static boolean hasTriggeredThisBattle(',
  'private static boolean isFireTypeMaxLevel(',
  'private static void onActivePokemonChanged(',
  'private static void onBattleEnded(',
  'private static void onBattleTurn(',
  'private static void onServerTick(',
].map(declaration).join('\n');
const events = fs.readFileSync(path.join(root, 'cobblemon-ext/src/main/java/com/poopycobblemon/cobblemonext/ExtEvents.java'), 'utf8')
  .replace(/^package .*;\r?\n/m, '').replace(/^import .*;\r?\n/gm, '')
  .replace('public final class ExtEvents', 'static final class ExtEvents');
const java = `
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
public class TurnRegression {
  static final Map<ActiveBattlePokemon, PendingDragonFruit> IMMEDIATE = new IdentityHashMap<>();
  static final Map<UUID, List<PendingDragonFruit>> TURN_WAITING = new ConcurrentHashMap<>();
  static final Map<UUID, Set<UUID>> TRIGGERED = new ConcurrentHashMap<>();
  static final Log LOGGER = new Log();
  static class Log { void debug(String s,Object... args){} void warn(String s,Object... args){} void error(String s,Object... args){} }
  static class CobblemonExt { static final Log LOGGER = new Log(); }
  static class Cobblemon { static final Cobblemon INSTANCE=new Cobblemon(); int max=100; Cobblemon getConfig(){return this;} int getMaxPokemonLevel(){return max;} }
  static class Item {}
  static class ItemRef { Item item=new Item(); Item get(){return item;} }
  static class PoItems { static final ItemRef KING_OF_DRAGON_FRUIT=new ItemRef(); }
  static class ItemStack { boolean held=true; boolean isEmpty(){return !held;} boolean is(Item i){return held;} }
  static class Type { String name; Type(String n){name=n;} String getName(){return name;} }
  static class Pokemon {
    UUID id=UUID.randomUUID(); ItemStack item=new ItemStack(); int level=50; List<Type> types=List.of(new Type("normal"));
    ItemStack heldItem(){return item;} UUID getUuid(){return id;} int getLevel(){return level;} List<Type> getTypes(){return types;}
  }
  static class Entity { int beam; int getBeamMode(){return beam;} }
  static class BattlePokemon { Pokemon pokemon=new Pokemon(); Entity entity=new Entity(); Pokemon getEffectedPokemon(){return pokemon;} Entity getEntity(){return entity;} }
  static class MoveTemplate {}
  static class ActiveBattlePokemon {
    PokemonBattle battle; BattlePokemon self=new BattlePokemon(); boolean alive=true; int side=0;
    ActiveBattlePokemon(PokemonBattle b){battle=b;b.active.add(this);}
    BattlePokemon getBattlePokemon(){return self;} PokemonBattle getBattle(){return battle;} boolean isAlive(){return self!=null&&alive;} int getSide(){return side;}
  }
  static class PokemonBattle {
    UUID id=UUID.randomUUID(); boolean ended; List<ActiveBattlePokemon> active=new ArrayList<>();
    UUID getBattleId(){return id;} boolean getEnded(){return ended;} List<ActiveBattlePokemon> getActivePokemon(){return active;}
  }
  static class ServerTickEvent { static class Post {} }
  static class ExtBridge { static boolean patched=true; static void ensurePatched(){} static boolean isPatched(){return patched;} }
  static final List<Pokemon> triggers=new ArrayList<>();
  static void triggerDragonFruit(PokemonBattle b,BattlePokemon self,Pokemon pokemon){triggers.add(pokemon);TRIGGERED.computeIfAbsent(b.id,k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(pokemon.id);}
  ${events}
  ${production}
  static int checks;
  static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
  static PokemonBattle fixture(){
    IMMEDIATE.clear();TURN_WAITING.clear();TRIGGERED.clear();triggers.clear();ExtEvents.CURRENT_TURN.clear();ExtEvents.READY_MARKED.clear();
    Cobblemon.INSTANCE.max=100;ExtBridge.patched=true;return new PokemonBattle();
  }
  static ActiveBattlePokemon enter(PokemonBattle b){var a=new ActiveBattlePokemon(b);onActivePokemonChanged(a);return a;}
  public static void main(String[] args){
    ExtEvents.BATTLE_TURN.add(e->onBattleTurn(e.battle(),e.turn()));
    ExtEvents.BATTLE_ENDED.add(TurnRegression::onBattleEnded);
    var b=fixture();var a=enter(b);
    ExtEvents.emitTurn(b,1);
    check("lead remains queued throughout turn 1",triggers.isEmpty()&&TURN_WAITING.get(b.id).size()==1);
    ExtEvents.emitTurn(b,2);
    check("lead triggers after one complete turn and locks for the battle",triggers.size()==1&&a.self.pokemon.item.held&&hasTriggeredThisBattle(b.id,a.self.pokemon.id));
    ExtEvents.emitTurn(b,2);ExtEvents.emitTurn(b,3);
    check("later or duplicate turn events do not retrigger",triggers.size()==1&&TURN_WAITING.isEmpty());

    b=fixture();ExtEvents.emitTurn(b,1);a=enter(b);ExtEvents.emitTurn(b,2);
    check("mid-turn switch-in keeps waiting at the next boundary",triggers.isEmpty()&&TURN_WAITING.containsKey(b.id));
    ExtEvents.emitTurn(b,3);
    check("mid-turn switch-in triggers after its next full turn",triggers.size()==1);

    b=fixture();ExtEvents.emitTurn(b,3);a=enter(b);ExtEvents.emitTurn(b,4);ExtEvents.emitTurn(b,5);
    check("end-of-turn replacement also waits a full upcoming turn",triggers.size()==1);

    b=fixture();var early=enter(b);ExtEvents.emitTurn(b,1);var late=enter(b);ExtEvents.emitTurn(b,2);
    check("doubles keep the later holder after the earlier trigger",triggers.equals(List.of(early.self.pokemon))&&TURN_WAITING.get(b.id).size()==1);
    ExtEvents.emitTurn(b,3);check("doubles later holder subsequently triggers",triggers.size()==2);

    b=fixture();a=enter(b);var saved=a.self;ExtEvents.emitTurn(b,1);a.self=null;onActivePokemonChanged(a);ExtEvents.emitTurn(b,2);
    check("switch-out cancels without consuming fruit",triggers.isEmpty()&&saved.pokemon.item.held&&TURN_WAITING.isEmpty());
    a.self=saved;onActivePokemonChanged(a);ExtEvents.emitTurn(b,3);
    check("returning holder starts a fresh waiting period",triggers.isEmpty());
    ExtEvents.emitTurn(b,4);check("returning holder triggers at the fresh boundary",triggers.size()==1);

    b=fixture();a=enter(b);ExtEvents.emitTurn(b,1);a.alive=false;ExtEvents.emitTurn(b,2);
    check("faint cancels even before slot cleanup",triggers.isEmpty()&&a.self.pokemon.item.held&&TURN_WAITING.isEmpty());
    b=fixture();a=enter(b);a.self.pokemon.item.held=false;ExtEvents.emitTurn(b,1);
    check("lost held item cancels before due turn",TURN_WAITING.isEmpty()&&triggers.isEmpty());
    b=fixture();a=enter(b);ExtEvents.emitTurn(b,1);b.ended=true;ExtEvents.emitBattleEnded(b);
    check("battle end releases waits and turn state without consumption",TURN_WAITING.isEmpty()&&ExtEvents.currentTurn(b.id)==0&&a.self.pokemon.item.held);

    b=fixture();a=enter(b);ExtEvents.emitTurn(b,1);ExtEvents.emitTurn(b,2);
    check("burst locks the battle but keeps the item",triggers.size()==1&&a.self.pokemon.item.held&&hasTriggeredThisBattle(b.id,a.self.pokemon.id));
    onActivePokemonChanged(a);ExtEvents.emitTurn(b,3);
    check("same battle cannot trigger twice",triggers.size()==1&&TURN_WAITING.isEmpty()&&IMMEDIATE.isEmpty());
    ExtEvents.emitBattleEnded(b);
    var again=new PokemonBattle();a.battle=again;again.active.add(a);onActivePokemonChanged(a);
    check("new battle re-arms the held fruit",TURN_WAITING.get(again.id).size()==1&&triggers.size()==1);
    ExtEvents.emitTurn(again,1);ExtEvents.emitTurn(again,2);
    check("same holder bursts again in the new battle",triggers.size()==2&&hasTriggeredThisBattle(again.id,a.self.pokemon.id));

    b=fixture();a=enter(b);var other=fixtureIndependent();ExtEvents.emitTurn(b,1);ExtEvents.emitTurn(b,2);
    check("one battle does not drop another battle's wait",triggers.size()==1&&TURN_WAITING.containsKey(other.id));

    b=fixture();a=new ActiveBattlePokemon(b);a.self.pokemon.level=100;a.self.pokemon.types=List.of(new Type("flying"),new Type("fire"));
    onActivePokemonChanged(a);var foe=new ActiveBattlePokemon(b);foe.side=1;ExtEvents.markBattleActiveReady(b);
    a.self.entity.beam=1;onServerTick(new ServerTickEvent.Post());
    check("max-level dual fire type waits for entry animation",triggers.isEmpty()&&IMMEDIATE.size()==1);
    a.self.entity.beam=0;onServerTick(new ServerTickEvent.Post());
    check("max-level fire type still triggers immediately",triggers.size()==1&&IMMEDIATE.isEmpty());
    onActivePokemonChanged(a);
    check("immediate path also honors the once-per-battle lock",triggers.size()==1&&IMMEDIATE.isEmpty());

    b=fixture();a=new ActiveBattlePokemon(b);a.self.pokemon.level=50;a.self.pokemon.types=List.of(new Type("fire"));
    Cobblemon.INSTANCE.max=50;onActivePokemonChanged(a);
    check("fire eligibility uses configured level cap",IMMEDIATE.containsKey(a));
    a.self.pokemon.item.held=false;onServerTick(new ServerTickEvent.Post());
    check("immediate holder losing fruit cannot trigger",IMMEDIATE.isEmpty()&&triggers.isEmpty());
    System.out.println(checks+" Java scheduling checks passed.");
  }
  static PokemonBattle fixtureIndependent(){var b=new PokemonBattle();enter(b);return b;}
}
`;
const file = path.join(output, 'TurnRegression.java');
fs.writeFileSync(file, java);
const jdk = process.argv[2] || process.env.JAVA_HOME;
function run(tool, args) {
  const executable = jdk ? path.join(jdk, 'bin', tool + (process.platform === 'win32' ? '.exe' : '')) : tool;
  const result = spawnSync(executable, args, {encoding: 'utf8'});
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
run('javac', ['-encoding', 'UTF-8', '-d', output, file]);
run('java', ['-cp', output, 'TurnRegression']);
