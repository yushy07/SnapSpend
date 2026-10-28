package com.example.data.ai

import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import java.util.Locale

/**
 * Single clean dictionary of ~150 Indian brands, merchant keywords, and UPI handle prefixes
 * mapped strictly to existing [DefaultCategories.ALL_DEFAULTS] category names.
 *
 * All keyword matching uses whole-word boundaries (`\b...\b`) so short tokens like "ola", "tea",
 * or "gas" never match inside unrelated words like "Coca Cola", "Steam", or "Vegas".
 */
object IndianBrandDictionary {

    data class BrandRule(
        val keyword: String,
        val category: String,
        val regex: Regex = Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
    )

    data class UpiHandleRule(
        val prefix: String,
        val category: String
    )

    // Strictly uses existing category names from DefaultCategories.ALL_DEFAULTS:
    // "Food & Dining", "Groceries", "Transport & Fuel", "Shopping", "Bills & Utilities",
    // "Rent", "Entertainment", "Health", "Education", "Travel", "Subscriptions"
    val BRAND_RULES: List<BrandRule> = buildList {
        fun addBrands(category: String, vararg keywords: String) {
            for (kw in keywords) {
                add(BrandRule(keyword = kw.lowercase(Locale.US), category = category))
            }
        }

        // 1. Food & Dining (~35 brands)
        addBrands(
            "Food & Dining",
            "swiggy", "zomato", "eatsure", "magicpin", "box8", "faasos", "behrouz",
            "ovenstory", "mcdonald", "mcdonalds", "burger king", "kfc", "dominos",
            "domino's", "pizza hut", "subway", "starbucks", "third wave coffee",
            "blue tokai", "chaayos", "chai point", "haldiram", "haldirams", "bikanervala",
            "barbeque nation", "wow momo", "rebel foods", "natural ice cream", "baskin robbins",
            "dunkin", "taco bell", "wendys", "cafe coffee day", "ccd", "theobroma",
            "truffles", "meghana foods", "saravana bhavan", "a2b", "adarsh", "udupi",
            "restaurant", "cafe", "dhaba", "bakery", "biryani", "chai", "tea", "coffee"
        )

        // 2. Groceries (~20 brands)
        addBrands(
            "Groceries",
            "zepto", "blinkit", "instamart", "swiggy instamart", "bigbasket", "bbnow",
            "bbdaily", "dmart", "d-mart", "avenue supermarts", "jiomart", "reliance fresh",
            "reliance smart", "more supermarket", "spencers", "nature's basket", "natures basket",
            "ratnadeep", "star bazaar", "lulu hypermarket", "spar", "country delight",
            "milkbasket", "supr daily", "licious", "freshtohome", "supermarket", "kirana",
            "grocery", "groceries", "dairy farm", "amul", "nandini"
        )

        // 3. Transport & Fuel (~22 brands)
        addBrands(
            "Transport & Fuel",
            "uber", "ola", "rapido", "namma yatri", "blusmart", "meru",
            "bmrc", "dmrc", "cmrl", "mmrda", "mahametro", "metro",
            "bmtc", "best bus", "apsrtc", "tsrtc", "ksrtc", "msrtc",
            "indian oil", "iocl", "bharat petroleum", "bpcl", "hindustan petroleum", "hpcl",
            "shell", "nayara energy", "jio bp", "petrol", "fuel", "diesel", "cng",
            "fastag", "nhai", "park+", "parkplus"
        )

        // 4. Travel (~15 brands)
        addBrands(
            "Travel",
            "irctc", "makemytrip", "goibibo", "cleartrip", "easemytrip", "ixigo",
            "yatra", "redbus", "abhibus", "indigo", "air india", "vistara",
            "akasa air", "spicejet", "oyo", "airbnb", "agoda", "booking.com", "thomas cook"
        )

        // 5. Shopping (~22 brands)
        addBrands(
            "Shopping",
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq",
            "tata cliq", "croma", "reliance digital", "vijay sales", "decathlon",
            "ikea", "zudio", "westside", "pantaloons", "lifestyle", "shoppers stop",
            "max fashion", "zara", "h&m", "uniqlo", "lenskart", "firstcry", "pepperfry",
            "urban ladder", "purplle", "mamaearth", "wakefit", "boat", "titan", "tanishq"
        )

        // 6. Bills & Utilities (~22 brands)
        addBrands(
            "Bills & Utilities",
            "jio", "airtel", "vodafone", "vi", "bsnl", "act fibernet", "hathway",
            "tata play", "tatasky", "dish tv", "d2h", "bescom", "msedcl", "tata power",
            "adani electricity", "bses", "torrent power", "cesc", "tangedco", "kseb",
            "uppcl", "bwssb", "indane", "bharatgas", "hp gas", "mahanagar gas",
            "indraprastha gas", "igl", "mgl", "adani gas", "electricity", "broadband",
            "postpaid", "prepaid", "recharge", "water bill", "cylinder", "gas"
        )

        // 7. Entertainment (~12 brands)
        addBrands(
            "Entertainment",
            "bookmyshow", "pvr", "inox", "cinepolis", "paytm insider", "zomato live",
            "district", "steam store", "playstation", "xbox", "imagica", "wonderla"
        )

        // 8. Subscriptions (~12 brands)
        addBrands(
            "Subscriptions",
            "netflix", "spotify", "youtube premium", "google one", "apple services",
            "icloud", "amazon prime", "prime video", "hotstar", "disney+ hotstar",
            "jiocinema", "sonyliv", "zee5", "audible", "kindle", "chatgpt", "openai",
            "claude", "canva", "microsoft 365", "adobe"
        )

        // 9. Health (~16 brands)
        addBrands(
            "Health",
            "apollo", "apollo pharmacy", "pharmeasy", "1mg", "tata 1mg", "medplus",
            "netmeds", "practo", "cult.fit", "cultfit", "healthians", "dr lal pathlabs",
            "thyrocare", "metropolis", "manipal hospital", "fortis", "max healthcare",
            "narayana health", "aster", "pharmacy", "medical", "chemist", "druggist",
            "hospital", "clinic", "dental", "diagnostics"
        )

        // 10. Education (~10 brands)
        addBrands(
            "Education",
            "udemy", "coursera", "unacademy", "physicswallah", "pw", "byjus",
            "allen", "upgrad", "scaler", "duolingo", "skillshare", "tuition", "college", "school"
        )

        // 11. Rent (~5 brands)
        addBrands(
            "Rent",
            "nobroker", "nestaway", "stanza living", "zolo", "colive"
        )
    }

