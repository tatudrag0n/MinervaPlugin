from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

old = '''               FfaManager.FfaSession victimSessionForCrusher = this.sessions.get(victim.getUniqueId());
               if (victimSessionForCrusher != null && victimSessionForCrusher.kit == FfaKit.CRUSHER) {
                  this.triggerCrusherExplosion(victim, attacker, false);
               }

               this.applyGamblerIncoming(event, victim);
               FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());
               if (session != null) {
                  if (session.kit == FfaKit.CRUSHER) {
                     this.triggerCrusherExplosion(attacker, victim, true);
                  }
'''
new = '''               boolean crusherExplosionEvent = this.crusherExplosionDamage.contains(attacker.getUniqueId());
               FfaManager.FfaSession victimSessionForCrusher = this.sessions.get(victim.getUniqueId());
               if (!crusherExplosionEvent && victimSessionForCrusher != null && victimSessionForCrusher.kit == FfaKit.CRUSHER) {
                  this.triggerCrusherExplosion(victim, attacker, false);
               }

               this.applyGamblerIncoming(event, victim);
               FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());
               if (session != null) {
                  if (!crusherExplosionEvent && session.kit == FfaKit.CRUSHER) {
                     this.triggerCrusherExplosion(attacker, victim, true);
                  }
'''

if old not in text:
    raise SystemExit('Crusher trigger block not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8', newline='\n')
