package bj.sebpay.app;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tous les pays du monde : code ISO, nom français, indicatif, devise.
 * Fichier généré : la même table existe dans la fonction Edge (fn-sebpay.ts).
 * Le drapeau est calculé à partir du code ISO (émoji), il n'y a aucune image à embarquer.
 * Que SebPay accepte ou non un pays est décidé à l'exécution, via la liste d'opérateurs renvoyée par SebPay.
 */
final class Countries {

    static final class Country {
        final String iso, name, dial, currency, key;

        Country(String iso, String name, String dial, String currency) {
            this.iso = iso;
            this.name = name;
            this.dial = dial;
            this.currency = currency;
            this.key = norm(name + " " + iso + " +" + dial);
        }

        String flag() {
            return Countries.flag(iso);
        }
    }

    /** Pays proposés en tête de liste (zone Mobile Money historique de l'app). */
    static final String[] FEATURED_ISO = {"BJ", "TG", "CI", "SN", "BF", "ML", "NE", "GN", "CM", "CG"};

    private static final String RAW =
            "AF|Afghanistan|93|AFN\n" +
            "ZA|Afrique du Sud|27|ZAR\n" +
            "AL|Albanie|355|ALL\n" +
            "DZ|Algérie|213|DZD\n" +
            "DE|Allemagne|49|EUR\n" +
            "AD|Andorre|376|EUR\n" +
            "AO|Angola|244|AOA\n" +
            "AG|Antigua-et-Barbuda|1|XCD\n" +
            "SA|Arabie saoudite|966|SAR\n" +
            "AR|Argentine|54|ARS\n" +
            "AM|Arménie|374|AMD\n" +
            "AU|Australie|61|AUD\n" +
            "AT|Autriche|43|EUR\n" +
            "AZ|Azerbaïdjan|994|AZN\n" +
            "BS|Bahamas|1|BSD\n" +
            "BH|Bahreïn|973|BHD\n" +
            "BD|Bangladesh|880|BDT\n" +
            "BB|Barbade|1|BBD\n" +
            "BE|Belgique|32|EUR\n" +
            "BZ|Belize|501|BZD\n" +
            "BJ|Bénin|229|XOF\n" +
            "BT|Bhoutan|975|BTN\n" +
            "BY|Biélorussie|375|BYN\n" +
            "MM|Birmanie|95|MMK\n" +
            "BO|Bolivie|591|BOB\n" +
            "BA|Bosnie-Herzégovine|387|BAM\n" +
            "BW|Botswana|267|BWP\n" +
            "BR|Brésil|55|BRL\n" +
            "BN|Brunei|673|BND\n" +
            "BG|Bulgarie|359|BGN\n" +
            "BF|Burkina Faso|226|XOF\n" +
            "BI|Burundi|257|BIF\n" +
            "KH|Cambodge|855|KHR\n" +
            "CM|Cameroun|237|XAF\n" +
            "CA|Canada|1|CAD\n" +
            "CV|Cap-Vert|238|CVE\n" +
            "CL|Chili|56|CLP\n" +
            "CN|Chine|86|CNY\n" +
            "CY|Chypre|357|EUR\n" +
            "CO|Colombie|57|COP\n" +
            "KM|Comores|269|KMF\n" +
            "CG|Congo|242|XAF\n" +
            "CD|Congo (RDC)|243|CDF\n" +
            "KP|Corée du Nord|850|KPW\n" +
            "KR|Corée du Sud|82|KRW\n" +
            "CR|Costa Rica|506|CRC\n" +
            "CI|Côte d'Ivoire|225|XOF\n" +
            "HR|Croatie|385|EUR\n" +
            "CU|Cuba|53|CUP\n" +
            "DK|Danemark|45|DKK\n" +
            "DJ|Djibouti|253|DJF\n" +
            "DM|Dominique|1|XCD\n" +
            "EG|Égypte|20|EGP\n" +
            "AE|Émirats arabes unis|971|AED\n" +
            "EC|Équateur|593|USD\n" +
            "ER|Érythrée|291|ERN\n" +
            "ES|Espagne|34|EUR\n" +
            "EE|Estonie|372|EUR\n" +
            "SZ|Eswatini|268|SZL\n" +
            "US|États-Unis|1|USD\n" +
            "ET|Éthiopie|251|ETB\n" +
            "FJ|Fidji|679|FJD\n" +
            "FI|Finlande|358|EUR\n" +
            "FR|France|33|EUR\n" +
            "GA|Gabon|241|XAF\n" +
            "GM|Gambie|220|GMD\n" +
            "GE|Géorgie|995|GEL\n" +
            "GH|Ghana|233|GHS\n" +
            "GR|Grèce|30|EUR\n" +
            "GD|Grenade|1|XCD\n" +
            "GT|Guatemala|502|GTQ\n" +
            "GN|Guinée|224|GNF\n" +
            "GW|Guinée-Bissau|245|XOF\n" +
            "GQ|Guinée équatoriale|240|XAF\n" +
            "GY|Guyana|592|GYD\n" +
            "HT|Haïti|509|HTG\n" +
            "HN|Honduras|504|HNL\n" +
            "HU|Hongrie|36|HUF\n" +
            "MH|Îles Marshall|692|USD\n" +
            "SB|Îles Salomon|677|SBD\n" +
            "IN|Inde|91|INR\n" +
            "ID|Indonésie|62|IDR\n" +
            "IQ|Irak|964|IQD\n" +
            "IR|Iran|98|IRR\n" +
            "IE|Irlande|353|EUR\n" +
            "IS|Islande|354|ISK\n" +
            "IL|Israël|972|ILS\n" +
            "IT|Italie|39|EUR\n" +
            "JM|Jamaïque|1|JMD\n" +
            "JP|Japon|81|JPY\n" +
            "JO|Jordanie|962|JOD\n" +
            "KZ|Kazakhstan|7|KZT\n" +
            "KE|Kenya|254|KES\n" +
            "KG|Kirghizistan|996|KGS\n" +
            "KI|Kiribati|686|AUD\n" +
            "KW|Koweït|965|KWD\n" +
            "LA|Laos|856|LAK\n" +
            "LS|Lesotho|266|LSL\n" +
            "LV|Lettonie|371|EUR\n" +
            "LB|Liban|961|LBP\n" +
            "LR|Libéria|231|LRD\n" +
            "LY|Libye|218|LYD\n" +
            "LI|Liechtenstein|423|CHF\n" +
            "LT|Lituanie|370|EUR\n" +
            "LU|Luxembourg|352|EUR\n" +
            "MK|Macédoine du Nord|389|MKD\n" +
            "MG|Madagascar|261|MGA\n" +
            "MY|Malaisie|60|MYR\n" +
            "MW|Malawi|265|MWK\n" +
            "MV|Maldives|960|MVR\n" +
            "ML|Mali|223|XOF\n" +
            "MT|Malte|356|EUR\n" +
            "MA|Maroc|212|MAD\n" +
            "MU|Maurice|230|MUR\n" +
            "MR|Mauritanie|222|MRU\n" +
            "MX|Mexique|52|MXN\n" +
            "FM|Micronésie|691|USD\n" +
            "MD|Moldavie|373|MDL\n" +
            "MC|Monaco|377|EUR\n" +
            "MN|Mongolie|976|MNT\n" +
            "ME|Monténégro|382|EUR\n" +
            "MZ|Mozambique|258|MZN\n" +
            "NA|Namibie|264|NAD\n" +
            "NR|Nauru|674|AUD\n" +
            "NP|Népal|977|NPR\n" +
            "NI|Nicaragua|505|NIO\n" +
            "NE|Niger|227|XOF\n" +
            "NG|Nigéria|234|NGN\n" +
            "NO|Norvège|47|NOK\n" +
            "NZ|Nouvelle-Zélande|64|NZD\n" +
            "OM|Oman|968|OMR\n" +
            "UG|Ouganda|256|UGX\n" +
            "UZ|Ouzbékistan|998|UZS\n" +
            "PK|Pakistan|92|PKR\n" +
            "PW|Palaos|680|USD\n" +
            "PS|Palestine|970|ILS\n" +
            "PA|Panama|507|PAB\n" +
            "PG|Papouasie-Nouvelle-Guinée|675|PGK\n" +
            "PY|Paraguay|595|PYG\n" +
            "NL|Pays-Bas|31|EUR\n" +
            "PE|Pérou|51|PEN\n" +
            "PH|Philippines|63|PHP\n" +
            "PL|Pologne|48|PLN\n" +
            "PT|Portugal|351|EUR\n" +
            "QA|Qatar|974|QAR\n" +
            "CF|République centrafricaine|236|XAF\n" +
            "DO|République dominicaine|1|DOP\n" +
            "CZ|République tchèque|420|CZK\n" +
            "RO|Roumanie|40|RON\n" +
            "GB|Royaume-Uni|44|GBP\n" +
            "RU|Russie|7|RUB\n" +
            "RW|Rwanda|250|RWF\n" +
            "KN|Saint-Christophe-et-Niévès|1|XCD\n" +
            "LC|Sainte-Lucie|1|XCD\n" +
            "SM|Saint-Marin|378|EUR\n" +
            "VC|Saint-Vincent-et-les-Grenadines|1|XCD\n" +
            "SV|Salvador|503|USD\n" +
            "WS|Samoa|685|WST\n" +
            "ST|Sao Tomé-et-Principe|239|STN\n" +
            "SN|Sénégal|221|XOF\n" +
            "RS|Serbie|381|RSD\n" +
            "SC|Seychelles|248|SCR\n" +
            "SL|Sierra Leone|232|SLE\n" +
            "SG|Singapour|65|SGD\n" +
            "SK|Slovaquie|421|EUR\n" +
            "SI|Slovénie|386|EUR\n" +
            "SO|Somalie|252|SOS\n" +
            "SD|Soudan|249|SDG\n" +
            "SS|Soudan du Sud|211|SSP\n" +
            "LK|Sri Lanka|94|LKR\n" +
            "SE|Suède|46|SEK\n" +
            "CH|Suisse|41|CHF\n" +
            "SR|Suriname|597|SRD\n" +
            "SY|Syrie|963|SYP\n" +
            "TJ|Tadjikistan|992|TJS\n" +
            "TZ|Tanzanie|255|TZS\n" +
            "TD|Tchad|235|XAF\n" +
            "TH|Thaïlande|66|THB\n" +
            "TL|Timor oriental|670|USD\n" +
            "TG|Togo|228|XOF\n" +
            "TO|Tonga|676|TOP\n" +
            "TT|Trinité-et-Tobago|1|TTD\n" +
            "TN|Tunisie|216|TND\n" +
            "TM|Turkménistan|993|TMT\n" +
            "TR|Turquie|90|TRY\n" +
            "TV|Tuvalu|688|AUD\n" +
            "UA|Ukraine|380|UAH\n" +
            "UY|Uruguay|598|UYU\n" +
            "VU|Vanuatu|678|VUV\n" +
            "VA|Vatican|379|EUR\n" +
            "VE|Venezuela|58|VES\n" +
            "VN|Viêt Nam|84|VND\n" +
            "YE|Yémen|967|YER\n" +
            "ZM|Zambie|260|ZMW\n" +
            "ZW|Zimbabwe|263|USD\n" +
            "TW|Taïwan|886|TWD\n" +
            "XK|Kosovo|383|EUR\n" +
            "HK|Hong Kong|852|HKD\n" +
            "MO|Macao|853|MOP\n" +
            "PR|Porto Rico|1|USD\n" +
            "GL|Groenland|299|DKK\n" +
            "NC|Nouvelle-Calédonie|687|XPF\n" +
            "PF|Polynésie française|689|XPF\n" +
            "RE|La Réunion|262|EUR\n" +
            "YT|Mayotte|262|EUR\n" +
            "GP|Guadeloupe|590|EUR\n" +
            "MQ|Martinique|596|EUR\n" +
            "GF|Guyane française|594|EUR\n" +
            "BM|Bermudes|1|BMD\n" +
            "KY|Îles Caïmans|1|KYD\n" +
            "GI|Gibraltar|350|GIP\n" +
            "FO|Îles Féroé|298|DKK\n" +
            "";

