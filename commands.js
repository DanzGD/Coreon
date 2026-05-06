const { getUser, updateUser } = require("./utils/db");
const { rollItem, checkLevelUp } = require("./utils/rng");
const { getPrice } = require("./utils/crypto");

module.exports = async (message) => {
  if (!message.content.startsWith("!") || message.author.bot) return;

  const args = message.content.slice(1).split(" ");
  const cmd = args.shift().toLowerCase();

  const user = getUser(message.author.id);

  // 🎰 ROLL
  if (cmd === "roll") {
    const item = rollItem(user.luck);

    user.inventory[item.name] = (user.inventory[item.name] || 0) + 1;
    user.xp += 15;

    const levelUp = checkLevelUp(user);

    updateUser(user);

    message.reply(`🎰 Dapat: ${item.name} (${item.rarity})${levelUp ? "\n🔥 Level Up!" : ""}`);
  }

  // ⛏️ MINE
  else if (cmd === "mine") {
    const rand = Math.random() * 100;

    if (rand < 50) {
      user.iron += 1;
      message.reply("⛏️ Dapat Iron");
    } else if (rand < 80) {
      user.gold += 1;
      message.reply("✨ Dapat Gold");
    } else {
      user.diamond += 1;
      message.reply("💎 Dapat Diamond");
    }

    user.xp += 10;
    updateUser(user);
  }

  // 🎒 INVENTORY
  else if (cmd === "inventory") {
    let text = "🎒 Inventory:\n";
    for (let item in user.inventory) {
      text += `- ${item} x${user.inventory[item]}\n`;
    }
    message.reply(text || "Kosong");
  }

  // 🧑 PROFILE
  else if (cmd === "profile") {
    message.reply(`
🧑 Profile:
Level: ${user.level}
XP: ${user.xp}
Coins: ${user.coins}
Luck: ${user.luck}

⛏️ Iron: ${user.iron}
✨ Gold: ${user.gold}
💎 Diamond: ${user.diamond}

🪙 Crypto: ${user.crypto}
    `);
  }

  // 🎁 DAILY
  else if (cmd === "daily") {
    const now = Date.now();

    if (now - user.lastDaily < 86400000) {
      return message.reply("Udah claim hari ini!");
    }

    user.coins += 500;
    user.lastDaily = now;

    updateUser(user);

    message.reply("🎁 +500 coins");
  }

  // 🪙 WORK
  else if (cmd === "work") {
    const now = Date.now();

    if (now - user.lastWork < 300000) {
      return message.reply("Tunggu dulu (5 menit)");
    }

    const reward = Math.floor(Math.random() * 300) + 100;

    user.coins += reward;
    user.lastWork = now;

    updateUser(user);

    message.reply(`🪙 Kamu dapat ${reward} coins`);
  }

  // 💣 GAMBLE
  else if (cmd === "gamble") {
    const amount = parseInt(args[0]);
    if (!amount) return message.reply("Masukkan jumlah!");

    if (user.coins < amount) return message.reply("Coins kurang");

    if (Math.random() < 0.5) {
      user.coins += amount;
      message.reply(`Menang +${amount}`);
    } else {
      user.coins -= amount;
      message.reply(`Kalah -${amount}`);
    }

    updateUser(user);
  }

  // 📊 PRICE
  else if (cmd === "price") {
    const price = getPrice();
    message.reply(`📊 Harga CoreCoin: ${price}`);
  }

  // 💰 BUY CRYPTO
  else if (cmd === "buy") {
    const amount = parseInt(args[0]);
    if (!amount) return message.reply("Masukkan jumlah!");

    const price = getPrice();
    const total = price * amount;

    if (user.coins < total) return message.reply("Coins tidak cukup");

    user.coins -= total;
    user.crypto += amount;

    updateUser(user);

    message.reply(`🪙 Beli ${amount} CC`);
  }

  // 💸 SELL CRYPTO
  else if (cmd === "sellcrypto") {
    const amount = parseInt(args[0]);
    if (!amount) return message.reply("Masukkan jumlah!");

    if (user.crypto < amount) return message.reply("Crypto tidak cukup");

    const price = getPrice();
    const total = price * amount;

    user.crypto -= amount;
    user.coins += total;

    updateUser(user);

    message.reply(`💸 Jual ${amount} CC dapat ${total}`);
  }
};