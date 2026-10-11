# Dialogue Guard

A RuneLite plugin that stops dialogue-skipping keys from spilling into the chat box. You hammer `1` or space to get
through a conversation, the dialogue ends a keypress earlier than you expected, and the next few presses land in
chat as `111` or an accidental message. This swallows them.

## How it decides
A dialogue page accepts exactly one guarded key (`1`–`9`, space, enter). Once one has been sent the plugin is
*armed* and swallows every further guarded key until either:

- a new dialogue page loads (that page needs a key of its own), or
- you stop pressing guarded keys for the guard window (default 600 ms).

Any other key — a letter — ends the guard immediately, since that is clearly deliberate typing. Nothing is ever
swallowed unless a dialogue key has just been pressed, so normal chat is untouched. A small "swallowed N keys"
note appears above the chat box so a swallowed key never looks like a dead keyboard.

Dialogues recognised: NPC and player chat, option menus, message and sprite boxes, level-up screens, make-x.

**Never start a message with a space** (on by default): with no dialogue open and the chat box empty, the space
bar is dropped outright, so a message can never begin with spaces. Space still works in dialogues and once you've
typed something.

**Clear leftover junk when a dialogue opens** (on by default): if the chat box holds only digits and spaces when a
dialogue starts — a `111` left over from the last conversation — it is cleared so it can't be sent later. Real text
is never touched.

The hint shows the burst just swallowed and, optionally, the all-time total of keys the plugin has eaten for you.

## Config
Guard number keys / space / enter (each on by default), guard window (100–3000 ms), never start a message with a
space, clear leftover junk when a dialogue opens, show hint, show keys saved all-time in hint.

## Building
`./gradlew build`, or double-click `build.command` / `run-client.command` (writes `build.log` / `run-client.log`).
