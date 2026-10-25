package com.aburv.kurippidu.data.progress

enum class FoodTag { FRIED, SWEET, DRINK, WATER }

/**
 * One food with rough values per 100 g (or 100 ml for drinks).
 *
 * [servingG] is a normal helping when no amount is written ("lemon rice"),
 * [pieceG] is one piece when a count is written ("3 parota"). Values are
 * typical home/restaurant numbers rounded from public food tables; they are
 * estimates and the app always says so.
 */
data class Food(
    val name: String,
    val aliases: List<String>,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val servingG: Double,
    val pieceG: Double = servingG,
    val tags: Set<FoodTag> = emptySet()
)

object IndianFoodTable {

    private val F = FoodTag.FRIED
    private val S = FoodTag.SWEET
    private val D = FoodTag.DRINK

    private fun food(
        name: String, aliases: String, kcal: Number, protein: Number, serving: Number,
        piece: Number? = null, vararg tags: FoodTag
    ) = Food(
        name, (listOf(name) + aliases.split("|")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
        kcal.toDouble(), protein.toDouble(), serving.toDouble(), (piece ?: serving).toDouble(), tags.toSet()
    )

    val foods: List<Food> = listOf(
        // ── Rice and meals ──
        food("Rice", "white rice|sadham|saadam|chawal|plain rice|steamed rice", 130, 2.7, 150),
        food("Lemon rice", "elumichai sadam|chitranna", 175, 3, 150),
        food("Curd rice", "thayir sadam|dahi rice|thayir sadham", 120, 3.5, 200),
        food("Tamarind rice", "puliyodarai|puli sadam|pulihora", 180, 3, 150),
        food("Sambar rice", "sambar sadam|bisibelebath|bisi bele bath", 130, 4, 250),
        food("Chicken biryani", "biryani|biriyani|chicken biriyani", 180, 8, 250),
        food("Mutton biryani", "mutton biriyani", 200, 9, 250),
        food("Veg biryani", "veg biriyani|vegetable biryani", 160, 4, 250),
        food("Chicken rice", "chicken fried rice", 170, 6, 200, null, F),
        food("Fried rice", "veg fried rice|egg fried rice", 165, 4, 200, null, F),
        food("Pulao", "pulav|ghee rice|nei sadam", 160, 3.5, 200),
        food("Khichdi", "kichdi", 120, 4.5, 250),

        // ── Tiffin ──
        food("Idli", "idly|iddli", 135, 4, 120, 40),
        food("Dosa", "dosai|thosai|plain dosa", 165, 4, 160, 80),
        food("Masala dosa", "masal dosa|masala dosai", 180, 4, 180, 180),
        food("Ghee roast", "ghee dosa|roast", 210, 4, 120, 120, F),
        food("Rava dosa", "rava dosai", 190, 4, 150, 100),
        food("Uttapam", "oothappam|uthappam|uttappam", 160, 4, 200, 100),
        food("Pongal", "ven pongal|khara pongal", 150, 4, 200),
        food("Sweet pongal", "sakkarai pongal|chakkara pongal", 240, 4, 150, null, S),
        food("Upma", "uppuma|rava upma|kichadi", 150, 3.5, 180),
        food("Poori", "puri|poori masala", 330, 6, 90, 30, F),
        food("Chapati", "chapathi|roti|phulka|chappathi", 300, 9, 80, 40),
        food("Parotta", "parota|porotta|barotta|paratha|kothu parotta|kothu|veechu", 320, 7, 160, 80, F),
        food("Naan", "butter naan|garlic naan", 290, 9, 90, 90),
        food("Appam", "aappam", 120, 2, 120, 60),
        food("Idiyappam", "string hoppers|sevai|nool puttu", 130, 2, 150, 50),
        food("Puttu", "pittu", 170, 3.5, 150),
        food("Adai", "adai dosa", 180, 7, 150, 100),
        food("Pesarattu", "moong dosa", 160, 8, 150, 100),
        food("Bread", "bread slice|toast|brown bread", 265, 9, 50, 25),
        food("Bread omelette", "bread omlet", 230, 11, 150, 150),
        food("Oats", "oatmeal|oats porridge|kanji", 380, 13, 40),
        food("Cornflakes", "corn flakes|muesli|cereal", 380, 7, 40),

        // ── Sides and curries ──
        food("Sambar", "sambhar|sambaar", 60, 3, 150),
        food("Rasam", "saaru|chaaru", 25, 1, 150),
        food("Dal", "dhal|paruppu|dal tadka|dal fry", 110, 6, 150),
        food("Kurma", "korma|veg kurma|gravy|salna|saalna", 120, 3, 100),
        food("Chutney", "coconut chutney|thengai chutney|tomato chutney|kara chutney", 150, 3, 40),
        food("Poriyal", "thoran|vegetable poriyal|cabbage poriyal|beans poriyal|carrot poriyal", 80, 2, 100),
        food("Beetroot poriyal", "beetroot|beet root", 60, 1.6, 100),
        food("Potato fry", "urulai roast|aloo fry|potato roast|potato", 150, 2, 100, null, F),
        food("Avial", "aviyal", 90, 2, 150),
        food("Kootu", "koottu", 90, 3, 150),
        food("Keerai", "spinach|palak|greens|keerai masiyal", 50, 3, 100),
        food("Paneer", "paneer butter masala|paneer curry|palak paneer|kadai paneer", 265, 18, 100),
        food("Chole", "channa masala|chana masala|chickpea curry", 140, 7, 150),
        food("Rajma", "rajma curry", 125, 7, 150),
        food("Sundal", "sprouts|chana sundal|kondakadalai", 120, 8, 100),
        food("Curd", "thayir|yogurt|yoghurt|dahi", 60, 3.5, 100),
        food("Pickle", "oorugai|achar", 200, 2, 15),
        food("Salad", "vegetable salad|cucumber|carrot", 30, 1, 100),

        // ── Egg, meat, fish ──
        food("Egg", "boiled egg|muttai|egg white|eggs", 155, 13, 50, 50),
        food("Egg poriyal", "egg burji|egg bhurji|muttai poriyal|scrambled egg", 180, 12, 70, 70),
        food("Omelette", "omlet|omelet|egg omelette", 190, 12, 70, 70),
        food("Chicken curry", "chicken gravy|chicken kulambu|chicken masala|chicken", 150, 15, 150),
        food("Chicken fry", "chicken 65|fried chicken|chicken roast|chicken chukka", 250, 20, 100, null, F),
        food("Grilled chicken", "tandoori chicken|grill chicken|chicken breast|boiled chicken", 165, 25, 150),
        food("Mutton curry", "mutton gravy|mutton kulambu|mutton", 200, 15, 150),
        food("Fish fry", "meen varuval|fish varuval|fried fish", 220, 20, 80, null, F),
        food("Fish curry", "meen kulambu|fish kulambu|fish", 110, 13, 150),
        food("Prawn", "prawns|eral|shrimp", 100, 20, 100),

        // ── Snacks ──
        food("Vada", "medu vada|ulundu vada|uzhunnu vada|parippu vada|masala vada", 290, 8, 80, 40, F),
        food("Mini vada", "mini vadai|sambar vada", 290, 8, 60, 15, F),
        food("Bonda", "aloo bonda|potato bonda", 280, 6, 80, 40, F),
        food("Bajji", "bhaji|bajjii|milagai bajji|vazhakkai bajji", 250, 5, 90, 30, F),
        food("Pakoda", "pakora|pakkoda|onion pakoda", 450, 9, 50, null, F),
        food("Samosa", "samosas", 310, 5, 60, 60, F),
        food("Murukku", "chakli|thenkuzhal", 520, 9, 20, 20, F),
        food("Mixture", "kara mixture|namkeen|chivda", 520, 12, 30, null, F),
        food("Inippu sev", "inipusev|inippu sev|sweet sev|sweet boondi|boondi", 480, 6, 30, null, F, S),
        food("Chips", "lays|potato chips|banana chips|crisps", 540, 6, 25, 2, F),
        food("Puff", "chicken puff|egg puff|veg puff|puffs", 330, 8, 90, 90, F),
        food("Bun", "coconut bun|cream bun|sweet bun|bun butter jam", 330, 7, 70, 70, S),
        food("Biscuit", "biscuits|butter biscuit|cookie|cookies|marie", 470, 7, 20, 8, S),
        food("Cake", "pastry|cup cake|cupcake", 370, 5, 60, 60, S),
        food("Popcorn", "pop corn", 400, 12, 25),
        food("Peanuts", "groundnut|kadalai|verkadalai|peanut", 570, 26, 30),
        food("Almonds", "badam|almond", 580, 21, 15, 1.2),
        food("Cashew", "cashews|mundhiri|kaju", 560, 18, 15, 1.5),
        food("Dates", "pericham pazham|date|khajur", 280, 2.5, 30, 8, S),

        // ── Sweets ──
        food("Laddu", "ladoo|laddoo|boondi laddu|rava laddu", 420, 6, 40, 40, S),
        food("Jalebi", "jilebi|jangiri", 400, 3, 50, 25, F, S),
        food("Gulab jamun", "gulab jamoon|jamun", 380, 5, 50, 25, F, S),
        food("Mysore pak", "mysorepak", 520, 6, 40, 40, S),
        food("Halwa", "kesari|rava kesari|halva|carrot halwa", 350, 4, 80, null, S),
        food("Payasam", "kheer|payasa|semiya payasam", 180, 4, 150, null, S),
        food("Ice cream", "icecream|kulfi", 210, 3.5, 80, null, S),
        food("Chocolate", "dairy milk|chocolate bar", 535, 7, 30, 10, S),

        // ── Fruit ──
        food("Banana", "vazhaipazham|pazham|banana fruit", 90, 1.1, 120, 120),
        food("Apple", "apples", 52, 0.3, 150, 150),
        food("Orange", "oranges|mosambi|sweet lime", 47, 0.9, 150, 150),
        food("Mango", "maambazham|mangoes", 60, 0.8, 150, 150, S),
        food("Papaya", "pappali", 43, 0.5, 150),
        food("Watermelon", "tharpoosani|water melon", 30, 0.6, 250),
        food("Guava", "koyya|guavas", 68, 2.6, 120, 120),
        food("Grapes", "thiratchai", 69, 0.7, 100),
        food("Pomegranate", "mathulai", 83, 1.7, 100),

        // ── Drinks ──
        food("Tender coconut", "ilaneer|ilani|elaneer|ilaneer water|tender coconut water|coconut water|elaneer water|ilaneeru", 19, 0.7, 250, null, D),
        food("Pathaneer", "padaneer|neera|palm juice|palm sap", 30, 0.3, 250, null, D),
        food("Buttermilk", "butter milk|moru|mor|chaas|chaach", 20, 1, 200, null, D),
        food("Lemon juice", "lime juice|nimbu pani|lemonade|lemon water", 40, 0, 250, null, D, S),
        food("Fresh juice", "juice|orange juice|mosambi juice|sugarcane juice|watermelon juice", 50, 0.5, 250, null, D, S),
        food("Soft drink", "cola|coke|pepsi|sprite|soda|cool drink|cooldrink", 42, 0, 300, null, D, S),
        food("Tea", "chai|milk tea|ginger tea", 40, 1, 150, null, D),
        food("Coffee", "filter coffee|kaapi|milk coffee", 50, 1.5, 150, null, D),
        food("Milk", "paal|badam milk|rose milk", 65, 3.3, 200, null, D),
        food("Lassi", "sweet lassi", 100, 3, 200, null, D, S),
        food("Protein shake", "whey|protein|protein powder", 400, 75, 30, 30),
        food("Water", "drinking water|thanni|tanni|mineral water", 0, 0, 250, null, D, FoodTag.WATER),
    )

    private val byFoodName: Map<String, Food> = foods.associateBy { it.name }

    /** A food by its table name, as stored in the entry cache. */
    fun byName(name: String): Food? = byFoodName[name]

    private val words = Regex("""[^\p{L}\p{N}]+""")

    /** Alias words joined by single spaces → its food. */
    private val byAlias: Map<String, Food> = buildMap {
        foods.forEach { food ->
            food.aliases.forEach { alias -> putIfAbsent(words.split(alias).filter { it.isNotEmpty() }.joinToString(" "), food) }
        }
    }
    private val longestAlias = byAlias.keys.maxOf { it.split(" ").size }

    /**
     * The food named in [text], matching whole words; the longest name wins,
     * so "lemon rice" beats "rice". One map lookup per word group, so it's fast
     * enough to run over every line of a long diary.
     */
    fun find(text: String): Food? {
        val tokens = words.split(text.lowercase()).filter { it.isNotEmpty() }
        var best: String? = null
        for (start in tokens.indices) {
            for (size in minOf(longestAlias, tokens.size - start) downTo 1) {
                val key = tokens.subList(start, start + size).joinToString(" ")
                if (key in byAlias && (best == null || key.length > best.length)) best = key
            }
        }
        return best?.let(byAlias::get)
    }
}
