const { Client, GatewayIntentBits } = require("discord.js");
const config = require("./config");
const commands = require("./commands"); // ✅ ini benar kalau di root

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent
  ]
});

client.on("messageCreate", commands);

client.login(config.token);