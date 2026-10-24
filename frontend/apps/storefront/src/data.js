export const categories = ["全部", "鞋履", "服饰", "数码", "配饰", "香水"];
const legacyProducts = [
  {
    id: "s01",
    name: "PACE 01 轻量运动鞋",
    en: "PACE / EVERYDAY RUN",
    brand: "LECTUS LAB",
    category: "球鞋",
    price: 699,
    image: "sneaker",
    tag: "本周热选",
    sizes: ["36", "37", "38", "39", "40", "41", "42", "43"],
    colors: ["炽红"],
    sold: 1240,
    description: "轻盈轮廓与明快配色，给日常的每一步多一点能量。",
  },
  {
    id: "b01",
    name: "RIDGE 系带厚底短靴",
    en: "RIDGE / OFF THE ROAD",
    brand: "FIELD OBJECTS",
    category: "户外",
    price: 1280,
    image: "boots",
    tag: "户外灵感",
    sizes: ["36", "37", "38", "39", "40", "41"],
    colors: ["岩棕"],
    sold: 680,
    description: "立体鞋面、系带细节与厚底轮廓，从城市街角走向山野。",
  },
  {
    id: "s02",
    name: "FORM 复古低帮休闲鞋",
    en: "FORM / LOW PROFILE",
    brand: "STUDIO FORM",
    category: "球鞋",
    price: 559,
    image: "shoe",
    tag: "日常之选",
    sizes: ["36", "37", "38", "39", "40", "41", "42"],
    colors: ["砂棕"],
    sold: 910,
    description: "以简洁线条与复古色调，回应不费力的日常搭配。",
  },
  {
    id: "h01",
    name: "QUIET 无线头戴式耳机",
    en: "QUIET / LISTEN IN",
    brand: "QUIET SOUND",
    category: "数码",
    price: 899,
    image: "headphones",
    tag: "声音有形",
    sizes: ["标准版"],
    colors: ["曜石黑"],
    sold: 508,
    description: "把片刻留给音乐。包覆式外观与简洁设计，适配通勤与独处时光。",
  },
  {
    id: "p01",
    name: "DAILY 极简通勤双肩包",
    en: "DAILY / CARRY LESS",
    brand: "ON THE WAY",
    category: "箱包",
    price: 429,
    image: "backpack",
    tag: "随行装备",
    sizes: ["标准款"],
    colors: ["深海蓝"],
    sold: 786,
    description: "干净的包身与实用轮廓，收纳日常，也装下一次临时起意。",
  },
  {
    id: "j01",
    name: "EDGE 短款皮质夹克",
    en: "EDGE / CITY UNIFORM",
    brand: "STUDIO FORM",
    category: "服饰",
    price: 1590,
    image: "jacket",
    tag: "风格新章",
    sizes: ["S", "M", "L", "XL"],
    colors: ["墨黑"],
    sold: 232,
    description: "利落线条与皮质外观，塑造独立而鲜明的城市风格。",
  },
  {
    id: "w01",
    name: "MOMENT 极简石英腕表",
    en: "MOMENT / MAKE TIME",
    brand: "DAILY OBJECTS",
    category: "配饰",
    price: 1080,
    image: "watch",
    tag: "慢一点也好",
    sizes: ["均码"],
    colors: ["棕皮银盘"],
    sold: 349,
    description: "将时间留在腕间，以简洁表盘记录那些细小而值得的瞬间。",
  },
  {
    id: "t01",
    name: "WEEKEND 双人营地帐篷",
    en: "WEEKEND / STAY OUT",
    brand: "FIELD OBJECTS",
    category: "户外",
    price: 899,
    image: "tent",
    tag: "周末计划",
    sizes: ["双人款"],
    colors: ["暖橘"],
    sold: 187,
    description: "把窗外换成森林，给周末换一个坐标。",
  },
  {
    id: "f01",
    name: "BOTANIC 日常护理精华",
    en: "BOTANIC / DAILY RITUAL",
    brand: "BOTANIC STUDIO",
    category: "美妆",
    price: 329,
    image: "beauty",
    tag: "护理日常",
    sizes: ["标准装"],
    colors: ["原色"],
    sold: 542,
    description: "为日常留一段照顾自己的时间，以简洁瓶身装点梳妆台。",
  },
  {
    id: "d01",
    name: "FLOW 随行简约水瓶",
    en: "FLOW / HYDRATE DAILY",
    brand: "DAILY OBJECTS",
    category: "户外",
    price: 189,
    image: "bottle",
    tag: "日常搭子",
    sizes: ["标准款"],
    colors: ["森林绿"],
    sold: 1068,
    description: "从桌边到路上，一件刚刚好的随行日常。",
  },
];
export const products = [
  {
    "id": "wp-kobe",
    "name": "Nike Kobe 5 Protro Dodgers",
    "en": "NIKE / THE PRODUCT EDIT",
    "brand": "NIKE",
    "category": "鞋履",
    "price": 1499,
    "image": "product-kobe",
    "tag": "本期主角",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "道奇蓝"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-asics",
    "name": "ASICS GEL-1130 白银",
    "en": "ASICS / THE PRODUCT EDIT",
    "brand": "ASICS",
    "category": "鞋履",
    "price": 699,
    "image": "product-asics",
    "tag": "复古跑鞋",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "白银"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-samba",
    "name": "adidas Samba OG 白黑",
    "en": "ADIDAS / THE PRODUCT EDIT",
    "brand": "ADIDAS",
    "category": "鞋履",
    "price": 799,
    "image": "product-samba",
    "tag": "经典轮廓",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "白黑"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-jordan",
    "name": "Air Jordan 4 Bred Reimagined",
    "en": "JORDAN / THE PRODUCT EDIT",
    "brand": "JORDAN",
    "category": "鞋履",
    "price": 1399,
    "image": "product-jordan",
    "tag": "篮球档案",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "黑红"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-nb9060",
    "name": "New Balance 9060 海盐",
    "en": "NEW BALANCE / THE PRODUCT EDIT",
    "brand": "NEW BALANCE",
    "category": "鞋履",
    "price": 1099,
    "image": "product-nb9060",
    "tag": "日常之选",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "海盐"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-macbook",
    "name": "Apple MacBook Pro 14 英寸",
    "en": "APPLE / THE PRODUCT EDIT",
    "brand": "APPLE",
    "category": "数码",
    "price": 9999,
    "image": "product-macbook",
    "tag": "随行工作室",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "深空灰"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-shirt-blue",
    "name": "蓝黑格纹长袖衬衫",
    "en": "SELECTED / THE PRODUCT EDIT",
    "brand": "SELECTED",
    "category": "服饰",
    "price": 329,
    "image": "product-shirt-blue",
    "tag": "叠穿日常",
    "sizes": [
      "S",
      "M",
      "L",
      "XL"
    ],
    "colors": [
      "蓝黑格纹"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-ck-one",
    "name": "Calvin Klein CK One 香水",
    "en": "CALVIN KLEIN / THE PRODUCT EDIT",
    "brand": "CALVIN KLEIN",
    "category": "香水",
    "price": 459,
    "image": "product-ck-one",
    "tag": "日常气息",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "图示款"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-dunk",
    "name": "Nike Dunk Low 黑白",
    "en": "NIKE / THE PRODUCT EDIT",
    "brand": "NIKE",
    "category": "鞋履",
    "price": 699,
    "image": "product-dunk",
    "tag": "百搭黑白",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "黑白"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-nb550",
    "name": "New Balance 550 白绿",
    "en": "NEW BALANCE / THE PRODUCT EDIT",
    "brand": "NEW BALANCE",
    "category": "鞋履",
    "price": 759,
    "image": "product-nb550",
    "tag": "复古球场",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "白绿"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-converse",
    "name": "Converse Chuck 70 黑色高帮",
    "en": "CONVERSE / THE PRODUCT EDIT",
    "brand": "CONVERSE",
    "category": "鞋履",
    "price": 569,
    "image": "product-converse",
    "tag": "帆布经典",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "黑色"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-ugg",
    "name": "UGG Tasman 栗色",
    "en": "UGG / THE PRODUCT EDIT",
    "brand": "UGG",
    "category": "鞋履",
    "price": 899,
    "image": "product-ugg",
    "tag": "松弛周末",
    "sizes": [
      "36",
      "37",
      "38",
      "39",
      "40",
      "41",
      "42",
      "43",
      "44"
    ],
    "colors": [
      "栗色"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-matebook",
    "name": "Huawei MateBook X Pro",
    "en": "HUAWEI / THE PRODUCT EDIT",
    "brand": "HUAWEI",
    "category": "数码",
    "price": 8999,
    "image": "product-matebook",
    "tag": "城市随行",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "图示款"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-shirt-red",
    "name": "红黑格纹长袖衬衫",
    "en": "SELECTED / THE PRODUCT EDIT",
    "brand": "SELECTED",
    "category": "服饰",
    "price": 299,
    "image": "product-shirt-red",
    "tag": "秋日色彩",
    "sizes": [
      "S",
      "M",
      "L",
      "XL"
    ],
    "colors": [
      "红黑格纹"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-shirt-floral",
    "name": "蓝色花卉短袖衬衫",
    "en": "SELECTED / THE PRODUCT EDIT",
    "brand": "SELECTED",
    "category": "服饰",
    "price": 259,
    "image": "product-shirt-floral",
    "tag": "假日提案",
    "sizes": [
      "S",
      "M",
      "L",
      "XL"
    ],
    "colors": [
      "蓝色花卉"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-shirt-green",
    "name": "绿灰格纹长袖衬衫",
    "en": "SELECTED / THE PRODUCT EDIT",
    "brand": "SELECTED",
    "category": "服饰",
    "price": 329,
    "image": "product-shirt-green",
    "tag": "轻松叠穿",
    "sizes": [
      "S",
      "M",
      "L",
      "XL"
    ],
    "colors": [
      "绿灰格纹"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-watch-brown",
    "name": "棕色皮带多功能造型腕表",
    "en": "SELECTED / THE PRODUCT EDIT",
    "brand": "SELECTED",
    "category": "配饰",
    "price": 1080,
    "image": "product-watch-brown",
    "tag": "腕间日常",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "棕皮白盘"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-watch-rolex",
    "name": "Rolex Submariner 腕表",
    "en": "ROLEX / THE PRODUCT EDIT",
    "brand": "ROLEX",
    "category": "配饰",
    "price": 89900,
    "image": "product-watch-rolex",
    "tag": "时间之物",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "图示款"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-chanel",
    "name": "CHANEL COCO NOIR 香水",
    "en": "CHANEL / THE PRODUCT EDIT",
    "brand": "CHANEL",
    "category": "香水",
    "price": 1099,
    "image": "product-chanel",
    "tag": "夜色气息",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "图示款"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  },
  {
    "id": "wp-dior",
    "name": "DIOR J'adore 香水",
    "en": "DIOR / THE PRODUCT EDIT",
    "brand": "DIOR",
    "category": "香水",
    "price": 899,
    "image": "product-dior",
    "tag": "香气之选",
    "sizes": [
      "图示款"
    ],
    "colors": [
      "图示款"
    ],
    "sold": 0,
    "description": "查看商品图片、配色与规格，选择适合你的款式。"
  }
];
export const allProducts = [...products, ...legacyProducts];
export const getProduct = (id) => allProducts.find((p) => p.id === id);
export const photo = (name) => `${import.meta.env.BASE_URL}images/${name}.webp`;
export const posts = [
  {
    id: "n1",
    author: "阿屿 / YU",
    avatar: "YU",
    title: "把周末的坐标，放在山里。",
    text: "走过的路，比收藏夹里的风景更真实。今天的山风刚刚好。",
    image: "hiker",
    likes: 286,
    topic: "周末去野",
    product: "b01",
  },
  {
    id: "n2",
    author: "Nora的日常",
    avatar: "N",
    title: "黑色，是我永远的舒适区。",
    text: "一件夹克，几种不同的自己。穿搭的答案，应该由你自己来写。",
    image: "jacket",
    likes: 143,
    topic: "今日穿搭",
    product: "j01",
  },
  {
    id: "n3",
    author: "WALK STUDIO",
    avatar: "W",
    title: "日常也需要一点红。",
    text: "从鞋子开始，给普通的一天一点不普通的配色。",
    image: "sneaker",
    likes: 391,
    topic: "球鞋日记",
    product: "s01",
  },
  {
    id: "n4",
    author: "未末 / Momo",
    avatar: "M",
    title: "一顶帐篷，换一个房间。",
    text: "森林是这周的新邻居。带上热爱，轻装出发。",
    image: "tent",
    likes: 168,
    topic: "露营生活",
    product: "t01",
  },
];
export const coupons = [
  {
    id: "welcome",
    name: "新朋友见面礼",
    amount: 50,
    minimum: 500,
    desc: "满 ¥500 可用",
  },
  {
    id: "daily",
    name: "日常好物礼",
    amount: 20,
    minimum: 200,
    desc: "满 ¥200 可用",
  },
];
export const demoNotice = "商品信息与服务说明";
