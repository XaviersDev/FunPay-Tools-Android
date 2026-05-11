// @name 🌈 RainbowBot ULTRA
// @author XaviersDev
// @version 6.6.6
// @description Полноценный чатбот: ASCII-анимации, мини-игры, радуга, кубик, рулетка, змейка и ещё куча всего. Просто напиши !help в чате.
// @banner https://www.freeiconspng.com/uploads/rainbow-png-16.png









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





const RAINBOW_EMOJIS = ["🔴","🟠","🟡","🟢","🔵","🟣","🟤"];
const SPARKLES       = ["✨","💫","⭐","🌟","💥","🎆","🎇"];
const HEARTS         = ["❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎"];

function rainbowLine(text) {
    
    const emojis = RAINBOW_EMOJIS;
    let result = "";
    let i = 0;
    for (const ch of text) {
        if (ch === " ") { result += " "; continue; }
        result += emojis[i % emojis.length] + ch;
        i++;
    }
    return result;
}

function rainbowBorder(width) {
    let line = "";
    for (let i = 0; i < width; i++) {
        line += RAINBOW_EMOJIS[i % RAINBOW_EMOJIS.length];
    }
    return line;
}





const ASCII_RAINBOW = [
    "    .-\"\"\"\"\"-.    ",
    "  .'  R O Y  '.  ",
    " /  G B I V W  \\ ",
    "|   🌈🌈🌈🌈   |",
    " \\   P U R P   / ",
    "  '.         .'  ",
    "    '-.....-'    "
].join("\n");

const ASCII_DICE = {
    1: [
        "┌─────────┐",
        "│         │",
        "│    ●    │",
        "│         │",
        "└─────────┘"
    ],
    2: [
        "┌─────────┐",
        "│  ●      │",
        "│         │",
        "│      ●  │",
        "└─────────┘"
    ],
    3: [
        "┌─────────┐",
        "│  ●      │",
        "│    ●    │",
        "│      ●  │",
        "└─────────┘"
    ],
    4: [
        "┌─────────┐",
        "│  ●   ●  │",
        "│         │",
        "│  ●   ●  │",
        "└─────────┘"
    ],
    5: [
        "┌─────────┐",
        "│  ●   ●  │",
        "│    ●    │",
        "│  ●   ●  │",
        "└─────────┘"
    ],
    6: [
        "┌─────────┐",
        "│  ●   ●  │",
        "│  ●   ●  │",
        "│  ●   ●  │",
        "└─────────┘"
    ]
};

const ASCII_SLOTS_SYMBOLS = ["🍒","🍋","🍊","🍇","⭐","💎","🔔","🍀"];

const ASCII_FIREWORK_FRAMES = [
    
    [
        "          ",
        "          ",
        "          ",
        "    |     ",
        "    |     ",
        "   \\|/    ",
        "  --*--   ",
        "   /|\\    "
    ],
    
    [
        "   * * *  ",
        "  *     * ",
        " *   ✦   *",
        "  *     * ",
        "   * * *  ",
        "    |||   ",
        "     |    ",
        "          "
    ],
    
    [
        " ✨ 💥 ✨ ",
        "💥  🌟  💥",
        "✨  🎆  ✨",
        "💥  🌟  💥",
        " ✨ 💥 ✨ ",
        "          ",
        "          ",
        "          "
    ]
];





async function animateFirework(chatId) {
    await fpt.chat.send(chatId, "🚀 Запускаем фейерверк...");
    await sleep(600);
    for (const frame of ASCII_FIREWORK_FRAMES) {
        const msg = "```\n" + frame.join("\n") + "\n```";
        await fpt.chat.send(chatId, msg);
        await sleep(700);
    }
    await fpt.chat.send(chatId, rainbowBorder(7) + "\n🎆 БА-БАХ! 🎆\n" + rainbowBorder(7));
}

