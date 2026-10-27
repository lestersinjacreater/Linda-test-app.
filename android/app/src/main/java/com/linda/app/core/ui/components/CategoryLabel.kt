package com.linda.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.linda.app.R

/** A scam type in plain words, in the app language. Unknown types read as just "scam". */
@Composable
fun categoryLabel(category: String): String = stringResource(categoryRes(category))

/** The string for a scam type, for code outside a screen (the call card). */
fun categoryRes(category: String): Int = when (category) {
    "test" -> R.string.cat_test
    "fake_mpesa" -> R.string.cat_fake_mpesa
    "sent_by_mistake" -> R.string.cat_sent_by_mistake
    "prize" -> R.string.cat_prize
    "fuliza_upgrade" -> R.string.cat_fuliza_upgrade
    "kra_refund" -> R.string.cat_kra_refund
    "job_fee" -> R.string.cat_job_fee
    "loan_fee" -> R.string.cat_loan_fee
    "pin_request" -> R.string.cat_pin_request
    "phishing_link" -> R.string.cat_phishing_link
    else -> R.string.cat_other
}

/** The same words starting with a capital, for card titles and table cells ("Sent by mistake"). */
@Composable
fun categoryTitle(category: String): String = categoryLabel(category).replaceFirstChar { it.uppercaseChar() }
