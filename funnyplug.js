// @name 🌈 RainbowBot ULTRA
// @author XaviersDev
// @version 6.6.6
// @description Чатбот с мини-играми и анимациями. Напиши !help в чате.
// @banner https://www.rookiemag.com/wp-content/themes/rookie/assets/img/sticker/rainbow.png

fpt.app.log("🌈 RainbowBot ULTRA загружается...");

function rand(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}
function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

const RB = ["🔴","🟠","🟡","🟢","🔵","🟣"];
const SP = ["✨","💫","⭐","🌟","💥"];

function rb(n) {
    let s = "";
    for (let i = 0; i < n; i++) s += RB[i % RB.length];
    return s;
}

const DICE = { 1:"⚀", 2:"⚁", 3:"⚂", 4:"⚃", 5:"⚄", 6:"⚅" };
const SLOTS_SYM = ["🍒","🍋","🍊","🍇","⭐","💎","🔔","🍀"];

async function animateRainbow(chatId) {
    const frames = [
        "🔴",
        "🔴 🟠",
        "🔴 🟠 🟡",
        "🔴 🟠 🟡 🟢",
        "🔴 🟠 🟡 🟢 🔵",
        "🔴 🟠 🟡 🟢 🔵 🟣",
        "🔴 🟠 🟡 🟢 🔵 🟣 ✨",
        "🔴 🟠 🟡 🟢 🔵 🟣 ✨ 🌈"
    ];
    for (const f of frames) {
        await fpt.chat.send(chatId, f);
        await sleep(300);
    }
    await fpt.chat.send(chatId, "🌈 РАДУГА! Мир стал лучше " + pick(SP));
}

async function animateFirework(chatId) {
    const frames = ["🚀", "🚀 💨", "💥", "✨ 💥 ✨", "🎆 🌟 🎆", "✨ 💥 🌟 💥 ✨", rb(6) + " БА-БАХ! " + rb(6)];
    for (const f of frames) {
        await fpt.chat.send(chatId, f);
        await sleep(500);
    }
}

async function animateDice(chatId, sides) {
    await fpt.chat.send(chatId, "🎲 Бросаю...");
    await sleep(300);
    for (let i = 0; i < 4; i++) {
        const v = rand(1, 6);
        await fpt.chat.send(chatId, DICE[v] + " " + DICE[v] + " " + DICE[v]);
        await sleep(250);
    }
    const result = rand(1, sides);
    if (sides === 6) {
        const f = DICE[result];
        await fpt.chat.send(chatId, f + " " + f + " " + f + "\nВыпало: " + result + "! " + pick(["Удача!","Судьба!","Роком помечено!","Фортуна!"]));
    } else {
        await fpt.chat.send(chatId, "🎲 d" + sides + " — выпало " + result + "! " + pick(["Удача!","Рок!"]));
    }
}

async function flipCoin(chatId) {
    await fpt.chat.send(chatId, "🪙 Подбрасываю...");
    for (const s of ["🌀","💫","🌀","💫","🌀"]) {
        await fpt.chat.send(chatId, s);
        await sleep(250);
    }
    await fpt.chat.send(chatId, Math.random() < 0.5 ? "🥇 🥇 🥇\nОРЁЛ! 👑" : "⚡ ⚡ ⚡\nРЕШКА! ⚡");
}

async function animateSlots(chatId) {
    await fpt.chat.send(chatId, "🎰 Крутим...");
    await sleep(400);
    for (let i = 0; i < 4; i++) {
        await fpt.chat.send(chatId, pick(SLOTS_SYM) + " | " + pick(SLOTS_SYM) + " | " + pick(SLOTS_SYM));
        await sleep(350);
    }
    const r = [pick(SLOTS_SYM), pick(SLOTS_SYM), pick(SLOTS_SYM)];
    await fpt.chat.send(chatId, r[0] + " | " + r[1] + " | " + r[2]);
    if (r[0] === r[1] && r[1] === r[2]) {
        await fpt.chat.send(chatId, "💎 ДЖЕКПОТ!!! " + r[0] + r[1] + r[2]);
        await animateFirework(chatId);
    } else if (r[0] === r[1] || r[1] === r[2] || r[0] === r[2]) {
        await fpt.chat.send(chatId, "✨ Два одинаковых! Почти...");
    } else {
        await fpt.chat.send(chatId, "😅 Не повезло. !слоты — попробуй снова!");
    }
}

