from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

player_old = '''                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double multiplier = this.applyVampireProgression(attacker, event.getDamage());
                     event.setDamage(event.getDamage() * multiplier);
                     double dealt = Math.max(0.0, event.getFinalDamage());
                     double hungerGain = dealt
                        * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "hunger-steal-percent"), 50.0))
                        / 100.0;
                     int before = attacker.getFoodLevel();
                     int restored = Math.max(1, (int)Math.ceil(hungerGain));
                     attacker.setFoodLevel(Math.min(20, before + restored));
                     attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
                     attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));
                     attacker.sendActionBar(Component.text("吸血 満腹度 +" + (attacker.getFoodLevel() - before), NamedTextColor.RED));
                  }
'''
player_new = '''                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double multiplier = this.applyVampireProgression(attacker, event.getDamage());
                     event.setDamage(event.getDamage() * multiplier);
                     double dealt = Math.min(
                        victim.getHealth() + victim.getAbsorptionAmount(),
                        Math.max(0.0, event.getFinalDamage())
                     );
                     this.applyVampireSteal(attacker, dealt);
                  }
'''
if player_old not in text:
    raise SystemExit('player vampire block not found')
text = text.replace(player_old, player_new, 1)

# The training-Husk adapter had two separate Vampire hunger branches, so it could
# restore hunger twice while still never restoring health. Remove the first one.
training_duplicate = '''      if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
         int before = attacker.getFoodLevel();
         int restored = Math.max(1, (int)Math.ceil(event.getFinalDamage() * 0.5));
         attacker.setFoodLevel(Math.min(20, before + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
      }

'''
if training_duplicate in text:
    text = text.replace(training_duplicate, '', 1)

training_old = '''      if (session.kit == FfaKit.VAMPIRE && !event.isCancelled() && event.getDamage() > 0.0) {
         double multiplier = this.applyVampireProgression(attacker, event.getDamage());
         event.setDamage(event.getDamage() * multiplier);
         double dealt = Math.max(0.0, event.getDamage());
         int restored = Math.max(1, (int)Math.ceil(dealt * 0.5));
         attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
         attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));
      }
'''
training_new = '''      if (session.kit == FfaKit.VAMPIRE && !event.isCancelled() && event.getDamage() > 0.0) {
         double multiplier = this.applyVampireProgression(attacker, event.getDamage());
         event.setDamage(event.getDamage() * multiplier);
         double dealt = Math.min(husk.getHealth(), Math.max(0.0, event.getFinalDamage()));
         this.applyVampireSteal(attacker, dealt);
      }
'''
if training_old not in text:
    raise SystemExit('training vampire block not found')
text = text.replace(training_old, training_new, 1)

helper = '''   private void applyVampireSteal(Player attacker, double dealtDamage) {
      double dealt = Math.max(0.0, dealtDamage);
      if (dealt <= 0.0) {
         return;
      }

      double lifestealPercent = Math.max(
         0.0,
         this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "lifesteal-percent"), 30.0)
      );
      double requestedHealing = dealt * lifestealPercent / 100.0;
      AttributeInstance maxHealthAttribute = attacker.getAttribute(Attribute.MAX_HEALTH);
      double maxHealth = maxHealthAttribute == null ? 20.0 : maxHealthAttribute.getValue();
      if (requestedHealing > 0.0 && attacker.getHealth() < maxHealth) {
         attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + requestedHealing));
      }

      double hungerGain = dealt
         * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "hunger-steal-percent"), 30.0))
         / 100.0;
      if (hungerGain > 0.0) {
         int restored = Math.max(1, (int)Math.ceil(hungerGain));
         attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
         attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));
      }
   }

'''
marker = '   private double applyVampireProgression(Player attacker, double baseDamage) {'
if 'private void applyVampireSteal(' not in text:
    if marker not in text:
        raise SystemExit('vampire progression marker not found')
    text = text.replace(marker, helper + marker, 1)

path.write_text(text, encoding='utf-8', newline='\n')