async function animateRainbow(chatId) {
    const frames = [
        "🔴                    ",
        "🔴🟠                  ",
        "🔴🟠🟡                ",
        "🔴🟠🟡🟢              ",
        "🔴🟠🟡🟢🔵            ",
        "🔴🟠🟡🟢🔵🟣          ",
        "🔴🟠🟡🟢🔵🟣✨        ",
        "🔴🟠🟡🟢🔵🟣✨🌈      ",
        "🌈✨🟣🔵🟢🟡🟠🔴✨🌈  "
    ];
    for (const f of frames) {
        await fpt.chat.send(chatId, f);
        await sleep(300);
    }
    await fpt.chat.send(chatId, "🌈 РАДУГА АКТИВИРОВАНА! Мир стал лучше! 🌈");
}

async function animateDiceRoll(chatId, sides) {
    sides = sides || 6;
    
    const rollFrames = ["⚀","⚁","⚂","⚃","⚄","⚅"];
    let rollMsg = "🎲 Бросаю кубик";
    await fpt.chat.send(chatId, rollMsg + "...");
    await sleep(400);

    
    const shuffleSteps = 5;
    for (let i = 0; i < shuffleSteps; i++) {
        const tempVal = rand(1, 6);
        const lines = ASCII_DICE[tempVal];
        await fpt.chat.send(chatId, "```\n" + lines.join("\n") + "\n```");
        await sleep(300);
    }

    const result = rand(1, sides);
    
    if (sides === 6) {
        const finalLines = ASCII_DICE[result];
        await fpt.chat.send(chatId,
            "```\n" + finalLines.join("\n") + "\n```\n" +
            "🎲 Выпало: **" + result + "**! " + pick(["Удача!", "Судьба решила!", "Роком помечено!", "Фортуна улыбнулась!"])
        );
    } else {
        await fpt.chat.send(chatId,
            "🎲 d" + sides + " → **" + result + "**! " + pick(["Удача!", "Рок!", "Судьба!"])
        );
    }
}

async function animateSlots(chatId) {
    await fpt.chat.send(chatId, "🎰 Запускаю слоты...");
    await sleep(500);

    const sym = ASCII_SLOTS_SYMBOLS;
    
    for (let i = 0; i < 4; i++) {
        const s1 = pick(sym), s2 = pick(sym), s3 = pick(sym);
        const frame =
            "┌───┬───┬───┐\n" +
            "│ " + s1 + " │ " + s2 + " │ " + s3 + " │\n" +
            "└───┴───┴───┘";
        await fpt.chat.send(chatId, "```\n" + frame + "\n```");
        await sleep(400);
    }

    
    const r1 = pick(sym), r2 = pick(sym), r3 = pick(sym);
    const finalFrame =
        "╔═══╦═══╦═══╗\n" +
        "║ " + r1 + " ║ " + r2 + " ║ " + r3 + " ║\n" +
        "╚═══╩═══╩═══╝";
    await fpt.chat.send(chatId, "```\n" + finalFrame + "\n```");

    if (r1 === r2 && r2 === r3) {
        await fpt.chat.send(chatId, "🎉💎 ДЖЕКПОТ!!! " + r1 + r2 + r3 + " 💎🎉\nТы выиграл... уважение окружающих!");
        await animateFirework(chatId);
    } else if (r1 === r2 || r2 === r3 || r1 === r3) {
        await fpt.chat.send(chatId, "✨ Два одинаковых! Почти джекпот, но не совсем 😏");
    } else {
        await fpt.chat.send(chatId, "😅 Ничего... В следующий раз повезёт! Напиши !слоты снова.");
    }
}






const guessGames = {};

function startGuessGame(chatId) {
    const secret = rand(1, 100);
    guessGames[chatId] = { secret: secret, attempts: 0, maxAttempts: 7 };
    return (
        "🎯 УГАДАЙ ЧИСЛО!\n" +
        "────────────────\n" +
        "Я загадал число от 1 до 100.\n" +
        "У тебя 7 попыток. Напиши число в чат!\n" +
        "Чтобы сдаться — напиши !сдаюсь\n" +
        "────────────────\n" +
        "[░░░░░░░] 0/7 попыток"
    );
}

function makeProgressBar(current, max) {
    const filled = Math.round((current / max) * 7);
    return "[" + "█".repeat(filled) + "░".repeat(7 - filled) + "] " + current + "/" + max;
}