async function animateRoulette(chatId) {
    await fpt.chat.send(chatId, "🎡 Колесо крутится...");
    const wheel = ["🔴","⚫","🟢","🔴","⚫","🔴","⚫","🔴","⚫","🟢"];
    for (let i = 0; i < 5; i++) {
        const o = rand(0, wheel.length - 1);
        await fpt.chat.send(chatId, [0,1,2,3,4].map(j => wheel[(o+j)%wheel.length]).join(" "));
        await sleep(300 + i * 80);
    }
    const num = rand(0, 36);
    const color = num === 0 ? "🟢" : (num % 2 === 0 ? "🔴" : "⚫");
    const name  = num === 0 ? "ЗЕРО" : (num % 2 === 0 ? "Красное" : "Чёрное");
    await fpt.chat.send(chatId, color + " Число: " + num + "\n" + name + "\n" + (num === 0 ? "Казино победило 😅" : "Удача сказала своё слово!"));
}

const guessGames = {};

function startGuessGame(chatId) {
    guessGames[chatId] = { secret: rand(1, 100), attempts: 0, max: 7 };
    return "🎯 Загадал число 1-100!\n7 попыток. Пиши число!\n!сдаюсь — сдаться.";
}

function progressBar(cur, max) {
    const f = Math.round((cur / max) * 7);
    return "[" + "█".repeat(f) + "░".repeat(7-f) + "] " + cur + "/" + max;
}

function guessNumber(chatId, num) {
    const g = guessGames[chatId];
    if (!g) return null;
    g.attempts++;
    const bar = progressBar(g.attempts, g.max);
    if (num === g.secret) {
        delete guessGames[chatId];
        return "🎉 ВЕРНО! Было " + num + "!\n" + bar + "\n" + pick(["Гений!","Телепат!!","Невероятно!"]);
    }
    if (g.attempts >= g.max) {
        const s = g.secret;
        delete guessGames[chatId];
        return "💀 Попытки кончились!\nЗагадано: " + s + "\n" + bar;
    }
    const hint = num < g.secret ? "Больше! ↑" : "Меньше! ↓";
    return hint + "\n" + bar + "\nОсталось: " + (g.max - g.attempts);
}

const KNB = ["камень","ножницы","бумага"];
const KNB_E = { "камень":"🪨", "ножницы":"✂️", "бумага":"📄" };

function playKNB(userChoice) {
    userChoice = userChoice.toLowerCase().trim();
    if (!KNB.includes(userChoice)) return "Пиши: !кнб камень, ножницы или бумага";
    const bot = pick(KNB);
    let result;
    if (userChoice === bot) {
        result = "Ничья! " + KNB_E[userChoice];
    } else if (
        (userChoice === "камень"  && bot === "ножницы") ||
        (userChoice === "ножницы" && bot === "бумага")  ||
        (userChoice === "бумага"  && bot === "камень")
    ) {
        result = "Ты победил! " + KNB_E[userChoice] + " бьёт " + KNB_E[bot] + " 🏆";
    } else {
        result = "Я победил! " + KNB_E[bot] + " бьёт " + KNB_E[userChoice] + " 😈";
    }
    return "✊ КНБ\nТы: " + KNB_E[userChoice] + " " + userChoice + "\nБот: " + KNB_E[bot] + " " + bot + "\n" + result;
}

const HM_WORDS = [
    "РАДУГА","ФЕЙЕРВЕРК","ЕДИНОРОГ","ПИКСЕЛЬ","ПРОГРАММИСТ",
    "КЛАВИАТУРА","ДРАКОН","ГАЛАКТИКА","ПРИКЛЮЧЕНИЕ","ИНТЕРНЕТ"
];

const HM_STAGES = [
    "o\n-----",
    "o\n|\n-----",
    "o\n/|\n-----",
    "o\n/|\\\n-----",
    "o\n/|\\\n/\n-----",
    "o\n/|\\\n/ \\\n-----",
    "x_x\n/|\\\n/ \\\n-----"
];

const hangmanGames = {};

function startHangman(chatId) {
    hangmanGames[chatId] = { word: pick(HM_WORDS), guessed: new Set(), wrong: 0 };
    return renderHangman(chatId);
}

function renderHangman(chatId) {
    const g = hangmanGames[chatId];
    const display = g.word.split("").map(c => g.guessed.has(c) ? c : "_").join(" ");
    const wrong   = [...g.guessed].filter(c => !g.word.includes(c)).join(" ") || "-";
    return "🎭 ВИСЕЛИЦА\n" + HM_STAGES[g.wrong] + "\n" + display + "\nОшибки (" + g.wrong + "/6): " + wrong + "\nПиши букву!";
}