    val UPI_HANDLE_PREFIXES: List<UpiHandleRule> = listOf(
        UpiHandleRule("swiggy", "Food & Dining"),
        UpiHandleRule("zomato", "Food & Dining"),
        UpiHandleRule("eatclub", "Food & Dining"),
        UpiHandleRule("dominos", "Food & Dining"),
        UpiHandleRule("mcdonalds", "Food & Dining"),
        UpiHandleRule("starbucks", "Food & Dining"),
        UpiHandleRule("zepto", "Groceries"),
        UpiHandleRule("blinkit", "Groceries"),
        UpiHandleRule("grofers", "Groceries"),
        UpiHandleRule("bigbasket", "Groceries"),
        UpiHandleRule("dmart", "Groceries"),
        UpiHandleRule("jiomart", "Groceries"),
        UpiHandleRule("licious", "Groceries"),
        UpiHandleRule("uber", "Transport & Fuel"),
        UpiHandleRule("ola", "Transport & Fuel"),
        UpiHandleRule("rapido", "Transport & Fuel"),
        UpiHandleRule("bmrc", "Transport & Fuel"),
        UpiHandleRule("dmrc", "Transport & Fuel"),
        UpiHandleRule("iocl", "Transport & Fuel"),
        UpiHandleRule("bpcl", "Transport & Fuel"),
        UpiHandleRule("hpcl", "Transport & Fuel"),
        UpiHandleRule("irctc", "Travel"),
        UpiHandleRule("makemytrip", "Travel"),
        UpiHandleRule("redbus", "Travel"),
        UpiHandleRule("ixigo", "Travel"),
        UpiHandleRule("indigo", "Travel"),
        UpiHandleRule("amazon", "Shopping"),
        UpiHandleRule("flipkart", "Shopping"),
        UpiHandleRule("myntra", "Shopping"),
        UpiHandleRule("meesho", "Shopping"),
        UpiHandleRule("nykaa", "Shopping"),
        UpiHandleRule("ajio", "Shopping"),
        UpiHandleRule("decathlon", "Shopping"),
        UpiHandleRule("lenskart", "Shopping"),
        UpiHandleRule("jio", "Bills & Utilities"),
        UpiHandleRule("airtel", "Bills & Utilities"),
        UpiHandleRule("bescom", "Bills & Utilities"),
        UpiHandleRule("tataplay", "Bills & Utilities"),
        UpiHandleRule("actfibernet", "Bills & Utilities"),
        UpiHandleRule("bookmyshow", "Entertainment"),
        UpiHandleRule("pvr", "Entertainment"),
        UpiHandleRule("inox", "Entertainment"),
        UpiHandleRule("netflix", "Subscriptions"),
        UpiHandleRule("spotify", "Subscriptions"),
        UpiHandleRule("hotstar", "Subscriptions"),
        UpiHandleRule("apollo", "Health"),
        UpiHandleRule("pharmeasy", "Health"),
        UpiHandleRule("1mg", "Health"),
        UpiHandleRule("medplus", "Health"),
        UpiHandleRule("practo", "Health"),
        UpiHandleRule("cultfit", "Health"),
        UpiHandleRule("unacademy", "Education"),
        UpiHandleRule("nobroker", "Rent")
    )