function guessNumber(chatId, num) {
    const game = guessGames[chatId];
    if (!game) return null;

    game.attempts++;
    const bar = makeProgressBar(game.attempts, game.maxAttempts);

    if (num === game.secret) {
        delete guessGames[chatId];
        return (
            "🎉 ПРАВИЛЬНО! Загаданное число: **" + num + "**!\n" +
            bar + "\n" +
            pick(["Ты гений!", "Мастер угадывания!", "Невероятно!", "Телепат!!"]) + " 🏆"
        );
    }

    if (game.attempts >= game.maxAttempts) {
        const secret = game.secret;
        delete guessGames[chatId];
        return (
            "💀 Попытки закончились!\n" +
            "Загаданное число было: **" + secret + "**\n" +
            bar + "\n" +
            "Напиши !угадай чтобы сыграть снова!"
        );
    }

    const hint = num < game.secret
        ? "📈 Больше! ↑↑↑"
        : "📉 Меньше! ↓↓↓";
    const remaining = game.maxAttempts - game.attempts;

    return hint + "\n" + bar + "\nОсталось попыток: " + remaining;
}





const KNB_CHOICES = ["камень", "ножницы", "бумага"];
const KNB_EMOJI   = { "камень": "🪨", "ножницы": "✂️", "бумага": "📄" };

const KNB_ASCII = {
    "камень":  ["    _____  ", "   /     \\ ", "  | (o)(o)|", "   \\     / ", "    \\___/  "],
    "ножницы": ["  __   __  ", " /  \\ /  \\ ", "|    X    |", " \\  / \\  / ", "  \\/   \\/  "],
    "бумага":  ["  ______   ", " /      \\  ", "|  ~~~~  | ", "|  ~~~~  | ", " \\______/  "]
};

function playKNB(chatId, userChoice) {
    userChoice = userChoice.toLowerCase().trim();
    if (!KNB_CHOICES.includes(userChoice)) {
        return "❓ Скажи: !кнб камень, !кнб ножницы или !кнб бумага";
    }

    const botChoice = pick(KNB_CHOICES);
    const userArt = KNB_ASCII[userChoice].join("\n");
    const botArt  = KNB_ASCII[botChoice].join("\n");

    let result;
    if (userChoice === botChoice) {
        result = "🤝 НИЧЬЯ! Мы оба " + KNB_EMOJI[userChoice];
    } else if (
        (userChoice === "камень"  && botChoice === "ножницы") ||
        (userChoice === "ножницы" && botChoice === "бумага")  ||
        (userChoice === "бумага"  && botChoice === "камень")
    ) {
        result = "🏆 ТЫ ПОБЕДИЛ! " + KNB_EMOJI[userChoice] + " бьёт " + KNB_EMOJI[botChoice];
    } else {
        result = "😈 Я ПОБЕДИЛ! " + KNB_EMOJI[botChoice] + " бьёт " + KNB_EMOJI[userChoice];
    }

    return (
        "✊ КНБ ФАЙТ!\n" +
        "━━━━━━━━━━━━━━\n" +
        "ТЫ:\n```\n" + userArt + "\n```\n" +
        "БОТ:\n```\n" + botArt + "\n```\n" +
        "━━━━━━━━━━━━━━\n" +
        result
    );
}





const COIN_ORLYOL = [
    "   _____   ",
    "  /  👑 \\  ",
    " | ОРЁЛ  | ",
    "  \\ ____ / ",
    "   ‾‾‾‾‾   "
];
const COIN_RESHKA = [
    "   _____   ",
    "  / \\|/ \\  ",
    " |РЕШКА  | ",
    "  \\  |   / ",
    "   ‾‾‾‾‾   "
];

async function flipCoin(chatId) {
    await fpt.chat.send(chatId, "🪙 Подбрасываю монетку...");
    await sleep(400);

    
    const spinFrames = ["🌀","💫","✨","🌀","💫","✨"];
    for (const f of spinFrames) {
        await fpt.chat.send(chatId, f + " крутится... " + f);
        await sleep(250);
    }

    const isOrlyol = Math.random() < 0.5;
    const art = isOrlyol ? COIN_ORLYOL : COIN_RESHKA;
    const name = isOrlyol ? "ОРЁЛ 👑" : "РЕШКА ⚡";

    await fpt.chat.send(chatId,
        "```\n" + art.join("\n") + "\n```\n" +
        "🪙 Выпало: **" + name + "**!"
    );
}





