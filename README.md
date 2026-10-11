# Dialogue Guard

A RuneLite plugin that keeps dialogue keys out of the chat box.

If you hold `1` or space to get through a conversation and the dialogue ends a keypress earlier than you expected, the extra presses end up in chat as `111` or get sent as a message. This plugin blocks those presses.

## How it works

Each dialogue page accepts one guarded key (`1` to `9`, space, or enter). After that key is sent, the plugin blocks any further guarded keys until one of two things happens:

- A new dialogue page loads. That page needs a key of its own.
- You stop pressing guarded keys for the guard window (600 ms by default). A key that is still held down counts as pressing, so the timer starts when you let go.

Pressing any other key, such as a letter, ends the guard right away since you are clearly typing on purpose. Nothing is blocked unless a dialogue key was just pressed, so normal chat is not affected. A small "blocked N keys" note shows above the chat box so you can tell the key was blocked rather than ignored.

Recognised dialogues: NPC and player chat, option menus, message and sprite boxes, level-up screens, and the make-x prompt.

### Never start a message with a space

On by default. When no dialogue is open and the chat box is empty, the space bar is dropped so a message can never begin with spaces. Space still works in dialogues and after you have typed something.

### Clear leftover junk when a dialogue opens

On by default. If the chat box contains only digits and spaces when a dialogue starts (for example a `111` left over from the last conversation), it is cleared so it cannot be sent later by accident. Anything with real text in it is left alone.

### Hint

The hint shows how many keys were blocked in the current burst and, optionally, the all-time total.

## Settings

- Guard number keys, guard space, guard enter (each on by default)
- Guard window, 100 to 3000 ms
- Never start a message with a space
- Clear leftover junk when a dialogue opens
- Show hint
- Show keys blocked all-time in hint

## Code layout

- `GuardState` holds the decision logic and has no RuneLite dependency. It takes dialogue opened/closed events and key pressed/released events and answers pass or block. Covered by `GuardStateTest`.
- `DialogueGuardPlugin` is the RuneLite wiring. It feeds widget and key events into `GuardState`, consumes blocked keys, and handles the two client-dependent features (leading space rule, junk clearing) plus the saved counter.
- `DialogueGuardHint` is the overlay.

## Building

Run `./gradlew build`, or double-click `build.command` or `run-client.command` (output goes to `build.log` or `run-client.log`).
