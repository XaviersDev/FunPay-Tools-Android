// @name FunTime AutoKK
// @author WhiteStrang6r
// @version 1.12.2
// @description Плагин автоматически выдаёт людям валюту в заказах по нику и номером анки
// @banner https://i.ibb.co/hJfcZvKR/Picsart-26-05-23-16-17-49-943.png

// Обязательно вешаем функцию на window, чтобы Android смог её вызвать
window.saveFunTimeSettings = function() {
    fpt.app.toast("Настройки FunTime AutoKK сохранены! (заглушка)");
};

function renderFunTimeUI() {
    var ui = {
        type: "Card",
        children: [
            { type: "Text", text: "Настройки FunTime AutoKK", bold: true, fontSize: 16.0 },
            { type: "Spacer", size: 8 },
            { type: "Text", text: "Функционал автовыдачи валюты скоро будет добавлен. Это заглушка.", color: "#888888" },
            { type: "Spacer", size: 12 },
            { type: "Button", text: "Применить", onClick: "window.saveFunTimeSettings()" }
        ]
    };
    
    // Используем встроенную системную переменную PLUGIN_SLOT_KEY
    fpt.ui.setSlot(PLUGIN_SLOT_KEY, ui);
}

// Отрисовываем интерфейс при запуске
renderFunTimeUI();
fpt.app.log("Плагин FunTime AutoKK (заглушка) успешно инициализирован.");