    private val UPI_HANDLE_EXTRACT_REGEX = Regex("""([a-zA-Z0-9._-]+)@[a-zA-Z0-9]+""")

    /**
     * Matches a merchant name or raw narration against UPI handle prefixes first,
     * then whole-word brand rules.
     *
     * If [allowedCategories] is provided and non-empty, only returns a category that exists in
     * [allowedCategories] or [DefaultCategories.ALL_DEFAULTS].
     */
    fun matchCategory(
        merchantText: String,
        rawText: String = "",
        allowedCategories: List<CategoryEntity> = emptyList()
    ): String? {
        val combined = "$merchantText $rawText".trim()
        if (combined.isEmpty()) return null

        // 1. Check UPI handle prefixes (e.g., "swiggy.stores@axisbank" or "irctc@sbi")
        val handleMatches = UPI_HANDLE_EXTRACT_REGEX.findAll(combined)
        for (match in handleMatches) {
            val handleUser = match.groupValues[1].lowercase(Locale.US)
            val rule = UPI_HANDLE_PREFIXES.firstOrNull {
                handleUser == it.prefix ||
                    handleUser.startsWith("${it.prefix}.") ||
                    handleUser.startsWith("${it.prefix}_") ||
                    handleUser.startsWith("${it.prefix}-") ||
                    handleUser.startsWith(it.prefix)
            }
            if (rule != null && isCategoryAllowed(rule.category, allowedCategories)) {
                return rule.category
            }
        }

        // 2. Check whole-word brand rules on merchantText first, then rawText
        for (rule in BRAND_RULES) {
            if (rule.regex.containsMatchIn(merchantText)) {
                if (isCategoryAllowed(rule.category, allowedCategories)) {
                    return rule.category
                }
            }
        }
        if (rawText.isNotBlank()) {
            for (rule in BRAND_RULES) {
                if (rule.regex.containsMatchIn(rawText)) {
                    if (isCategoryAllowed(rule.category, allowedCategories)) {
                        return rule.category
                    }
                }
            }
        }

        return null
    }

    private fun isCategoryAllowed(categoryName: String, allowedCategories: List<CategoryEntity>): Boolean {
        if (allowedCategories.isEmpty()) return true
        return allowedCategories.any { it.name.equals(categoryName, ignoreCase = true) } ||
            DefaultCategories.ALL_DEFAULTS.any { it.name.equals(categoryName, ignoreCase = true) }
    }

    /**
     * Validates that every single category name in [BRAND_RULES] and [UPI_HANDLE_PREFIXES]
     * exists in [DefaultCategories.ALL_DEFAULTS].
     * Throws [IllegalStateException] if any unknown category name is present.
     */
    fun validateAgainstDefaultCategories(): Boolean {
        val validNames = DefaultCategories.ALL_DEFAULTS.map { it.name }.toSet()
        for (rule in BRAND_RULES) {
            check(rule.category in validNames) {
                "Invalid category '${rule.category}' for brand '${rule.keyword}'. Allowed: $validNames"
            }
        }
        for (handle in UPI_HANDLE_PREFIXES) {
            check(handle.category in validNames) {
                "Invalid category '${handle.category}' for UPI handle '${handle.prefix}'. Allowed: $validNames"
            }
        }
        return true
    }
}
