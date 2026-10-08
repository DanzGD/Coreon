# AnarchyCore 2.1

Plugin Paper 26.3 / Java 25 untuk server anarki. Default dibuat se-vanilla mungkin.

## Fitur
- Discord webhook bridge
- Saldo dollar: /balance, /pay, /baltop, /eco
- Market dinamis: /sell hand, /sell all, /sell price, /sell list
- Auto-pricing untuk semua Material yang merupakan item
- Combat tag dan combat logging
- Bounty
- Statistik /stats dan /top
- Anti-lag opsional

## Auto pricing
Item yang belum ditulis di tiers atau overrides otomatis diberi harga. tiers dan overrides tetap punya prioritas.

market:
  auto-price:
    enabled: true
    depth: 64

## Build
Gunakan Java 25 dan Maven:

    mvn package

JAR ada di target/AnarchyCore-2.1.0.jar.

## Setup
Taruh JAR di folder plugins/, nyalakan server, lalu edit plugins/AnarchyCore/config.yml.

Untuk reload:
    /anarchycore reload
