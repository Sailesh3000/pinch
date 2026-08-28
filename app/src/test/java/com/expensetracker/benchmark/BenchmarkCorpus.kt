package com.expensetracker.benchmark

/**
 * Benchmark corpus — 1000+ realistic anonymized banking/UPI notifications
 * across Google Pay, PhonePe, Paytm, HDFC, ICICI, SBI, Axis, CRED, Amex
 * (spec §12.1 #3).
 *
 * Entries are generated programmatically from deterministic templates so the
 * expected labels (amount, merchant, category, type) are always exact — the
 * corpus is self-annotating and reproducible.
 */
object BenchmarkCorpus {

    data class Sample(
        val packageName: String,
        val title: String,
        val body: String,
        val expectedAmount: Double,
        val expectedMerchant: String,
        val expectedCategory: String,
        val expectedType: String, // DEBIT / CREDIT / NON_FINANCIAL
    )

    private val gPayMerchants = listOf(
        "Swiggy", "Zomato", "BigBasket", "Blinkit", "Zepto", "Amazon", "Flipkart",
        "Myntra", "Uber", "Ola", "Netflix", "Spotify", "PVR", "BookMyShow",
        "JioFiber", "Airtel", "Apollo Pharmacy", "1mg", "Rapido", "IRCTC",
        "Faasos", "Nature's Basket", "Croma", "Nykaa", "Yatra", "Cleartrip",
        "SonyLIV", "Zee5", "Netmeds", "Practo", "Decathlon", "BSNL",
    )

    private val phonePeMerchants = listOf(
        "Dominos", "KFC", "McDonald's", "DMart", "Reliance Fresh", "Ajio", "Meesho",
        "RedBus", "Makemytrip", "Hotstar", "Disney+", "Electricity Bill", "Gas Bill",
        "Fortis Hospital", "Nike", "Adidas", "Zepto", "Instamart", "Goibibo", "Indane Gas",
        "Subway", "More Supermarket", "Tata Cliq", "Vijay Sales", "Vodafone Idea",
        "PharmEasy", "Voot", "GoAir", "Upstox", "Zara", "Uniqlo", "Best Buy",
    )

    private val paytmMerchants = listOf(
        "PVR", "BookMyShow", "Zomato", "Uber Eats", "Flipkart", "Snapdeal",
        "IRCTC", "RedBus", "Recharge Airtel", "Recharge Jio", "Electricity Bill",
        "Broadband", "Insurance Premium", "Mutual Fund SIP", "Zerodha", "Groww",
        "1mg", "Apollo", "Lenskart", "BigBasket",
        "Reliance Digital", "Nykaa", "IndiGo", "Yatra", "Netmeds",
        "Vodafone Recharge", "BSNL Bill", "Paytm Money SIP", "Tata Power", "Costco",
        "Sephora", "Spencer's",
    )

    private val banks = listOf("HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank")
    private val bankMerchants = listOf(
        "SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "UBER", "OLA", "NETFLIX",
        "RELIANCE FRESH", "BIGBASKET", "DMART", "AIR INDIA", "IRCTC", "APOLLO PHARMACY",
        "JIO", "AIRTEL", "LIC PREMIUM", "SPOTIFY", "KFC", "PIZZA HUT", "MYNTRA",
        "NYKAA", "CROMA", "TATA CLIQ", "VODAFONE", "SUBWAY", "BURGER KING",
        "YATRA", "CLEARTRIP", "NETMEDS", "PRACTO", "ZERODHA", "GROWW",
    )

    private val amexMerchants = listOf(
        "Amazon.in", "Flipkart", "Myntra", "Swiggy", "Zomato", "Uber", "BookMyShow",
        "Marriott", "Taj Hotels", "Apple.com", "Samsung.in", "Netflix", "Adobe",
        "Forever21", "H&M", "Nike", "Lufthansa", "AirAsia", "Vistara", "Starbucks",
        "Hyatt", "Hilton", "Emirates", "British Airways", "Singapore Airlines",
        "Sephora", "Zara", "Uniqlo", "Best Buy", "Costco",
    )

    private val gPayCreditSenders = listOf(
        "Ramesh Kumar", "Priya Sharma", "Amit Patel", "Sneha Iyer", "Vikram Singh",
        "Anjali Verma", "Rohit Malhotra", "Deepa Krishnan", "Suresh Nair", "Pooja Desai",
    )