const HANGMAN_WORDS = [
    "РАДУГА","ФЕЙЕРВЕРК","ЕДИНОРОГ","ПИКСЕЛЬ","ПРОГРАММИСТ",
    "КЛАВИАТУРА","ИНТЕРНЕТ","ДРАКОН","ПРИКЛЮЧЕНИЕ","ГАЛАКТИКА"
];

const HANGMAN_STAGES = [
    
    ["  ┌───┐ ", "  │   │ ", "  │     ", "  │     ", "  │     ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │     ", "  │     ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │   │ ", "  │     ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │  /│ ", "  │     ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │  /│\\", "  │     ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │  /│\\", "  │  /  ", "  │     ", "──┴──   "],
    
    ["  ┌───┐ ", "  │   │ ", "  │   O ", "  │  /│\\", "  │  / \\", "  │     ", "──┴──   "]
];

const hangmanGames = {};

function startHangman(chatId) {
    const word = pick(HANGMAN_WORDS);
    hangmanGames[chatId] = {
        word: word,
        guessed: new Set(),
        wrong: 0,
        maxWrong: 6
    };
    return renderHangman(chatId);
}

function renderHangman(chatId) {
    const g = hangmanGames[chatId];
    const stage = HANGMAN_STAGES[g.wrong];
    const display = g.word.split("").map(ch => g.guessed.has(ch) ? ch : "_").join(" ");
    const wrongLetters = [...g.guessed].filter(ch => !g.word.includes(ch)).join(" ") || "—";
    return (
        "🎭 ВИСЕЛИЦА\n" +
        "```\n" + stage.join("\n") + "\n```\n" +
        "Слово: **" + display + "**\n" +
        "Ошибки (" + g.wrong + "/" + g.maxWrong + "): " + wrongLetters + "\n" +
        "Напиши букву (русскую)!"
    );
}

function guessHangman(chatId, letter) {
    const g = hangmanGames[chatId];
    if (!g) return null;

    letter = letter.toUpperCase().trim();
    if (letter.length !== 1) return "❓ Одна буква, пожалуйста!";
    if (g.guessed.has(letter)) return "♻️ Букву «" + letter + "» ты уже называл!";

    g.guessed.add(letter);

    const allGuessed = g.word.split("").every(ch => g.guessed.has(ch));
    if (allGuessed) {
        delete hangmanGames[chatId];
        return "🎉 ПОБЕДА! Слово: **" + g.word + "**\n🏆 Виселица осталась пустой!";
    }

    if (!g.word.includes(letter)) {
        g.wrong++;
        if (g.wrong >= g.maxWrong) {
            const word = g.word;
            delete hangmanGames[chatId];
            return (
                "💀 ПРОИГРЫШ!\n" +
                "```\n" + HANGMAN_STAGES[6].join("\n") + "\n```\n" +
                "Слово было: **" + word + "**\nПопробуй снова — !виселица"
            );
        }
        return "❌ Нет буквы «" + letter + "»!\n" + renderHangman(chatId);
    }

    return "✅ Буква «" + letter + "» есть!\n" + renderHangman(chatId);
}





function getHoroscope(chatId) {
    const signs = [
        { name: "♈ Овен",     text: "Сегодня звёзды говорят: не открывай холодильник после 22:00." },
        { name: "♉ Телец",    text: "Меркурий в ретрограде. Не обновляй систему." },
        { name: "♊ Близнецы", text: "Ты встретишь человека с ником из цифр. Это судьба." },
        { name: "♋ Рак",      text: "Луна говорит: выпей воды. Ты явно не пил сегодня." },
        { name: "♌ Лев",      text: "Сатурн благоволит тем, кто ляжет спать до полуночи." },
        { name: "♍ Дева",     text: "Юпитер шепчет: перестань прокрастинировать." },
        { name: "♎ Весы",     text: "Звёзды молчат. Это тоже знак." },
        { name: "♏ Скорпион", text: "Марс в огне! Хороший день чтобы ничего не делать." },
        { name: "♐ Стрелец",  text: "Козерог завидует твоей энергии. Береги её." },
        { name: "♑ Козерог",  text: "Венера говорит: съешь что-нибудь вкусное." },
        { name: "♒ Водолей",  text: "Нептун предрекает странный сон. Не пугайся." },
        { name: "♓ Рыбы",     text: "Все планеты за тебя. Сегодня твой день." }
    ];
    const s = pick(signs);
    return "🔮 ГОРОСКОП ДНЯ\n" + s.name + "\n━━━━━━━━━━━━━\n" + s.text + "\n━━━━━━━━━━━━━\n✨ Удачи!";
}

