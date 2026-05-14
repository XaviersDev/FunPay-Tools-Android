// @name UI Stress Tester MAX
// @author AI Assistant
// @version 1.0
// @description Максимальный стресс-тест UI и API FunPay Tools
// @banner https://i.imgur.com/example.png

var key = "ui_stress_test_state";

// состояние
function getState() {
    try {
        return JSON.parse(fpt.storage.get(key) || "{}");
    } catch (e) {
        return {};
    }
}

function setState(s) {
    fpt.storage.set(key, JSON.stringify(s));
}

// лог ошибок безопасно
function safe(fn, name) {
    try {
        fn();
        fpt.app.log("OK: " + name);
    } catch (e) {
        fpt.app.log("FAIL: " + name + " -> " + e);
    }
}

// ==========================
// 🔥 UI RENDER
// ==========================
function render() {
    var ui = {
        type: "Card",
        children: [
            {
                type: "Text",
                text: "UI STRESS TESTER",
                bold: true,
                fontSize: 18.0
            },
            {
                type: "Text",
                text: "Тестирует UI / Storage / Chat / App API"
            },

            {
                type: "Button",
                text: "▶ Запустить полный тест",
                onClick: "runFullTest()"
            },

            {
                type: "Button",
                text: "⚡ UI Spam Test",
                onClick: "uiSpam()"
            },

            {
                type: "Button",
                text: "💾 Storage Test",
                onClick: "storageTest()"
            },

            {
                type: "Button",
                text: "💬 Chat API Test",
                onClick: "chatTest()"
            },

            {
                type: "Button",
                text: "📱 App API Test",
                onClick: "appTest()"
            }
        ]
    };

    fpt.ui.setSlot("settings_ui_stress", ui);
}

// ==========================
// ⚡ FULL TEST
// ==========================
window.runFullTest = function () {
    fpt.app.toast("Запуск UI стресс теста...");

    safe(uiSpam, "uiSpam");
    safe(storageTest, "storageTest");
    safe(chatTest, "chatTest");
    safe(appTest, "appTest");

    fpt.app.notify("UI Test", "Тест завершён. Смотри лог.");
};

// ==========================
// ⚡ UI TESTS
// ==========================
window.uiSpam = function () {
    for (var i = 0; i < 5; i++) {
        fpt.ui.setSlot("stress_" + i, {
            type: "Card",
            children: [
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