function guessHangman(chatId, letter) {
    const g = hangmanGames[chatId];
    if (!g) return null;
    letter = letter.toUpperCase().trim();
    if (g.guessed.has(letter)) return "Букву " + letter + " уже называл!";
    g.guessed.add(letter);
    const allDone = g.word.split("").every(c => g.guessed.has(c));
    if (allDone) {
        delete hangmanGames[chatId];
        return "🎉 ПОБЕДА! Слово: " + g.word;
    }
    if (!g.word.includes(letter)) {
        g.wrong++;
        if (g.wrong >= 6) {
            const w = g.word;
            delete hangmanGames[chatId];
            return "💀 ПРОИГРЫШ!\n" + HM_STAGES[6] + "\nСлово: " + w + "\n!виселица — снова";
        }
        return "❌ Нет буквы " + letter + "\n" + renderHangman(chatId);
    }
    return "✅ Буква " + letter + " есть!\n" + renderHangman(chatId);
}

function getMagicBall(q) {
    const a = ["Определённо да!","Не рассчитывай...","Спроси позже.","Мои источники — НЕТ.","Однозначно ДА!","Перспективы туманны.","Очень вероятно!","Не могу сказать.","Лучше не знать.","Знаки говорят ДА!","Сомневаюсь.","Без вариантов."];
    return "🎱 Шар отвечает:\n" + (q ? "\"" + q + "\"\n" : "") + pick(a);
}

function getHoroscope() {
    const list = [
        "Овен: не открывай холодильник после 22:00.",
        "Телец: Меркурий в ретро. Не обновляй систему.",
        "Близнецы: встретишь человека с ником из цифр.",
        "Рак: Луна говорит — выпей воды.",
        "Лев: Сатурн за тех, кто ложится до полуночи.",
        "Дева: Юпитер — перестань прокрастинировать.",
        "Весы: звёзды молчат. Это тоже знак.",
        "Скорпион: хороший день ничего не делать.",
        "Стрелец: береги энергию — Козерог завидует.",
        "Козерог: Венера говорит съесть что-то вкусное.",
        "Водолей: Нептун предрекает странный сон.",
        "Рыбы: все планеты за тебя. Твой день."
    ];
    return "🔮 Гороскоп дня\n" + pick(list);
}

function getCompliment() {
    const list = [
        "Ты алмаз в мире стекла! 💎",
        "Если бы интеллект был деньгами — ты богат! 🧠",
        "Ты как wi-fi — все хотят подключиться! 📶",
        "Твоя улыбка заряжает батареи НАСА! ☀️",
        "Настолько крут, что понедельник стесняется! 🌟",
        "Ты — главный персонаж. Остальные NPC. 🎮",
        "Ты как редкий мем — ценный и уникальный! 🐸"
    ];
    return "💌 Комплимент:\n" + pick(list);
}

function getRoast() {
    const list = [
        "Думаешь медленнее Wi-Fi! 🐢",
        "Твоя харизма — как 404. Не найдена. 🙃",
        "Ты как обновление Windows — никто не просил! 💻",
        "IQ и размер обуви — похожие числа! 👟",
        "Как батарея на 2% — вроде живой, толку ноль! 🔋"
    ];
    return "🔥 Роаст (шутя!):\n" + pick(list);
}

function getJoke() {
    const list = [
        "Программист в магазине:\n— Дайте хлеб!\n— Нет хлеба.\n— null pointer exception.",
        "Хэллоуин = Рождество?\nOCT 31 = DEC 25! 🎃🎄",
        "— Не сдал тесты?\n— Папа, я программист. У меня баги, не ошибки!",
        "Жена: если есть яйца — возьми 10 молока.\nОн вернулся с 10 пакетами.",
        "— Как дела?\n— 200 OK\n— Подробнее?\n— 503 Unavailable",
        "Бесконечность — очень длинный for loop! ♾️"
    ];
    return "😂 Анекдот:\n" + pick(list);
}

async function sendHelp(chatId) {
    await fpt.chat.send(chatId,
        rb(6) + "\n" +
        "🌈 RainbowBot ULTRA\n" +
        rb(6) + "\n" +
        "ИГРЫ (1/2):\n" +
        "!кубик — кубик d6\n" +
        "!кубик 20 — кубик d20\n" +
        "!монетка — орёл / решка\n" +
        "!слоты — однорукий бандит\n" +
        "!рулетка — колесо\n" +
        "!угадай — число 1-100\n" +
        "!кнб камень/ножницы/бумага\n" +
        "!виселица — угадай слово\n" +
        "!сдаюсь — выйти из игры"
    );
    await sleep(300);
    await fpt.chat.send(chatId,
        "АНИМАЦИИ И РАЗНОЕ (2/2):\n" +
        "!радуга — радужная анимация\n" +
        "!фейерверк — бах!\n" +
        "!шар вопрос — магический шар\n" +
        "!гороскоп — предсказание дня\n" +
        "!комплимент — позитив\n" +
        "!роаст — огонь (шутя!)\n" +
        "!анекдот — хаха\n" +
        rb(6)
    );
}

