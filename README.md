AutoNexus Core v1.0
====================

Thank you for purchasing **AutoNexus Core**.
This package contains everything you need to run the network core on **Velocity** and **Paper/Spigot** servers.

--------------------
1. Contents
--------------------

Root:
- `Velocity_Proxy/`
- `Paper_Spigot_Servers/`

Velocity_Proxy:
- `nexus-proxy-1.0.0.jar` — Velocity plugin
- `config.yml` — default proxy configuration

Paper_Spigot_Servers:
- `nexus-server-1.0.0.jar` — Paper/Spigot plugin
- `config.yml` — default server configuration

--------------------
2. Requirements
--------------------

- **Java:** 21 (required on both proxy and backend servers)
- **Proxy:** Velocity 3.3.x (or compatible 3.x build)
- **Backend:** Paper 1.20.4 (or fork based on 1.20.4)
- **Data:** Redis 5+/6+ reachable from both proxy and servers

--------------------
3. Installation
--------------------

Velocity (Proxy):
1. Stop your Velocity proxy.
2. Copy `Velocity_Proxy/nexus-proxy-1.0.0.jar` into `velocity/plugins/`.
3. Start the proxy once to generate `plugins/autonexus/config.yml` (or use the provided config.yml).
4. Adjust `redis` and `network.namespace` values in `config.yml` to match your environment.

Paper / Spigot (Servers):
1. Stop each backend server.
2. Copy `Paper_Spigot_Servers/nexus-server-1.0.0.jar` into `plugins/`.
3. Start the server once to let AutoNexus generate its data folder and config.
4. Replace or merge with the provided `config.yml`.
5. Make sure:
   - `server-name` is unique per server (e.g. `lobby`, `lobby-1`, `survival-1`).
   - `group` reflects your logical group (e.g. `lobby`, `survival`).
   - `redis` and `network.namespace` match the proxy settings.

Redis:
- Use a single Redis instance (or cluster) reachable from both proxy and all servers.
- AutoNexus uses Redis for:
  - server discovery and heartbeats
  - global player tracking
  - metadata and command dispatch

--------------------
4. Core Commands
--------------------

All commands are executed on the **server side** (Paper/Spigot).

Base:
- `/nexus help` — list available subcommands.

Player command:
- `/nexus join <server>`  
  - Teleport the player to another server in the network.
  - Controlled by `commands.join` in server `config.yml`:
    - `enabled`: enable/disable the command on this server.
    - `cooldown`: per-player cooldown in seconds.
    - `validation`: check server availability before teleport.
    - `require-permission`: if `true`, requires `autonexus.command.join`; if `false`, open for everyone.

Admin commands (require `autonexus.admin`):
- `/nexus servers` — show live list of online servers (based on Redis heartbeats).
- `/nexus find <player>` — locate a player across the network (global Tab-Completion).
- `/nexus broadcast <message>` — global network-wide announcement.
- `/nexus reload` — reload AutoNexus configuration on the current server.

--------------------
5. Key Features
--------------------

- Redis-based network core (no SQL in critical path)
- Global player tracking with zero main-thread impact
- Self-cleaning heartbeats to prevent ghost servers
- TTL-protected online player cache to prevent ghost players
- Built-in command anti-spam and cooldowns for `/nexus join`

--------------------
6. Basic Configuration Notes
--------------------

Proxy `config.yml` (Velocity_Proxy):
- `redis.host` / `redis.port` / `redis.password` — connection to Redis.
- `network.namespace` — namespace prefix used for all keys and channels.
- `network.heartbeat-interval` — proxy-side monitoring interval.
- `settings.group` — logical group label for this proxy instance.

Server `config.yml` (Paper_Spigot_Servers):
- `server-name` — unique server ID, used in `/nexus join <server>`.
- `group` — logical group name, used for grouped command dispatch.
- `redis.*` — connection to the same Redis as proxy.
- `network.heartbeat-interval` / `cleanup-threshold` — heartbeat and cleanup behavior.
- `commands.join` / `commands.servers` / `commands.find` / `commands.broadcast` — enable/disable subcommands and tune UX.

--------------------
7. Developer API (Addons)
--------------------

AutoNexus exposes a simple API for other plugins on your servers.

Entry points:
- **Service Manager (Bukkit):**
  - Service: `lytblu7.autonexus.common.INexusAPI`
- **Static accessor:**
  - `lytblu7.autonexus.common.NexusProvider.get()`

Conceptually, the API allows you to:
- Get global player data (including current server and metadata).
- Dispatch console commands to specific servers or server groups.
- Work with global metadata fields (balances, flags, etc.).

Typical patterns:
- On a backend plugin:
  - Obtain `INexusAPI` from the Bukkit ServicesManager or `NexusProvider.get()`.
  - Use API methods to:
    - read/update player metadata
    - run cross-server commands
    - query global leaderboards (if enabled)

Important:
- Always treat API calls as potentially asynchronous and avoid blocking the main thread with heavy work.
- Redis is the primary data source; avoid introducing your own heavy I/O on the main thread.

--------------------
8. Support
--------------------

- Contact email: `myshopandmyl1fe@gmail.com`
- Support channels: Discord & email (see marketplace listing for the latest links).

If you run into issues with configuration, connectivity to Redis, or network behavior, collect:
- proxy log
- backend server log
- your `config.yml` files

and send them along with a short description of your setup (number of servers, version of Velocity/Paper, Java version).