    private val personalTransfers = listOf(
        "Ramesh Kumar", "Priya Sharma", "Amit Patel", "Sneha Iyer", "Vikram Singh",
        "Kiran Rao", "Neha Gupta", "Sanjay Mehta", "Divya Nair", "Arjun Reddy",
        "Manoj Tiwari", "Lakshmi Menon", "Rahul Bose", "Swati Joshi", "Karthik Subramanian",
        "Anita D'Souza", "Vivek Chatterjee", "Meera Pillai", "Ashok Yadav", "Rekha Bhatt",
    )

    private val corpus: List<Sample> by lazy { buildCorpus() }

    val samples: List<Sample> get() = corpus

    val size: Int get() = corpus.size

    private fun buildCorpus(): List<Sample> {
        val out = mutableListOf<Sample>()
        val rand = java.util.Random(42) // deterministic seed

        // --- Google Pay (UPI) ---
        gPayMerchants.forEachIndexed { i, merchant ->
            repeat(4) { j ->
                val amount = round2(50.0 + ((i * 37 + j * 53) % 4000) * 0.75 + rand.nextDouble() * 40)
                out += Sample(
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    title = "Money debited from your account",
                    body = "₹$amount paid to ${merchant.uppercase()} via UPI. UPI Ref: ${402931827 + i * 17 + j}",
                    expectedAmount = amount,
                    expectedMerchant = merchant,
                    expectedCategory = categoryFor(merchant),
                    expectedType = "DEBIT",
                )
            }
        }

        // --- Google Pay credits (ambiguous personal senders → low confidence) ---
        gPayCreditSenders.forEachIndexed { i, sender ->
            repeat(4) { j ->
                val amount = round2(200.0 + (i * 41 + j * 29) * 13.0)
                out += Sample(
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    title = "Money received",
                    body = "₹$amount received from $sender. UPI Ref: ${991200101 + i * 53 + j}",
                    expectedAmount = amount,
                    expectedMerchant = sender,
                    expectedCategory = "Uncategorized",
                    expectedType = "CREDIT",
                )
            }
        }

        // --- PhonePe ---
        phonePeMerchants.forEachIndexed { i, merchant ->
            repeat(4) { j ->
                val amount = round2(80.0 + ((i * 29 + j * 61) % 5000) * 0.6 + rand.nextDouble() * 25)
                out += Sample(
                    packageName = "com.phonepe.app",
                    title = "Payment successful",
                    body = "₹$amount paid to $merchant via PhonePe. Txn ID: ${70001100 + i * 100 + j}",
                    expectedAmount = amount,
                    expectedMerchant = merchant,
                    expectedCategory = categoryFor(merchant),
                    expectedType = "DEBIT",
                )
            }
        }

        // --- Paytm ---
        paytmMerchants.forEachIndexed { i, merchant ->
            repeat(4) { j ->
                val amount = round2(60.0 + ((i * 17 + j * 43) % 6000) * 0.5 + rand.nextDouble() * 30)
                out += Sample(
                    packageName = "net.one97.paytm",
                    title = "Paytm txn successful",
                    body = "Debited ₹$amount from your Paytm Payments Bank account, paid to $merchant. UPI Ref: ${52003300 + i * 90 + j}",
                    expectedAmount = amount,
                    expectedMerchant = merchant,
                    expectedCategory = categoryFor(merchant),
                    expectedType = "DEBIT",
                )
            }
        }

        // --- Banks (HDFC, ICICI, SBI, Axis) ---
        banks.forEachIndexed { b, bank ->
            bankMerchants.forEachIndexed { i, merchant ->
                repeat(3) { j ->
                    val amount = round2(150.0 + ((b * 100 + i * 23 + j * 37) % 9000) * 0.55 + rand.nextDouble() * 50)
                    out += Sample(
                        packageName = when (bank) {
                            "HDFC Bank" -> "com.hdfc.bank"
                            "ICICI Bank" -> "com.icici.bank"
                            "State Bank of India" -> "in.org.npci.upi"
                            else -> "com.axisbank.lite"
                        },
                        title = "$bank debit alert",
                        body = "₹$amount debited from a/c **${8500 + b * 1234} at $merchant on 12-AUG. Available bal: ₹${18000 + b * 500}.",
                        expectedAmount = amount,
                        expectedMerchant = merchant.replaceFirstChar { it.uppercase() },
                        expectedCategory = categoryFor(merchant.replaceFirstChar { it.uppercase() }),
                        expectedType = "DEBIT",
                    )
                }
            }
        }

        // --- Amex credit card ---
        amexMerchants.forEachIndexed { i, merchant ->
            repeat(4) { j ->
                val amount = round2(200.0 + ((i * 47 + j * 31) % 12000) * 0.8 + rand.nextDouble() * 100)
                out += Sample(
                    packageName = "com.americanexpress.android.acctsvc.us",
                    title = "Amex Purchase",
                    body = "INR $amount at ${merchant.uppercase()}. Card ending 1004 on 12-AUG.",
                    expectedAmount = amount,
                    expectedMerchant = merchant,
                    expectedCategory = categoryFor(merchant),
                    expectedType = "DEBIT",
                )
            }
        }

        // --- Personal UPI transfers (ambiguous → low confidence, clarify) ---
        personalTransfers.forEachIndexed { i, name ->
            repeat(3) { j ->
                val amount = round2(100.0 + (i * 33 + j * 21) * 17.0)
                out += Sample(
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    title = "Money debited",
                    body = "₹$amount paid to $name via UPI. Ref ${701112233 + i * 77 + j}",
                    expectedAmount = amount,
                    expectedMerchant = name,
                    expectedCategory = "Uncategorized",
                    expectedType = "DEBIT",
                )
            }
        }

        // --- Non-financial (OTP, offers, login) must be dropped ---
        out += Sample("com.google.android.apps.nbu.paisa.user", "OTP for UPI registration", "Your OTP is 381920. Do not share it with anyone.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.google.android.apps.nbu.paisa.user", "UPI Login Alert", "New device login detected. If this was you, no action needed.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.phonepe.app", "Flat 50% OFF offer", "Limited period offer on your next food order. Check the app!", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.hdfc.bank", "HDFC offer", "Earn 10x reward points on dining this weekend.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.icici.bank", "Balance inquiry", "Your account balance has been updated. Check the app for details.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("net.one97.paytm", "Your monthly statement", "View your August statement online.", 0.0, "", "Uncategorized", "NON_FINANCIAL")

        // --- Non-financial (cashback, rewards, promo) must be dropped ---
        out += Sample("com.phonepe.app", "Cashback credited", "₹50 cashback credited to your wallet for this order", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.google.android.apps.nbu.paisa.user", "You're a winner!", "You've won a reward of Rs 100! Claim now", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("net.one97.paytm", "Big sale", "Flat 20% off + extra 10% instant cashback on your next order", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.hdfc.bank", "Diwali sale", "10% cashback credited on your HDFC card this Diwali sale", 0.0, "", "Uncategorized", "NON_FINANCIAL")

        // --- P2P transfer: counterparty's name is the notification title, not a business ---
        out += Sample("com.phonepe.app", "Varun", "sent ₹10 to you.", 10.0, "Varun", "Uncategorized", "CREDIT")

        // --- Abbreviated bank SMS shorthand: "Dr./Cr." + "Acct XXX###", semicolon-delimited
        // clauses. Real-world format (this exact message was captured on-device and failed
        // extraction before the Dr./Cr./Acct/semicolon fixes this session) — underrepresented
        // vs. the explicit "debited"/"credited" wording most other samples use. ---
        out += Sample("com.google.android.apps.messaging", "AX-CANBNK-S", "Dear Customer, Acct XXX331 Dr. INR 10.00 on 26/08/26 to KONDAPURAM V; UPI: 669992992861; Bal INR 1,261.97.Not you?SMS BLOCKUPI to 9901771222-CanaraBank", 10.0, "KONDAPURAM V", "Uncategorized", "DEBIT")
        out += Sample("com.google.android.apps.messaging", "SBIINB-S", "Dear Customer, Acct **5567 Cr. INR 2500.00 on 15/03/26 by NEFT from RAVI KUMAR SHARMA; Ref: 887766554; Avl Bal INR 45,230.00-SBI", 2500.0, "RAVI KUMAR SHARMA", "Uncategorized", "CREDIT")
        out += Sample("com.google.android.apps.messaging", "AXISBK-S", "Acct XX7788 Dr. INR 2000.00 on 02/01/26 ATM Cash Wdl; Avl Bal INR 8,500.00-Axis Bank", 2000.0, "", "Uncategorized", "DEBIT")
        out += Sample("com.google.android.apps.messaging", "UNIONBK-S", "Acct XXX219 Dr. INR 450.00 on 10/02/26 to SWIGGY BANGALORE; UPI: 445566778899; Avl Bal INR 3,200.00-Union Bank", 450.0, "SWIGGY BANGALORE", "Food & Dining", "DEBIT")
        out += Sample("com.google.android.apps.messaging", "ICICIB-S", "Acct XX3345 Cr. INR 599.00 on 05/04/26 REFUND FROM AMAZON; Ref: 991122334; Avl Bal INR 12,340.00-ICICI Bank", 599.0, "AMAZON", "Shopping", "CREDIT")
        out += Sample("com.google.android.apps.messaging", "HDFCBK-S", "Card XX4521 Dr. INR 1200.00 on 20/05/26 at DMART; Avl Bal INR 6,780.00-HDFC Bank", 1200.0, "DMART", "Groceries", "DEBIT")

        // --- Non-financial ad/promo SMS that quote a price but have no transaction verb ---
        out += Sample("com.google.android.apps.messaging", "AD-650025-P", "The wait is over! Watch India vs Sri Lanka on Sony LIV this Independence Day. Get Box Office Pack and enjoy 20+ OTTs at Rs. 200. Recharge now.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.phonepe.app", "Daily RD", "Turn ₹100/day into ₹37,611*! Set up Daily RD to maximize your savings with assured returns. Start saving now.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.phonepe.app", "redBus offer", "Save up to ₹300 on bus tickets! Book redBus tickets directly on PhonePe! Use code PP300 to get up to ₹300 off now.", 0.0, "", "Uncategorized", "NON_FINANCIAL")
        out += Sample("com.google.android.apps.messaging", "BT-SAMSNG-P", "Get personal training on the run with Running Coach on Samsung Galaxy Watch9. Starts at Rs.1417/month*. Own now.", 0.0, "", "Uncategorized", "NON_FINANCIAL")

        return out
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private fun categoryFor(merchant: String): String {
        val m = merchant.lowercase()
        return when {
            m.contains("swiggy") || m.contains("zomato") || m.contains("domin") || m.contains("kfc") ||
                m.contains("mcdonald") || m.contains("pizza") || m.contains("uber eats") || m.contains("starbucks") -> "Food & Dining"
            m.contains("bigbasket") || m.contains("d mart") || m.contains("dmart") || m.contains("reliance fresh") ||
                m.contains("blinkit") || m.contains("instamart") || m.contains("zepto") || m.contains("grocery") ||
                m.contains("indane gas") || m.contains("gas bill") -> "Groceries"
            m.contains("uber") || m.contains("ola") || m.contains("rapido") || m.contains("redbus") ||
                m.contains("irctc") || m.contains("makemytrip") || m.contains("goibibo") || m.contains("air india") ||
                m.contains("lufthansa") || m.contains("airasia") || m.contains("vistara") -> "Transportation"
            m.contains("amazon") || m.contains("flipkart") || m.contains("myntra") || m.contains("ajio") ||
                m.contains("meesho") || m.contains("nike") || m.contains("adidas") || m.contains("h&m") ||
                m.contains("forever21") || m.contains("lenskart") || m.contains("snapdeal") ||
                m.contains("samsung") || m.contains("apple") -> "Shopping"
            m.contains("netflix") || m.contains("spotify") || m.contains("hotstar") || m.contains("disney") ||
                m.contains("bookmyshow") || m.contains("pvr") || m.contains("prime") || m.contains("play store") -> "Entertainment"
            m.contains("jio") || m.contains("airtel") || m.contains("electricity") || m.contains("broadband") ||
                m.contains("insurance") || m.contains("gas bill") || m.contains("recharge") ||
                m.contains("vodafone") || m.contains("indane") -> "Bills & Utilities"
            m.contains("rent") || m.contains("landlord") -> "Rent"
            m.contains("apollo") || m.contains("pharmacy") || m.contains("1mg") || m.contains("hospital") ||
                m.contains("fortis") || m.contains("doctor") || m.contains("clinic") -> "Health & Medical"
            m.contains("zerodha") || m.contains("groww") || m.contains("lic") || m.contains("mutual") ||
                m.contains("stock") || m.contains("sip") || m.contains("investment") || m.contains("adobe") -> "Investment"
            m.contains("marriott") || m.contains("taj hotels") -> "Entertainment"
            // Matches DeterministicRegexExtractor.inferCategory()'s actual default —
            // amount/merchant extraction accuracy is what this corpus exists to
            // guarantee; category is a best-effort secondary signal, and a new
            // brand name with no keyword match should expect what the real
            // extractor will actually produce, not a different made-up default.
            else -> "Uncategorized"
        }
    }
}