package com.traslation.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 价格换算：人民币 → 目标国家货币。
 *
 * <p>汇率与格式化规则与 Flask 的 {@code backend/services/currency_converter.py} 保持一致， 否则同一个商品在两个实现下会显示不同价格。
 */
final class CurrencyRates {

    private record Rate(String currency, String symbol, double rate) {}

    private static final Map<String, Rate> RATES = new LinkedHashMap<>();

    /** 整数显示货币（不保留小数）。 */
    private static final java.util.Set<String> INTEGER_CURRENCIES =
            java.util.Set.of("JPY", "KRW", "VND", "IDR");

    static {
        RATES.put("中国", new Rate("CNY", "¥", 1.0));
        RATES.put("美国", new Rate("USD", "$", 0.14));
        RATES.put("沙特", new Rate("SAR", "﷼", 0.52));
        RATES.put("阿联酋", new Rate("AED", "د.إ", 0.51));
        RATES.put("日本", new Rate("JPY", "¥", 20.5));
        RATES.put("韩国", new Rate("KRW", "₩", 185.0));
        RATES.put("俄罗斯", new Rate("RUB", "₽", 12.5));
        RATES.put("德国", new Rate("EUR", "€", 0.13));
        RATES.put("法国", new Rate("EUR", "€", 0.13));
        RATES.put("泰国", new Rate("THB", "฿", 4.8));
        RATES.put("越南", new Rate("VND", "₫", 3300.0));
        RATES.put("马来西亚", new Rate("MYR", "RM", 0.62));
        RATES.put("印度尼西亚", new Rate("IDR", "Rp", 2100.0));
        RATES.put("菲律宾", new Rate("PHP", "₱", 7.8));
        RATES.put("英国", new Rate("GBP", "£", 0.11));
        RATES.put("新加坡", new Rate("SGD", "S$", 0.19));
        RATES.put("澳大利亚", new Rate("AUD", "A$", 0.21));
        RATES.put("加拿大", new Rate("CAD", "C$", 0.19));
        RATES.put("巴西", new Rate("BRL", "R$", 0.75));
        RATES.put("墨西哥", new Rate("MXN", "MX$", 2.5));
        RATES.put("印度", new Rate("INR", "₹", 11.5));
    }

    private CurrencyRates() {}

    /**
     * 换算价格。未知国家按「中国」处理，与 Flask 一致。
     *
     * @return 与 Flask 同构的 map：price / formatted_price / currency / symbol / country
     */
    static Map<String, Object> convert(double priceCny, String country) {
        String target = country != null && RATES.containsKey(country) ? country : "中国";
        Rate rate = RATES.get(target);
        double converted = priceCny * rate.rate();
        String formatted =
                INTEGER_CURRENCIES.contains(rate.currency())
                        ? String.valueOf(Math.round(converted))
                        : String.format(java.util.Locale.ROOT, "%.2f", converted);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("price", converted);
        info.put("formatted_price", formatted);
        info.put("currency", rate.currency());
        info.put("symbol", rate.symbol());
        info.put("country", target);
        return info;
    }
}