function getMagicBall(question) {
    const answers = [
        "🎱 Определённо да!",
        "🎱 Не рассчитывай на это...",
        "🎱 Спроси позже.",
        "🎱 Мои источники говорят НЕТ.",
        "🎱 Однозначно ДА!",
        "🎱 Перспективы туманны.",
        "🎱 Очень вероятно!",
        "🎱 Не могу сказать сейчас.",
        "🎱 Лучше не знать.",
        "🎱 Знаки указывают на ДА!",
        "🎱 Сомневаюсь.",
        "🎱 Без вариантов."
    ];
    return (
        "🔮 Магический шар отвечает:\n" +
        "━━━━━━━━━━━━━\n" +
        "Вопрос: \"" + (question || "?") + "\"\n" +
        "━━━━━━━━━━━━━\n" +
        pick(answers)
    );
}

function getCompliment() {
    const compliments = [
        "Ты настоящий алмаз в мире стекла! 💎",
        "Если бы интеллект был валютой, ты бы был миллиардером! 🧠",
        "С тобой даже понедельник становится лучше! 🌟",
        "Ты как wi-fi — все хотят подключиться! 📶",
        "Твоя улыбка заряжает солнечные батареи НАСА! ☀️",
        "Ты настолько крутой, что тебе завидует Arctic Monkeys! 🐒",
        "Если бы было общество по защите крутых людей — ты бы его возглавил! 👑",
        "Ты как редкий мем — ценный и уникальный! 🐸",
        "С тобой коты становятся добрее, а понедельники — короче! 😸",
        "Ты — главный персонаж. Остальные просто NPC! 🎮"
    ];
    return "💌 КОМПЛИМЕНТ ДНЯ:\n" + pick(compliments);
}

function getRoast() {
    const roasts = [
        "Ты настолько медленно думаешь, что Wi-Fi передаёт данные быстрее! 🐢",
        "Твоя харизма — как 404 страница. Не найдена. 🙃",
        "Если бы глупость была достоинством, ты бы получил Нобелевку! 🏆",
        "Ты как батарея на 2% — вроде живой, но толку ноль! 🔋",
        "Единственная вещь острее тебя — это тупой нож! 🔪",
        "Твой IQ и размер обуви — похожие числа! 👟",
        "Ты как обновление Windows — никто не просил, но вот ты здесь! 💻",
        "Твоя логика — загадка даже для ChatGPT! 🤖"
    ];
    return "🔥 РОАСТ (шутя!):\n" + pick(roasts);
}

function getJoke() {
    const jokes = [
        "Программист зашёл в магазин.\n— Дайте мне хлеб!\n— Нет хлеба.\n— А-а-а, *null pointer exception*.",
        "Почему программисты путают Хэллоуин и Рождество?\nПотому что OCT 31 = DEC 25! 🎃🎄",
        "— Сынок, ты опять не сдал ни одного теста?\n— Папа, я программист! У меня баги, а не ошибки!",
        "Жена говорит мужу-программисту:\n— Сходи в магазин, купи молоко. Если есть яйца — возьми 10.\nОн вернулся с 10 пакетами молока.",
        "— Как дела?\n— 200 OK\n— А подробнее?\n— 503 Service Unavailable",
        "Бесконечность — это просто очень длинный for loop! ♾️",
        "Почему у скелета нет друзей?\nПотому что он всех задолбал нытьём! 💀",
        "— Алло, это техподдержка?\n— Да.\n— У меня всё сломалось!\n— Вы пробовали выключить и включить?\n— Нет...\n— С Новым годом!"
    ];
    return "😂 АНЕКДОТ:\n━━━━━━━━\n" + pick(jokes);
}





