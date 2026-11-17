package com.traslation.backend.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

@RestController
public class DashboardController {
    private final JdbcTemplate db;

    public DashboardController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/api/dashboard")
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(),
                lastStart = today.minusMonths(1).withDayOfMonth(1).atStartOfDay();
        long users = count("select count(*) from sys_users"),
                trans = count("select count(*) from translate_history"),
                products = count("select count(*) from product"),
                lastUsers = countBetween("sys_users", "create_time", lastStart, monthStart),
                lastTranslations =
                        countBetween("translate_history", "create_time", lastStart, monthStart),
                lastProducts = countBetween("product", "create_time", lastStart, monthStart);
        List<String> months = new ArrayList<>();
        List<Long> chars = new ArrayList<>(),
                exposes = new ArrayList<>(),
                clicks = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = YearMonth.from(today).minusMonths(i);
            Timestamp from = Timestamp.valueOf(m.atDay(1).atStartOfDay()),
                    to = Timestamp.valueOf(m.plusMonths(1).atDay(1).atStartOfDay());
            months.add(m.getMonth().getDisplayName(TextStyle.SHORT, Locale.CHINA));
            chars.add(
                    sum(
                            "select coalesce(sum(char_count),0) from translate_operation_log where"
                                + " create_time>=? and create_time<?",
                            from,
                            to));
            exposes.add(
                    sum(
                            "select count(*) from biz_rec_stat_log where action_type=1 and"
                                + " create_time>=? and create_time<?",
                            from,
                            to));
            clicks.add(
                    sum(
                            "select count(*) from biz_rec_stat_log where action_type=2 and"
                                + " create_time>=? and create_time<?",
                            from,
                            to));
        }
        long
                expose =
                        sum(
                                "select count(*) from biz_rec_stat_log where action_type=1 and"
                                    + " create_time>=? and create_time<?",
                                Timestamp.valueOf(today.atStartOfDay()),
                                Timestamp.valueOf(today.plusDays(1).atStartOfDay())),
                click =
                        sum(
                                "select count(*) from biz_rec_stat_log where action_type=2 and"
                                    + " create_time>=? and create_time<?",
                                Timestamp.valueOf(today.atStartOfDay()),
                                Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
        var top =
                db.queryForList(
                        "select product_id,count(*) click_count from biz_rec_stat_log where"
                            + " action_type=2 and create_time>=? and create_time<? group by"
                            + " product_id order by click_count desc limit 10",
                        Timestamp.valueOf(today.atStartOfDay()),
                        Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
        Map<String, Object> daily =
                Map.of(
                        "date",
                        today.toString(),
                        "total_expose",
                        expose,
                        "total_click",
                        click,
                        "click_rate",
                        (expose == 0 ? 0 : Math.round(click * 10000.0 / expose) / 100.0) + "%",
                        "top_products",
                        top);
        var recent =
                db.queryForList(
                        "select"
                            + " id,username,source_text,target_lang,target_lang_cn,date_format(create_time,'%Y-%m-%d"
                            + " %H:%i:%s') create_time from translate_history order by create_time"
                            + " desc limit 10");
        Map<String, Object> trend = Map.of("months", months, "char_counts", chars),
                recTrend =
                        Map.of("months", months, "expose_counts", exposes, "click_counts", clicks);
        var todayRows =
                db.queryForList(
                        "select translate_char_count,translate_api_cost from translate_daily_report"
                            + " where stat_date=?",
                        java.sql.Date.valueOf(today));
        long todayChars =
                todayRows.isEmpty() ? 0 : num(todayRows.get(0).get("translate_char_count"));
        double todayCost =
                todayRows.isEmpty() ? 0 : decimal(todayRows.get(0).get("translate_api_cost"));
        var monthRows =
                db.queryForMap(
                        "select coalesce(sum(translate_char_count),0)"
                            + " chars,coalesce(sum(translate_api_cost),0) cost from"
                            + " translate_daily_report where stat_date>=? and stat_date<=?",
                        java.sql.Date.valueOf(today.withDayOfMonth(1)),
                        java.sql.Date.valueOf(today));
        long monthChars = num(monthRows.get("chars"));
        double monthCost = decimal(monthRows.get("cost")), manual = monthChars * .001;
        var dailyRows =
                db.queryForList(
                        "select date_format(stat_date,'%Y-%m-%d')"
                            + " day,translate_char_count,translate_api_cost from"
                            + " translate_daily_report where stat_date>=? and stat_date<=? order by"
                            + " stat_date",
                        java.sql.Date.valueOf(today.minusDays(29)), java.sql.Date.valueOf(today));
        List<String> dates = new ArrayList<>();
        List<Long> dailyChars = new ArrayList<>();
        List<Double> dailyCosts = new ArrayList<>();
        for (var r : dailyRows) {
            dates.add(Objects.toString(r.get("day")));
            dailyChars.add(num(r.get("translate_char_count")));
            dailyCosts.add(decimal(r.get("translate_api_cost")));
        }
        Map<String, Object> stats =
                Map.of(
                        "current_date",
                        today.toString(),
                        "today",
                        Map.of("char_count", todayChars, "api_cost", round(todayCost)),
                        "month",
                        Map.of(
                                "total_char",
                                monthChars,
                                "total_api_cost",
                                round(monthCost),
                                "manual_cost",
                                round(manual),
                                "saved_cost",
                                round(Math.max(0, manual - monthCost))),
                        "trend",
                        Map.of("dates", dates, "char_counts", dailyChars, "api_costs", dailyCosts));
        Map<String, Object> data =
                Map.ofEntries(
                        Map.entry("total_users", users),
                        Map.entry("user_growth_rate", growth(users, lastUsers)),
                        Map.entry("total_translations", trans),
                        Map.entry("translation_growth_rate", growth(trans, lastTranslations)),
                        Map.entry("total_products", products),
                        Map.entry("product_growth_rate", growth(products, lastProducts)),
                        Map.entry("translate_trend", trend),
                        Map.entry("recommendation_trend", recTrend),
                        Map.entry("daily_stat", daily),
                        Map.entry("translate_stat", stats),
                        Map.entry("recent_history", recent));
        return Map.of("code", 200, "data", data);
    }

    private long count(String sql) {
        Long n = db.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private long num(Object n) {
        return n == null ? 0 : ((Number) n).longValue();
    }

    private double decimal(Object n) {
        return n == null ? 0 : ((Number) n).doubleValue();
    }

    private double round(double n) {
        return Math.round(n * 100.0) / 100.0;
    }

    private long sum(String sql, Object... args) {
        Long n = db.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private long countBetween(String table, String column, LocalDateTime from, LocalDateTime to) {
        return sum(
                "select count(*) from " + table + " where " + column + ">=? and " + column + "<?",
                Timestamp.valueOf(from),
                Timestamp.valueOf(to));
    }

    private String growth(long current, long previous) {
        if (previous == 0) return current > 0 ? "+100%" : "0%";
        long rate = Math.round((current - previous) * 100.0 / previous);
        return (rate >= 0 ? "+" : "") + rate + "%";
    }
}
