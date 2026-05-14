// @name Advanced Commands GUI
// @author AI Assistant
// @version 2.0
// @description GUI система кастомных команд
// @banner https://raw.githubusercontent.com/XaviersDev/FunPayTools-Site/refs/heads/main/default-banner.jpeg

var storageKey = "advanced_commands_gui";

// =======================
// STORAGE
// =======================

function loadCommands() {
    try {
        var raw = fpt.storage.get(storageKey);
        if (!raw) return [];
        return JSON.parse(raw);
    } catch (e) {
        return [];
    }
}

function saveCommands(cmds) {
    fpt.storage.set(storageKey, JSON.stringify(cmds));
}

// =======================
// HELPERS
// =======================

function findCommand(trigger) {
    var cmds = loadCommands();

    for (var i = 0; i < cmds.length; i++) {
        if (cmds[i].trigger === trigger) {
            return cmds[i];
        }
    }

    return null;
}

// =======================
// MESSAGE HANDLER
// =======================

fpt.on("onNewMessage", function(msg) {

    if (msg.isMe) return;

    var enabled = fpt.ui.getState("commands_enabled");

    if (enabled !== "true") return;

    var text = msg.text;

    if (!text) return;

    if (text.charAt(0) !== "!") return;

    var trigger = text.split(" ")[0].substring(1);

    var args = text.split(" ").slice(1).join(" ");

    var cmd = findCommand(trigger);

    if (!cmd) return;

    var response = cmd.response;

    response = response.replace("{args}", args);

    if (response.length > 2000) {
        response = response.substring(0, 1990);
    }

    fpt.chat.send(msg.chatId, response);
});

// =======================
// ADD COMMAND
// =======================

window.addCommand = function() {

    var trigger = fpt.ui.getState("cmd_trigger");
    var response = fpt.ui.getState("cmd_response");

    if (!trigger || !response) {
        fpt.app.toast("Заполни поля");
        return;
    }

    trigger = trigger.replace("!", "").trim();

    var cmds = loadCommands();

    cmds.push({
        trigger: trigger,
        response: response
    });

    saveCommands(cmds);

    fpt.app.toast("Команда !" + trigger + " добавлена");

    renderUi();
};

// =======================
// DELETE COMMAND
// =======================

window.deleteCommand = function() {

    var trigger = fpt.ui.getState("delete_trigger");

    if (!trigger) {
        fpt.app.toast("Введите команду");
        return;
    }

    trigger = trigger.replace("!", "").trim();

    var cmds = loadCommands();

    var newCmds = [];

    for (var i = 0; i < cmds.length; i++) {
        if (cmds[i].trigger !== trigger) {
            newCmds.push(cmds[i]);
        }
    }

    saveCommands(newCmds);

    fpt.app.toast("Команда удалена");

    renderUi();
};

// =======================
// DEMO COMMANDS
// =======================

window.installDemo = function() {

    var cmds = [
        {
            trigger: "ping",
            response: "Pong!"
        },
        {
            trigger: "echo",
            response: "{args}"
        },
        {
            trigger: "hi",
            response: "Привет 👋"
        }
    ];

    saveCommands(cmds);

    fpt.app.toast("Демо команды установлены");

    renderUi();
};

// =======================
// UI
// =======================

function renderUi() {

    var cmds = loadCommands();

    var children = [

        {
            type: "Text",
            text: "Advanced Commands GUI",
            bold: true,
            fontSize: 18.0
        },

        {
            type: "Spacer",
            size: 8
        },

        {
            type: "Checkbox",
            text: "Включить систему команд",
            stateKey: "commands_enabled"
        },

        {
            type: "Divider",
            padding: 6
        },

        {
            type: "Text",
            text: "Создать команду"
        },

        {
            type: "Input",
            stateKey: "cmd_trigger",
            label: "Команда без !",
            singleLine: true
        },

        {
            type: "Input",
            stateKey: "cmd_response",
            label: "Ответ команды",
            singleLine: false
        },

        {
            type: "Button",
            text: "➕ Добавить команду",
            onClick: "addCommand()"
        },

        {
            type: "Divider",
            padding: 6
        },

        {
            type: "Text",
            text: "Удаление команды"
        },

        {
            type: "Input",
            stateKey: "delete_trigger",
            label: "Название команды",
            singleLine: true
        },

        {
            type: "Button",
            text: "❌ Удалить команду",
            onClick: "deleteCommand()"
        },

        {
            type: "Divider",
            padding: 6
        },

        {
            type: "Button",
            text: "⚡ Установить демо команды",
            onClick: "installDemo()"
        },

        {
            type: "Spacer",
            size: 10
        },

        {
            type: "Text",
            text: "Список команд:"
        }
    ];

    // список команд
    for (var i = 0; i < cmds.length; i++) {

        children.push({
            type: "Card",
            children: [
                {
                    type: "Text",
                    text: "!" + cmds[i].trigger,
                    bold: true
                },
                {
                    type: "Text",
                    text: cmds[i].response
                }
            ]
        });
    }

    var ui = {
        type: "Card",
        children: children
    };

    fpt.ui.setSlot("settings_" + storageKey, ui);
}

// =======================
// INIT
// =======================

renderUi();

fpt.app.log("Advanced Commands GUI loaded");            children: [
                { type: "Text", text: "Stress UI #" + i }
            ]
        });
    }
    fpt.app.toast("UI slots обновлены");
};

// ==========================
// 💾 STORAGE TEST
// ==========================
window.storageTest = function () {
    var s = getState();
    s.counter = (s.counter || 0) + 1;
    setState(s);

    fpt.app.toast("Storage counter: " + s.counter);
};

// ==========================
// 💬 CHAT TEST
// ==========================
window.chatTest = function () {
    try {
        var chats = fpt.chat.getList();
        if (!chats || chats.length === 0) {
            fpt.app.toast("Нет чатов для теста");
            return;
        }

        var id = chats[0].id;

        fpt.chat.send(id, "UI TEST MESSAGE");
        fpt.chat.markRead(id);

        fpt.app.toast("Chat API OK");
    } catch (e) {
        fpt.app.log("Chat test error: " + e);
    }
};

// ==========================
// 📱 APP TEST
// ==========================
window.appTest = function () {
    fpt.app.toast("Toast OK");
    fpt.app.notify("Test", "Notification OK");
    fpt.app.vibrate(100);
    fpt.app.updateWidgets();

    fpt.app.log("App API tested");
};

// ==========================
// INIT
// ==========================
render();