    static final List<Country> ALL = new ArrayList<>();
    static final List<Country> FEATURED = new ArrayList<>();
    private static final Map<String, Country> BY_ISO = new HashMap<>();

    static {
        for (String line : RAW.split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split("\\|");
            Country c = new Country(p[0], p[1], p[2], p[3]);
            ALL.add(c);
            BY_ISO.put(c.iso, c);
        }
        final Collator collator = Collator.getInstance(Locale.FRANCE);
        Collections.sort(ALL, new Comparator<Country>() {
            @Override
            public int compare(Country a, Country b) {
                return collator.compare(a.name, b.name);
            }
        });
        for (String iso : FEATURED_ISO) {
            Country c = BY_ISO.get(iso);
            if (c != null) FEATURED.add(c);
        }
    }

    private Countries() {}

    static Country byIso(String iso) {
        return iso == null ? null : BY_ISO.get(iso.toUpperCase(Locale.ROOT));
    }

    /** Drapeau émoji : deux "indicateurs régionaux" Unicode à partir du code ISO. */
    static String flag(String iso) {
        if (iso == null || iso.length() != 2) return "\uD83C\uDF10";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            char ch = Character.toUpperCase(iso.charAt(i));
            if (ch < 'A' || ch > 'Z') return "\uD83C\uDF10";
            sb.appendCodePoint(0x1F1E6 + (ch - 'A'));
        }
        return sb.toString();
    }

    /** Minuscules sans accents, pour la recherche. */
    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
