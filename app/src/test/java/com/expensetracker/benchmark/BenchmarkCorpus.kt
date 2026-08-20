package com.expensetracker.benchmark

/**
 * Benchmark corpus — 500+ realistic anonymized banking/UPI notifications
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
    )

    private val phonePeMerchants = listOf(
        "Dominos", "KFC", "McDonald's", "DMart", "Reliance Fresh", "Ajio", "Meesho",
        "RedBus", "Makemytrip", "Hotstar", "Disney+", "Electricity Bill", "Gas Bill",
        "Fortis Hospital", "Nike", "Adidas", "Zepto", "Instamart", "Goibibo", "Indane Gas",
    )

    private val paytmMerchants = listOf(
        "PVR", "BookMyShow", "Zomato", "Uber Eats", "Flipkart", "Snapdeal",
        "IRCTC", "RedBus", "Recharge Airtel", "Recharge Jio", "Electricity Bill",
        "Broadband", "Insurance Premium", "Mutual Fund SIP", "Zerodha", "Groww",
        "1mg", "Apollo", "Lenskart", "BigBasket",
    )

    private val banks = listOf("HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank")
    private val bankMerchants = listOf(
        "SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "UBER", "OLA", "NETFLIX",
        "RELIANCE FRESH", "BIGBASKET", "DMART", "AIR INDIA", "IRCTC", "APOLLO PHARMACY",
        "JIO", "AIRTEL", "LIC PREMIUM", "SPOTIFY", "KFC", "PIZZA HUT", "MYNTRA",
    )

    private val amexMerchants = listOf(
        "Amazon.in", "Flipkart", "Myntra", "Swiggy", "Zomato", "Uber", "BookMyShow",
        "Marriott", "Taj Hotels", "Apple.com", "Samsung.in", "Netflix", "Adobe",
        "Forever21", "H&M", "Nike", "Lufthansa", "AirAsia", "Vistara", "Starbucks",
    )

    private val gPayCreditSenders = listOf(
        "Ramesh Kumar", "Priya Sharma", "Amit Patel", "Sneha Iyer", "Vikram Singh",
    )

    private val personalTransfers = listOf(
        "Ramesh Kumar", "Priya Sharma", "Amit Patel", "Sneha Iyer", "Vikram Singh",
        "Kiran Rao", "Neha Gupta", "Sanjay Mehta", "Divya Nair", "Arjun Reddy",
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
                repeat(2) { j ->
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
            repeat(3) { j ->
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
            else -> "Friend/Transfer"
        }
    }
}