fpt.on("onNewMessage", async function(msgData) {
    if (msgData.isMe) return;

    const chatId = msgData.chatId;
    const raw    = (msgData.text || "").trim();
    const text   = raw.toLowerCase();

    if (hangmanGames[chatId] && !text.startsWith("!")) {
        if (/^[а-яёА-ЯЁ]$/.test(raw)) {
            const r = guessHangman(chatId, raw);
            if (r) { await fpt.chat.send(chatId, r); return; }
        }
    }

    if (guessGames[chatId] && !text.startsWith("!")) {
        const n = parseInt(raw, 10);
        if (!isNaN(n) && n >= 1 && n <= 100) {
            const r = guessNumber(chatId, n);
            if (r) { await fpt.chat.send(chatId, r); return; }
        }
    }

    if (text === "!help" || text === "!хелп" || text === "!команды") {
        await sendHelp(chatId); return;
    }

    if (text.startsWith("!кубик") || text.startsWith("!куб") || text.startsWith("!dice")) {
        const parts = text.split(" ");
        let sides = 6;
        if (parts[1]) {
            const p = parseInt(parts[1].replace("d",""), 10);
            if (!isNaN(p) && p >= 2 && p <= 1000) sides = p;
        }
        await animateDice(chatId, sides); return;
    }

    if (text === "!монетка" || text === "!монета" || text === "!coin") {
        await flipCoin(chatId); return;
    }

    if (text === "!слоты" || text === "!slots") {
        await animateSlots(chatId); return;
    }

    if (text.startsWith("!рулетка") || text.startsWith("!рулет")) {
        await animateRoulette(chatId); return;
    }

    if (text === "!радуга" || text === "!rainbow") {
        await animateRainbow(chatId); return;
    }

    if (text === "!фейерверк" || text === "!бах" || text === "!firework") {
        await animateFirework(chatId); return;
    }

    if (text.startsWith("!шар")) {
        await fpt.chat.send(chatId, getMagicBall(raw.substring(4).trim())); return;
    }

    if (text === "!гороскоп" || text === "!horoscope") {
        await fpt.chat.send(chatId, getHoroscope()); return;
    }

    if (text === "!комплимент" || text === "!compliment") {
        await fpt.chat.send(chatId, getCompliment()); return;
    }

    if (text === "!роаст" || text === "!roast") {
        await fpt.chat.send(chatId, getRoast()); return;
    }

    if (text === "!анекдот" || text === "!joke") {
        await fpt.chat.send(chatId, getJoke()); return;
    }

    if (text === "!угадай" || text === "!guess") {
        if (guessGames[chatId]) {
            await fpt.chat.send(chatId, "Игра идёт! Пиши число 1-100.\n!сдаюсь — выйти.");
        } else {
            await fpt.chat.send(chatId, startGuessGame(chatId));
        }
        return;
    }

    if (text === "!сдаюсь" || text === "!сдаться") {
        if (guessGames[chatId]) {
            const s = guessGames[chatId].secret;
            delete guessGames[chatId];
            await fpt.chat.send(chatId, "Сдался! Загадано: " + s + "\n!угадай — снова");
        } else if (hangmanGames[chatId]) {
            const w = hangmanGames[chatId].word;
            delete hangmanGames[chatId];
            await fpt.chat.send(chatId, "Сдался! Слово: " + w + "\n!виселица — снова");
        } else {
            await fpt.chat.send(chatId, "Нет активных игр. !help");
        }
        return;
    }

    if (text.startsWith("!кнб") || text.startsWith("!rps")) {
        const choice = raw.split(" ").slice(1).join(" ").trim();
        await fpt.chat.send(chatId, playKNB(choice)); return;
    }

    if (text === "!виселица" || text === "!hangman") {
        if (hangmanGames[chatId]) {
            await fpt.chat.send(chatId, "Игра идёт!\n" + renderHangman(chatId));
        } else {
            await fpt.chat.send(chatId, startHangman(chatId));
        }
        return;
    }

    if (text.includes("радуга") || text.includes("rainbow")) {
        await fpt.chat.send(chatId, "🌈 " + pick(SP) + " Священное слово! " + pick(SP) + " 🌈");
        return;
    }
});

fpt.app.notify("🌈 RainbowBot ULTRA", "Активирован! Напиши !help в чате.");
fpt.app.log("🌈 RainbowBot ULTRA v6.6.6 — готов!");
