package com.apexvpn.client.data

/**
 * 602. Готовые наборы «Сайты страны напрямую»: домены страны (.kz, .by…) и её крупные
 * сайты на других доменах идут мимо VPN — быстрее и без проблем с «доступно только в стране».
 */
object DirectCountries {
    data class Country(val id: String, val flag: String, val title: String, val suffixes: List<String>)

    val all = listOf(
        Country("kz", "🇰🇿", "Казахстан", listOf("kz", "xn--80ao21a", "kaspi.kz", "halykbank.kz", "egov.kz", "kolesa.kz", "krisha.kz", "olx.kz")),
        Country("by", "🇧🇾", "Беларусь", listOf("by", "xn--90ais", "onliner.by", "kufar.by", "belarusbank.by", "priorbank.by")),
        Country("uz", "🇺🇿", "Узбекистан", listOf("uz", "olx.uz", "uzum.uz", "payme.uz", "click.uz")),
        Country("kg", "🇰🇬", "Кыргызстан", listOf("kg", "lalafo.kg", "mbank.kg")),
        Country("am", "🇦🇲", "Армения", listOf("am", "xn--y9a3aq", "list.am", "idram.am")),
        Country("az", "🇦🇿", "Азербайджан", listOf("az", "tap.az", "kapitalbank.az")),
        Country("ge", "🇬🇪", "Грузия", listOf("ge", "xn--node", "myhome.ge", "tbcbank.com.ge")),
        Country("tj", "🇹🇯", "Таджикистан", listOf("tj", "somon.tj")),
        Country("md", "🇲🇩", "Молдова", listOf("md", "999.md")),
        Country("tr", "🇹🇷", "Турция", listOf("com.tr", "gov.tr", "edu.tr", "org.tr", "net.tr", "tr", "trendyol.com", "hepsiburada.com", "sahibinden.com")),
    )

    fun suffixes(ids: Set<String>): List<String> = all.filter { it.id in ids }.flatMap { it.suffixes }.distinct()
}