async function animateRoulette(chatId, bet) {
    await fpt.chat.send(chatId, "🎡 Колесо крутится...");
    await sleep(500);

    const slots = ["🔴","⚫","🟢","🔴","⚫","🔴","⚫","🟢","🔴","⚫"];
    const frames = 6;
    let offset = 0;
    for (let i = 0; i < frames; i++) {
        const visible = [];
        for (let j = 0; j < 5; j++) {
            visible.push(slots[(offset + j) % slots.length]);
        }
        const frame = "│" + visible.join("│") + "│\n" +
                      " ▲▲▲▲▲▲▲▲▲\n" +
                      "     ★     ";
        await fpt.chat.send(chatId, "```\n" + frame + "\n```");
        offset = (offset + rand(1, 3)) % slots.length;
        await sleep(350 + i * 80);
    }

    const num   = rand(0, 36);
    const color = num === 0 ? "🟢" : (num % 2 === 0 ? "🔴" : "⚫");
    const colorName = num === 0 ? "Зеро" : (num % 2 === 0 ? "Красное" : "Чёрное");

    await fpt.chat.send(chatId,
        "🎡 РУЛЕТКА ОСТАНОВИЛАСЬ!\n" +
        "━━━━━━━━━━━━━━━━━━\n" +
        "Число: **" + num + "** " + color + "\n" +
        "Цвет: " + colorName + "\n" +
        "━━━━━━━━━━━━━━━━━━\n" +
        (num === 0 ? "🟢 ЗЕРО! Казино всегда побеждает!" :
            "Удача — понятие относительное 😏")
    );
}





function getHelp() {
    return (
        rainbowBorder(5) + "\n" +
        "🌈 RAINBOWBOT ULTRA v6.6.6\n" +
        rainbowBorder(5) + "\n\n" +

        "🎲 ИГРЫ:\n" +
        "  !кубик [стороны] — бросить кубик (d6, d20, d100...)\n" +
        "  !монетка — орёл или решка с анимацией\n" +
        "  !слоты — однорукий бандит\n" +
        "  !рулетка — крутим колесо\n" +
        "  !угадай — угадай число (1-100, 7 попыток)\n" +
        "  !кнб [камень/ножницы/бумага] — камень-ножницы-бумага\n" +
        "  !виселица — угадай слово по буквам\n\n" +

        "🎨 АНИМАЦИИ:\n" +
        "  !радуга — ASCII радужная анимация\n" +
        "  !фейерверк — бах! 🎆\n\n" +

        "🔮 РАЗНОЕ:\n" +
        "  !шар [вопрос] — магический шар\n" +
        "  !гороскоп — предсказание дня\n" +
        "  !комплимент — получи заряд позитива\n" +
        "  !роаст — огонь (шуточно!)\n" +
        "  !анекдот — смеёмся вместе\n" +
        "  !help — это меню\n\n" +

        rainbowBorder(5) + "\n" +
        "Удачи! " + pick(SPARKLES)
    );
}





