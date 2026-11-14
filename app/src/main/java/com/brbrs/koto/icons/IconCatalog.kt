package com.brbrs.koto.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Snowboarding
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A curated set of Material Symbols for list icons.
 *
 * Deliberately a different set from Tavla's, and in the Rounded style rather than Filled.
 * Tavla's catalog is built for work boards -- code, bug reports, kanban, design services --
 * and reusing it made a Koto list and a Tavla board look like the same object. These lean
 * toward what a personal task list actually holds: errands, chores, routines, admin.
 *
 * [DEFAULT_ICON] is the fallback for any name no longer in this map, so an icon chosen on
 * another device (or in an older build) never renders as a blank square.
 */
object IconCatalog {
    const val DEFAULT_ICON = "checklist"

    val icons: Map<String, ImageVector> = linkedMapOf(
        DEFAULT_ICON to Icons.Rounded.Checklist,
        "inbox" to Icons.Rounded.Inbox,
        "agenda" to Icons.Rounded.EventNote,
        "routine" to Icons.Rounded.Repeat,
        "alarm" to Icons.Rounded.Alarm,

        // Home and errands
        "groceries" to Icons.Rounded.ShoppingBasket,
        "shop" to Icons.Rounded.Storefront,
        "cooking" to Icons.Rounded.Restaurant,
        "breakfast" to Icons.Rounded.Egg,
        "coffee" to Icons.Rounded.LocalCafe,
        "cleaning" to Icons.Rounded.CleaningServices,
        "laundry" to Icons.Rounded.LocalLaundryService,
        "bathroom" to Icons.Rounded.Bathtub,
        "bedroom" to Icons.Rounded.Bed,
        "repairs" to Icons.Rounded.Build,
        "keys" to Icons.Rounded.Key,
        "garden" to Icons.Rounded.Eco,

        // Money and admin
        "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.Wallet,
        "card" to Icons.Rounded.CreditCard,
        "receipts" to Icons.Rounded.Receipt,
        "savings" to Icons.Rounded.Savings,
        "post" to Icons.Rounded.MarkEmailUnread,

        // People and health
        "family" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "conversations" to Icons.Rounded.Forum,
        "health" to Icons.Rounded.LocalHospital,
        "pharmacy" to Icons.Rounded.LocalPharmacy,
        "wellbeing" to Icons.Rounded.SelfImprovement,
        "spa" to Icons.Rounded.Spa,
        "birthday" to Icons.Rounded.Cake,

        // Out and about
        "commute" to Icons.Rounded.Commute,
        "cycling" to Icons.Rounded.DirectionsBike,
        "hiking" to Icons.Rounded.Hiking,
        "holiday" to Icons.Rounded.BeachAccess,
        "winter" to Icons.Rounded.Snowboarding,
        "sailing" to Icons.Rounded.Anchor,
        "weather" to Icons.Rounded.Umbrella,

        // Making and learning
        "reading" to Icons.Rounded.AutoStories,
        "writing" to Icons.Rounded.Brush,
        "music" to Icons.Rounded.Piano,
        "recording" to Icons.Rounded.Mic,

        // Time of day
        "morning" to Icons.Rounded.WbSunny,
        "daytime" to Icons.Rounded.LightMode,
        "evening" to Icons.Rounded.Nightlight,
    )

    fun iconFor(name: String?): ImageVector = icons[name] ?: icons.getValue(DEFAULT_ICON)
}
