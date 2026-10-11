# Dialogue Guard

A RuneLite plugin that keeps dialogue keys out of the chat box.

If you hold `1` or space to get through a conversation and the dialogue ends a keypress earlier than you expected, the extra presses end up in chat as `111` or get sent as a message. This plugin blocks those presses.

## How it works

Each dialogue page takes one key: space on a chat page, a number on an option menu (number or space on a make-x screen). Keys the page would ignore anyway, like a space mashed onto an option menu, go through and don't use the page up, so the number you press next still picks your option. Once the page has its key, further dialogue keys (`1` to `9` on the top row or numpad, space, enter) are blocked until the next page loads, because one landing just as the last page closes would end up in chat.

After the dialogue closes, the plugin keeps blocking dialogue keys until you stop pressing them for the guard window (600 ms by default). A key that is still held down counts as pressing, so the timer starts when you let go.

Pressing any other key, such as a letter, ends the guard right away since you are clearly typing on purpose. Nothing is blocked unless a dialogue key was just pressed, so normal chat is not affected. If you turn on the hint, a small "blocked N keys" note shows above the chat box when a key is kept out of chat after a dialogue, so you can tell the key was blocked rather than ignored. Keys held back while a page is still open are blocked quietly.

Recognised dialogues: NPC and player chat, option menus, message and sprite boxes, level-up screens, and the make-x prompt.

### Never start a message with a space

On by default. When no dialogue is open and the chat box is empty, the space bar is dropped so a message can never begin with spaces. Space still works in dialogues, in chat-box prompts such as bank or Grand Exchange search and "Enter amount", and after you have typed something.

### Clear leftover junk when a dialogue opens

On by default. If the chat box contains only digits and spaces when a dialogue starts (for example a `111` left over from the last conversation), it is cleared so it cannot be sent later by accident. Anything with real text in it is left alone.

### Hint

Off by default. When on, a small note above the right end of the chat box shows how many keys were blocked in the current burst and, optionally, the all-time total.

## Settings

- Guard number keys, guard space, guard enter (each on by default)
- Guard window, 100 to 3000 ms
- Never start a message with a space
- Clear leftover junk when a dialogue opens
- Show hint (off by default)
- Show keys blocked all-time in hint

## Code layout

- `GuardState` holds the decision logic and has no RuneLite dependency. It takes dialogue opened/closed events and key pressed/released events and answers pass, hold (quietly blocked: the open page already has its key) or block (spill-over). Covered by `GuardStateTest`.
- `DialogueKeys` holds the plain lookups: which interfaces are dialogues, which keys are dialogue keys (numpad digits count), and which keys each dialogue acts on. Covered by `DialogueKeysTest`.
- `DialogueGuardPlugin` is the RuneLite wiring. It feeds widget and key events into `GuardState`, consumes blocked keys, and handles the two client-dependent features (leading space rule, junk clearing) plus the saved counter.
- `DialogueGuardHint` is the overlay.

## Building

Run `./gradlew build`, or double-click `build.command` or `run-client.command` (output goes to `build.log` or `run-client.log`).