fpt.on("onNewMessage", async function(msgData) {
    
    if (msgData.isMe) return;

    const chatId = msgData.chatId;
    const raw    = (msgData.text || "").trim();
    const text   = raw.toLowerCase();

    
    if (hangmanGames[chatId] && !text.startsWith("!")) {
        if (/^[а-яёА-ЯЁ]$/.test(raw)) {
            const resp = guessHangman(chatId, raw);
            if (resp) { await fpt.chat.send(chatId, resp); return; }
        }
    }

    
    if (guessGames[chatId] && !text.startsWith("!")) {
        const num = parseInt(raw, 10);
        if (!isNaN(num) && num >= 1 && num <= 100) {
            const resp = guessNumber(chatId, num);
            if (resp) { await fpt.chat.send(chatId, resp); return; }
        }
    }

    

    if (text === "!help" || text === "!хелп" || text === "!команды") {
        await fpt.chat.send(chatId, getHelp());
        return;
    }

    
    if (text.startsWith("!кубик") || text.startsWith("!куб") || text.startsWith("!dice")) {
        const parts = text.split(" ");
        let sides = 6;
        if (parts[1]) {
            const parsed = parseInt(parts[1].replace("d",""), 10);
            if (!isNaN(parsed) && parsed >= 2 && parsed <= 1000) sides = parsed;
        }
        await animateDiceRoll(chatId, sides);
        return;
    }

    
    if (text === "!монетка" || text === "!монета" || text === "!coin") {
        await flipCoin(chatId);
        return;
    }

    
    if (text === "!слоты" || text === "!slot" || text === "!slots") {
        await animateSlots(chatId);
        return;
    }

    
    if (text.startsWith("!рулетка") || text.startsWith("!рулет")) {
        await animateRoulette(chatId, null);
        return;
    }

    
    if (text === "!радуга" || text === "!rainbow" || text === "!рейнбоу") {
        await animateRainbow(chatId);
        return;
    }

    
    if (text === "!фейерверк" || text === "!бах" || text === "!firework") {
        await animateFirework(chatId);
        return;
    }

    
    if (text.startsWith("!шар")) {
        const question = raw.substring(4).trim();
        await fpt.chat.send(chatId, getMagicBall(question));
        return;
    }

    
    if (text === "!гороскоп" || text === "!horoscope") {
        await fpt.chat.send(chatId, getHoroscope(chatId));
        return;
    }

    
    if (text === "!комплимент" || text === "!compliment" || text === "!похвала") {
        await fpt.chat.send(chatId, getCompliment());
        return;
    }

    
    if (text === "!роаст" || text === "!roast" || text === "!обидь") {
        await fpt.chat.send(chatId, getRoast());
        return;
    }

    
    if (text === "!анекдот" || text === "!joke" || text === "!смешно") {
        await fpt.chat.send(chatId, getJoke());
        return;
    }

    
    if (text === "!угадай" || text === "!guess") {
        if (guessGames[chatId]) {
            await fpt.chat.send(chatId, "⚠️ Игра уже идёт! Напиши число от 1 до 100. (!сдаюсь чтобы выйти)");
        } else {
            await fpt.chat.send(chatId, startGuessGame(chatId));
        }
        return;
    }

    
    if (text === "!сдаюсь" || text === "!сдаться") {
        if (guessGames[chatId]) {
            const secret = guessGames[chatId].secret;
            delete guessGames[chatId];
            await fpt.chat.send(chatId, "🏳️ Сдался! Загаданное число было: **" + secret + "**\nНапиши !угадай чтобы сыграть снова.");
        } else if (hangmanGames[chatId]) {
            const word = hangmanGames[chatId].word;
            delete hangmanGames[chatId];
            await fpt.chat.send(chatId, "🏳️ Сдался! Слово было: **" + word + "**\nНапиши !виселица чтобы сыграть снова.");
        } else {
            await fpt.chat.send(chatId, "😅 Нет активных игр. Попробуй !help");
        }
        return;
    }

    
    if (text.startsWith("!кнб") || text.startsWith("!rps")) {
        const choice = raw.split(" ").slice(1).join(" ").trim();
        await fpt.chat.send(chatId, playKNB(chatId, choice));
        return;
    }

    
    if (text === "!виселица" || text === "!hangman") {
        if (hangmanGames[chatId]) {
            await fpt.chat.send(chatId, "⚠️ Игра уже идёт!\n" + renderHangman(chatId));
        } else {
            await fpt.chat.send(chatId, startHangman(chatId));
        }
        return;
    }

    
    if (text.includes("радуга") || text.includes("rainbow")) {
        await fpt.chat.send(chatId, "🌈 " + pick(SPARKLES) + " Ты произнёс священное слово! " + pick(SPARKLES) + " 🌈");
        return;
    }
});





fpt.app.notify(
    "🌈 RainbowBot ULTRA",
    "Активирован! Напиши !help в любом чате."
);
fpt.app.log("🌈 RainbowBot ULTRA v6.6.6 — готов к бою!");
