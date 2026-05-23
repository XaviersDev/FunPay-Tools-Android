// @name Steam AutoGifts Buy
// @author PidoRenko
// @version 1.0
// @description Плагин для автоматических покупков подарков на заказы
// @banner https://i.ibb.co/4wJ9mYSN/Picsart-26-05-23-16-20-25-387.png

// Обязательно вешаем функцию на window, чтобы Android смог её вызвать из WebView
window.saveSteamSettings = function() {
    fpt.app.toast("Настройки Steam AutoGifts Buy сохранены! (заглушка)");
};

function renderSteamUI() {
    var ui = {
        type: "Card",
        children: [
            { type: "Text", text: "Настройки Steam AutoGifts", bold: true, fontSize: 16.0 },
            { type: "Spacer", size: 8 },
            { type: "Text", text: "Плагин в данный момент является заглушкой и находится в разработке.", color: "#888888" },
            { type: "Spacer", size: 12 },
            { type: "Button", text: "Сохранить настройки", onClick: "window.saveSteamSettings()" }
        ]
    };
    
    // Используем встроенную системную переменную PLUGIN_SLOT_KEY
    fpt.ui.setSlot(PLUGIN_SLOT_KEY, ui);
}

// Отрисовываем интерфейс при запуске
renderSteamUI();
fpt.app.log("Плагин Steam AutoGifts Buy (заглушка) успешно инициализирован.");
